本插件基于[huacnlee/redis-search](https://github.com/huacnlee/redis-search)改写而成，李顺华同学的源码基于Ruby实现，这里用grails改写。
开发工具：IntelliJ Idea


#### 使用说明：
##### 1.下载插件到本地并解压，在BuildConfig.groovy的末尾空白处添加一行
grails.plugin.location.redissearch = "yourpath/redis-search"
##### 2.在BuildConfig.groovy的dependencies中添加:

    compile 'org.apache.lucene:lucene-core:4.0.0'
    compile 'org.apache.lucene:lucene-analyzers:3.6.0'
    compile 'redis.clients:jedis:2.0.0'
    compile 'com.trigonic:jedis-namespace:0.1'


手动将pinyin4j-2.5.0.jar mmseg4j-all-with-dic-1.9.0.v20120712-SNAPSHOT.jar添加到lib目录

##### 3.在Config.groovy中添加:
    grails.plugins.redissearch = {
        host = 'localhost' 
        port = 6379
        pinyin_match = true
    }

##### 4.修改domainClass，例如
    class User{
        String username
        String passw    ord
        Integer age

	   //添加如下这一行，title_field 指定需要索引的字段，prefix_index_enable是否支持前缀检索
        static redissearch = [title_field:'username',prefix_index_enable:true]
    }

#####  5.检索
    class UserController {

        //基于前缀的检索
        def complete(){
            def prefix = params.p
            prefix = prefix.stripIndent()
            def result = User.complete(prefix)
            render result.unique() as JSON
        }

        //基于分词的检索
        def query(){
            def text = params.t
            def result = User.query(text)
            render result as JSON
        }
    ｝

##### 前端代码:
    结合bootstrap的typeahead插件，即可在页面上实现输入提示.HTML代码:
    <input type="text" name="p" id="name" size="20" data-provide="typeahead" data-link="complete" class="ajax-typeahead">

##### JavaScript代码:
    <script type="text/javascript">
        $(document).ready(function () {
            $("#name").typeahead({
                source: function(query,process){
                    return $.ajax({
                        url:$("#name").data('link'),
                        type:'post',
                        data:{p:query},
                        dataType:'json',
                        success:function(result){
                            var list = result.map(function(item){
                                var aitem = {id:item.id,name:item.title}
                                return JSON.stringify(aitem)
                            });
                            return process(list);
                        }
                    });
                },
                matcher:function(item){
                  return true
                },
                highlighter:function(item){
                    return JSON.parse(item).name;
                },
                updater:function(item){
                    return JSON.parse(item).name;
                }
            });
        });
    </script>

#####可以在bootstrap中增加如下代码，为数据库中已有的记录创建索引
	Search.config.redisPool.getResource().flushDB()
        for (domainClass in grailsApplication.domainClasses) {
            def searchClosure = ClassPropertyFetcher.forClass(domainClass.clazz).getStaticPropertyValue('redissearch', Map)
            if (searchClosure) {
                domainClass.clazz.list().each {
                    if (!it.deleteFlag) {
                        def field = searchClosure['title_field'];
                        new Index([id: it.id + "", title: it."${field}", type: domainClass.getShortName(), prefix_index_enable: true]).save()
                    }
                }
            }
        }
