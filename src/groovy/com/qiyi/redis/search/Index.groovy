package com.qiyi.redis.search

import grails.converters.JSON

/**
 * @author kxc
 */
class Index {
    def type
    def title
    def id
    def score
    def aliases = []
    def exts = []
    def condition_fields = []
    boolean prefix_index_enable = true
    def jedis

    Index(options = [:]) {
//        default data
        options.each { key, value ->
            this."${key}" = value
        }

        this.aliases << this.title
        this.aliases.unique()
        jedis = Search.configure().redisPool.getResource()
    }

    def save() {
        if (!this.title || "".equals(this.title))
            return
        def data = [
                title: this.title,
                id: this.id,
                type: this.type
        ]

        this.exts.each { f ->
            data[f[0]] = f[1]
        }

        def res = jedis.hset(this.type, this.id, (data as JSON).toString())

        this.condition_fields.each { field ->
            jedis.sadd(Search.mk_condition_key(this.type, field, data[field.toString()]), this.id)
        }

        //score for search set
        jedis.set(Search.mk_score_key(this.type, this.id), this.score ? this.score : "0")


        //save set index
        this.aliases.each { val ->
            def words = split_words_for_index(val)
            if (words && "".equals(words))
                return
            words.each { word ->
                jedis.sadd(Search.mk_sets_key(this.type, word), this.id)
            }

        }
        if (this.prefix_index_enable)
            save_prefix_index()
    }

    static void remove(options = [:]) {
        def type = options['type']
        def jedis = Search.configure().redisPool.getResource()
        jedis.hdel(type, options['id'])
        def words = split_words_for_index(options['title'])
        words.each { word ->
            jedis.srem(Search.mk_sets_key(type, word), options['id'])
            jedis.del(Search.mk_score_key(type, options['id']))
        }
        jedis.srem(Search.mk_sets_key(type, options['title']), options['id'])
    }

    static private void split_words_for_index(title) {
        def words = Search.split(title)
        if (Search.configure().pinyin_match) {
            def pinyin_full = Search.split_pinyin(title)
            def pinyin_first = pinyin_full.collect { it[0] }.join()

            words += pinyin_full
            words << pinyin_first
        }
        words.unique()
    }

     private void save_prefix_index() {
        this.aliases.each { val ->
            def words = []
            words << val.toLowerCase()
            jedis.sadd(Search.mk_sets_key(this.type, val), this.id)
            if (Search.configure().pinyin_match) {
                def pinyin_full = Search.split_pinyin(val.toLowerCase())
                def pinyin_first = pinyin_full.collect{ it[0] }.join()
                def pinyin = pinyin_full.join("")
                words << pinyin
                words << pinyin_first
                jedis.sadd(Search.mk_sets_key(this.type, pinyin), this.id)
            }

            words.each { word ->
                def key = Search.mk_complete_key(this.type)
                (1..(word.length()-1)).each { len ->
                    def prefix = word[0..(len-1)]
                    jedis.zadd(key, 0, prefix)
                }
                jedis.zadd(key, 0, word + "*")
            }
        }
    }
}
