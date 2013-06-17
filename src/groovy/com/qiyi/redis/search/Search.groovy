package com.qiyi.redis.search

import grails.converters.JSON

import redis.clients.jedis.SortingParams

import com.chenlb.mmseg4j.ComplexSeg
import com.chenlb.mmseg4j.Dictionary
import com.chenlb.mmseg4j.MMSeg
import com.chenlb.mmseg4j.Seg
import com.qiyi.redis.search.utils.PinyinUtil

/**
 * @author kxc
 */
class Search {

    static indexed_models
    static SearchConfig config
    static Dictionary dic

    static configure() {
        if (!config) {
            config = new SearchConfig()
        }

        if (!config.disable_mmseg) {
            dic = Dictionary.getInstance()
        }

        return config
    }

    //use mmseg
    static split(text) {
        _split(text)
    }

    static complete(type, w, options = [:]) {
        def jedis = Search.configure().redisPool.getResource()
        def limit = options["limit"] ? options['limit'] : 10
        def conditions = options['conditions'] ? options['conditions'] : []
        if (((!w || w == "") && (!conditions || conditions.isEmpty())) || (!type || "" == type))
            return []
        def prefix_matches = []

        def range_len = Search.config.complete_max_length
        def prefix = w.toLowerCase()
        def key = Search.mk_complete_key(type)
        def start = jedis.zrank(key, prefix)
        if (start != null) {
            def count = limit
            int max_range = start + (range_len * limit) - 1
            def range = jedis.zrange(key, start as int, max_range as int)
            while (prefix_matches.size() <= count) {
                start += range_len
                if (!range || range.size() == 0)
                    break
                range.each { entry ->
                    def min_len = Math.min(entry.length(), prefix.length())
                    if (entry[0..(min_len - 1)] != prefix[0..(min_len - 1)]) {
                        count = prefix_matches.size()
                        return
                    }
                    if (entry[-1..-1] == "*" && prefix_matches.size() != count) {
                        entry as String
                        prefix_matches << entry.substring(0, (entry.length() - 1))
                    }
                }
                if (start >= range.size()) {
                    range = []
                } else {
                    range = range.asList()[start..Math.min((max_range - 1), (range.size() - 1))]
                }
            }
        }

        //组合words 特别key名
        def words = []
        words = prefix_matches.unique().collect { wd ->
            Search.mk_sets_key(type, wd)
        }

        // 组合特别 key ,但这里不会像 query 那样放入 words， 因为在 complete 里面 words 是用 union 取的，condition_keys 和 words 应该取交集
        def condition_keys = []
        if (!conditions || conditions.isEmpty()) {
            if (conditions instanceof ArrayList) {
                conditions = conditions[0]
            }
            conditions.each { c_key, c_value ->
                condition_keys << Search.mk_condition_key(type, c_key, c_value)
            }
        }

        //按词搜索
        def temp_store_key = "tmpsunionstore:${words.join("+")}"
        if (words.size() > 0) {
            if (!jedis.exists(temp_store_key)) {
                //将多个词语组合对比，得到并集，并存入临时区域
                jedis.sunionstore(temp_store_key, words as String[])
                // 将临时搜索设为1天后自动清除
                jedis.expire(temp_store_key, 86400)
            }
        } else {
            if (words.size() == 1) {
                temp_store_key = words.first()
            } else {
                return []
            }

        }


        if (condition_keys || !condition_keys.isEmpty()) {
            if (!words || words.isEmpty()) {
                condition_keys << temp_store_key
            }
            temp_store_key = "tmpsinterstore:${condition_keys.join("+")}"
            if (!jedis.exists(temp_store_key)) {
                jedis.sinterstore(temp_store_key, condition_keys as String[])
                jedis.expire(temp_store_key, 86400)
            }
        }

        SortingParams sort_params = new SortingParams().limit(0, limit).by(Search.mk_score_key(type, "*")).desc()
        def ids = jedis.sort(temp_store_key, sort_params)
        if (!ids || ids.isEmpty())
            return []
        return hmget(jedis, type, ids)
    }

