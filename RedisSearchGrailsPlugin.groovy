import com.qiyi.redis.search.EntityConfig
import com.qiyi.redis.search.Index
import com.qiyi.redis.search.Search
import grails.converters.JSON
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

class RedisSearchGrailsPlugin {
    def version = "0.1"
    def grailsVersion = "2.0 > *"
    def pluginExcludes = [
        "src/java/Test.java"
    ]

    def title = "Redis Search Plugin"
    def author = "kexiaocheng"
    def authorEmail = ""
    def description = 'Brief summary/description of the plugin.'

    def documentation = "http://grails.org/plugin/redis-search"

    private searchMapping = [:]

//    def license = "APACHE"
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    def doWithSpring = {
//        sessionFactory(RedisSearchConfigureLocalSessionFactoryBean){ bean ->
//            bean.parent = 'abstractSessionFactoryBeanConfig'
//        }
        for (domainClass in application.domainClasses) {
            def searchMap = ClassPropertyFetcher.forClass(domainClass.clazz).getStaticPropertyValue('redissearch', Map)

            if (searchMap) {
                def entityConfig = new EntityConfig(searchMap)
                searchMapping.put(domainClass.clazz.name, entityConfig)
            }
        }

        def redissearchConfig = application.config.grails.plugins.redissearch
        if (redissearchConfig instanceof Closure) {
            def jedis_config = new JedisPoolConfig()
            jedis_config.setMaxActive(1000)
            jedis_config.setMaxIdle(200)
            jedis_config.setMaxWait(1000l)
            jedis_config.setMinEvictableIdleTimeMillis(1000)
            jedis_config.setTestOnBorrow(true)
            redissearchConfig.delegate = this
            redissearchConfig.resolveStrategy = Closure.DELEGATE_FIRST
            redissearchConfig.call()

            def host = redissearchConfig.host ? redissearchConfig.host : 'localhost'
            def port = redissearchConfig.port ? redissearchConfig.port : 6379
            def ns = redissearchConfig.ns ? redissearchConfig.ns : application.metadata['app.name']
            def pinyin_match = redissearchConfig.pinyin_match ? redissearchConfig.pinyin_match : false
            if (host && port) {
                def redisPool = new JedisPool(jedis_config, host, port)
//                def redisPool = new NamespaceJedisPool(jedis_config,host,port).withNamespace(ns)
                def redis = redisPool.getResource()
                Search.configure().redisPool = redisPool
                Search.configure().pinyin_match = pinyin_match
            }
        }
    }

    def doWithDynamicMethods = { ctx ->
        for (domainClass in application.domainClasses) {

            def searchClosure = ClassPropertyFetcher.forClass(domainClass.clazz).getStaticPropertyValue('redissearch', Map)
            if (searchClosure) {
                domainClass.metaClass.redis_search_fields_to_hash = { ext_fields ->
                    def exts = []
                    ext_fields.each { f ->
                        //instance-eval
                        exts[f] = Eval.me(f.toString())
                    }
                    return exts
                }

                domainClass.metaClass.redis_search_alias_value = { instance, field ->
                    if (!field || "" == field) {
                        return []
                    } else {
                        def val = instance.getPersistentValue(field)
                        if (val || ![String, ArrayList].contains(val.class))
                            return []
                        if (val instanceof String) {
                            val = val.toString().split(",")
                        }
                        return val
                    }
                }

                domainClass.metaClass.redis_search_index_create = {
                    def instance = delegate
                    EntityConfig entity = searchMapping.get(instance.class.name)
                    def index = new Index([title: instance."${entity.title_field}", id: instance."${entity.id_field}" + "", type: instance.class.getSimpleName(), prefix_index_enable: entity.prefix_index_enable])
                    index.save()
                    index = null
                    return true
                }

                domainClass.metaClass.redis_search_index_delete = { titles ->
                    def instance = delegate
                    EntityConfig entity = searchMapping.get(instance.class.name)
                    titles.unique().each { title ->
                        Index.remove([id: instance."${entity.id_field}" + "", title: instance."${entity.title_field}", type: instance.class.getSimpleName()])
                    }
                    return true
                }

                domainClass.metaClass.beforeDelete = {
                    def titles = []
                    EntityConfig entity = searchMapping.get(delegate.class.name)
                    titles = redis_search_alias_value(delegate, entity.aliases_field)
                    titles << delegate."${entity.title_field}"
                    redis_search_index_delete(titles)
                }

                domainClass.metaClass.redis_search_index_need_reindex = { instance ->
                    EntityConfig entity = searchMapping.get(instance.class.name)
                    if (instance.isDirty()) {
                        if (instance.isDirty("${entity.title_field}")) {
                            return true
                        }
                        if (instance.isDirty("${entity.aliases_field}")) {
                            return true
                        }
                    }
                    return false
                }

                domainClass.metaClass.beforeUpdate = {
                    EntityConfig entity = searchMapping.get(delegate.class.name)
                    if (redis_search_index_need_reindex(delegate)) {
                        def titles = []
                        titles = redis_search_alias_value(delegate, entity.aliases_field)
                        titles << delegate."${entity.title_field}"
                        redis_search_index_delete(titles)
                    }
                    return true
                }

                domainClass.metaClass.afterUpdate = {
                    if (delegate.deleteFlag) {
                        EntityConfig entity = searchMapping.get(delegate.class.name)
                        def titles = []
                        titles = redis_search_alias_value(delegate, entity.aliases_field)
                        titles << delegate."${entity.title_field}"
                        redis_search_index_delete(titles)
                        return true
                    }
                    redis_search_index_create()
                    return true
                }

                domainClass.metaClass.afterInsert = {
                    redis_search_index_create()
                    return true
                }

                domainClass.metaClass.static.complete = { prefix ->
                    return Search.complete(delegate.getSimpleName(), prefix)
                }

                domainClass.metaClass.static.query = { text ->
                    return Search.query(delegate.getSimpleName(), text)
                }
            }
        }
    }
}
