import com.qiyi.redis.search.EntityConfig
import com.qiyi.redis.search.Index
import com.qiyi.redis.search.Search
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
//import com.trigonic.jedis.NamespaceJedisPool

class RedisSearchGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.2 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Redis Search Plugin" // Headline display name of the plugin
    def author = "kexiaocheng"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/redis-redis"

    def searchMapping = new HashMap()

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]


    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
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
        if (redissearchConfig && redissearchConfig instanceof Closure) {
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
                def redis = redisPool.getResource()
                Search.configure().redisPool = redisPool
                Search.configure().pinyin_match = pinyin_match
            }

        }
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
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

                domainClass.metaClass.redis_search_alias_value = { field ->
                    if ( !field || "" == field) {
                        return []
                    }else{
                        def instance = delegate
                        def val = instance.getPersistentValue(field)
                        if (![String, ArrayList].contains(val))
                            return
                        if (val instanceof String) {
                            val = val.toString().split(",")
                        }
                        return val
                    }
                }

                domainClass.metaClass.redis_search_index_create = {
                    def instance = delegate
                    EntityConfig entity = searchMapping.get(domainClass.clazz.name)
                    def index = new Index([title: instance."${entity.title_field}", id: instance."${entity.id_field}" + "", type: entity.type ? entity.type : domainClass.getShortName(),prefix_index_enable:entity.prefix_index_enable])
                    index.save()
                    index = null
                    return true
                }

                domainClass.metaClass.redis_search_index_delete = { titles ->
                    def instance = delegate
                    EntityConfig entity = searchMapping.get(domainClass.clazz.name)
                    titles.unique().each { title ->
                        Index.remove([id:instance."${entity.id_field}" + "", title: instance."${entity.title_field}", type: entity.type ? entity.type : instance.class.getSimpleName()])
                    }
                    return true
                }

                domainClass.metaClass.beforeDelete = {
                    def titles = []
                    EntityConfig entity = searchMapping.get(domainClass.clazz.name)
                    titles = redis_search_alias_value(entity.aliases_field)
                    titles << entity.title_field

                    redis_search_index_delete(titles)
                }

                domainClass.metaClass.redis_search_index_need_reindex = { instance->
                    EntityConfig entity = searchMapping.get(domainClass.clazz.name)
                    if (instance.isDirty()){
                        if (instance.isDirty("${entity.title_field}")){
                            return true
                        }
                        if (instance.isDirty("${entity.aliases_field}")){
                            return true
                        }
                    }
                    return false
                }

                domainClass.metaClass.beforeUpdate = {
                    EntityConfig entity = searchMapping.get(domainClass.clazz.name)
                    def instance = delegate
                    if (redis_search_index_need_reindex(instance)) {
                        def titles = []
                        titles = redis_search_alias_value("${entity.aliases_field}")
                        titles << instance.getPersistentValue("${entity.title_field}")
                        redis_search_index_delete(titles)
                    }
                    return true
                }

                domainClass.metaClass.afterUpdate = {
                    redis_search_index_create()
                    return true
                }

                domainClass.metaClass.afterInsert = {
                    redis_search_index_create()
                    return true
                }

                domainClass.metaClass.static.complete = { prefix->
                    return Search.complete(delegate.getSimpleName(),prefix)
                }

                domainClass.metaClass.static.query = {text->
                    return Search.query(delegate.getSimpleName(),text)
                }
            }
        }
    }

    def doWithApplicationContext = { applicationContext ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.

    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