    //search items,this will split words by libmmseg

    static query(type, text, options = [:]) {
        def jedis = Search.configure().redisPool.getResource()
        def tm = System.currentTimeMillis()
        def result = []
        def limit = options['limit'] ? options['limit'] : 10
        def sort_field = options['sort_field'] ? options['sort_field'] : "id"
        def conditions = options['conditions'] ? options['conditions'] : []

        if ((!text || "" == text.trim()) && (!conditions || conditions.isEmpty()))
            return result

        def words = Search.split(text)
        words = words.collect { w ->
            Search.mk_sets_key(type, w)
        }


        def condition_keys = []
        if (conditions && !conditions.isEmpty()) {
            if (conditions instanceof ArrayList)
                conditions = conditions[0]
            conditions.each { key, val ->
                condition_keys << Search.mk_condition_key(type, key, val)
            }
            words += condition_keys
        }

        if (!words || words.isEmpty())
            return result

        def temp_store_key = "tmpinterstore:${words.join("+")}"

        if (words.size() > 0) {
            if (!jedis.exists(temp_store_key)) {
                jedis.sinterstore(temp_store_key, words as String[])
                jedis.expire(temp_store_key, 86400)
            }

            //搜索拼音
            if (Search.config.pinyin_match) {
                def pinyin_words = Search.split_pinyin(text)
                pinyin_words = pinyin_words.collect { w ->
                    Search.mk_sets_key(type, w)
                }
                pinyin_words += condition_keys
                def temp_sunion_key = "tmpsunionstore:${pinyin_words.join("+")}"
                def temp_pinyin_store_key
                if (Search.config.pinyin_match)
                    temp_pinyin_store_key = "tmpinterstore:${pinyin_words.join("+")}"
                //找出拼音的
                jedis.sinterstore(temp_pinyin_store_key, pinyin_words as String[])
//               合并中文和拼音的搜索结果
                jedis.sunionstore(temp_sunion_key, [temp_store_key, temp_pinyin_store_key] as String[])
                //将临时搜索设为1天后自动清楚
                jedis.expire(temp_pinyin_store_key, 86400)
                jedis.expire(temp_sunion_key, 86400)
                temp_store_key = temp_sunion_key
            }
        } else {
//            if (words.size() == 1) {
//                temp_store_key = words.first()
//            }
            return result
        }

        SortingParams sort_params = new SortingParams().limit(0, limit).by(Search.mk_score_key(type, "*")).desc()
        def ids = jedis.sort(temp_store_key, sort_params)
        return hmget(jedis, type, ids)
    }


    static mk_sets_key(String type, String key) {
        "${type}:${key.toLowerCase()}"
    }


    static mk_score_key(def type, def id) {
        "${type}:_score_:${id}"
    }

    static mk_condition_key(type, field, id) {
        "${type}:_by:_${field}:#{id}"
    }

    static mk_complete_key(type) {
        "Compl${type}"
    }

    static hmget(jedis, type, ids, options = [:]) {
        def result = []
        def sort_field = options['sort_field'] || "id"
        if (!ids || ids.isEmpty()) {
            return result
        }
        try {
            String[] ids_params = new String[ids.size()]
            int i = 0
            ids.each {
                ids_params[i++] = it.toString()
            }
            jedis.hmget(type, ids_params).each { r ->
                if (r && !"".equals(r)) {
                    result << JSON.parse(r)
                }
            }
            return result
        } catch (Exception e) {
            e.printStackTrace()
        }
    }

    static private _split(text) {
        if (Search.config.disable_mmseg)
            return text.split()
        Seg seg = new ComplexSeg(dic)
        MMSeg mmSeg = new MMSeg(new StringReader(text), seg)
        def words = []
        def word
        while ((word = mmSeg.next()) != null) {
            words << word.getString()
        }
        return words
    }

    static split_pinyin(text) {
        _split(PinyinUtil.getHanyuPinyin(text))
    }
}
