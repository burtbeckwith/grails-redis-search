package com.qiyi.redis.search

import grails.converters.deep.JSON

/**
 * @author kxc
 */
class EntityConfig {

    def title_field
    def aliases_field
    String id_field  = "id"
    def ext_fields
    def type
    def condition_fields
    def score_field
    boolean prefix_index_enable = false

    EntityConfig(options = [:]) {
        title_field = options['title_field'] ? options['title_field'] : title_field
        type = options['type'] ? options['type']:type
        aliases_field = options['aliases_field'] ? options['aliases_field'] : aliases_field
        prefix_index_enable = options['prefix_index_enable'] ? options['prefix_index_enable'] : false
        ext_fields = options['ext_fields'] ? options['ext_fields'] : []
        score_field = options['score_field'] ? options['score_field'] : 'lastUpdated'
        condition_fields = options['condition_fields'] ? options['condition_fields'] : []
        //score_field 添加到ext_fields
        ext_fields = [ext_fields, [score_field]].flatten()
        //condition_fields 添加到ext_fields
        ext_fields = [ext_fields, condition_fields].flatten()
    }


    /*

    def redis_search_fields_to_hash(ext_fields) {
        def exts = []
        ext_fields.each { f ->
            //instance-eval
            exts[f] = Eval.me(f.toString())
        }
        return exts
    }

    def redis_search_alias_value(field) {
        if ((!field || "" == field) || (field && "_was" == field)) {
            def val = Eval.me("this.${field}").clone()
            if (![String, ArrayList].contains(val))
                return
            if (val instanceof String) {
                val = val.toString().split(",")
            }
            return val
        }
    }

    def redis_search_index_create() {
        def index = new Index([title: this.title_field, aliases: this.aliases_field, id: this.id_field, exts: this.ext_fields, type: this.class.name.toString(), condition_fields: this.condition_fields, score: this.score_field, prefix_index_enable: this.prefix_index_enable])
        index.save()
        index = null
        return true
    }

    def redis_search_index_delete(titles) {
        titles.unique().each { title ->
            Index.remove(id: this.id, title: title, type: this.class.name.toString())
        }
        return true
    }

    def beforeDelete() {
        def titles = []
        titles = redis_search_alias_value("${aliases_field}")
        titles << this.title_field

        redis_search_index_delete(titles)
    }



    def redis_search_index_need_reindex(){
        return true
    }

    def afterUpdate() {
        if (redis_search_index_need_reindex()) {
            def titles = []
            titles = redis_search_alias_value("${aliases_field}_was")
            titles << this.title_field + "_was"
            redis_search_index_delete(titles)
        }
        return true
    }

    def afterInsert() {
        redis_search_index_create()
        return true
    } */
}
