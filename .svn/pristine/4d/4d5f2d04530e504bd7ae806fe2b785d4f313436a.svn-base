package com.qiyi.redis.search

import org.codehaus.groovy.grails.orm.hibernate.ConfigurableLocalSessionFactoryBean
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.HibernateException
import org.hibernate.cfg.Configuration

/**
 * Created with IntelliJ IDEA.
 * User: kxc
 * Date: 13-5-2
 * Time: 下午5:17
 * To change this template use File | Settings | File Templates.
 */
class RedisSearchConfigureLocalSessionFactoryBean extends ConfigurableLocalSessionFactoryBean {
    @Override
    protected void postProcessConfiguration(Configuration config) throws HibernateException {
        super.postProcessConfiguration(config)

        try {
            def properties = config.properties

            def searchMapping = new HashMap()

            grailsApplication.domainClasses.each {
                def searchClosure = ClassPropertyFetcher.forClass(it.clazz).getStaticPropertyValue('redissearch', Closure)

                if (searchClosure) {

                    def entityConfig = new EntityConfig([title_field: searchClosure.getProperty('title'),prefix_index_enable: searchClosure.getProperty('prefix_index_enable')])
                    searchClosure.delegate = entityConfig
                    searchClosure.resolveStrategy = Closure.DELEGATE_FIRST
                    searchClosure.call()
                    searchMapping(it.clazz,entityConfig)
                }
            }

            properties.put("redis_search", searchMapping)

        }catch (Exception e){
            e.printStackTrace()
        }
    }
}
