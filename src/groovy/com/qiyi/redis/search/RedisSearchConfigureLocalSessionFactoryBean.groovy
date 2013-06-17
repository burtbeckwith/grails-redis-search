package com.qiyi.redis.search

import org.codehaus.groovy.grails.orm.hibernate.ConfigurableLocalSessionFactoryBean
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher
import org.hibernate.HibernateException
import org.hibernate.cfg.Configuration
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author kxc
 */
class RedisSearchConfigureLocalSessionFactoryBean extends ConfigurableLocalSessionFactoryBean {

    private Logger log = LoggerFactory.getLogger(getClass())

    @Override
    protected void postProcessConfiguration(Configuration config) throws HibernateException {
        super.postProcessConfiguration(config)

        try {
            def properties = config.properties

            def searchMapping = [:]

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
            log.error e.message, e
        }
    }
}
