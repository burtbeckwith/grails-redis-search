grails.project.work.dir = 'target'

grails.project.dependency.resolution = {

    inherits 'global'
    log 'warn'

    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
        mavenRepo "http://repository.codehaus.org"
        mavenRepo "http://repository.jboss.com/maven2/"
    }

    dependencies {
        compile 'org.apache.lucene:lucene-core:4.1.0'
        compile 'org.apache.lucene:lucene-analyzers-common:4.1.0'
        compile 'redis.clients:jedis:2.0.0'
        compile 'com.trigonic:jedis-namespace:0.1'

        compile 'com.belerweb:pinyin4j:2.5.0'
        compile 'com.chenlb.mmseg4j:mmseg4j-core:1.9.0'
    }

    plugins {

        compile ":hibernate:$grailsVersion"

        build ':release:2.2.1', ':rest-client-builder:1.0.3', {
            export = false
        }
    }
}
