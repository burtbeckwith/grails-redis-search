package com.qiyi.redis.search

import redis.clients.jedis.Jedis

/**
 * Created with IntelliJ IDEA.
 * User: kxc
 * Date: 13-4-27
 * Time: 下午2:35
 * To change this template use File | Settings | File Templates.
 */
class SearchConfig {
    def redisPool
    Boolean debug
    Integer complete_max_length
    Boolean pinyin_match
    Boolean disable_mmseg

    SearchConfig(){
        this.debug = false
        this.redisPool = null
        this.complete_max_length = 100
        this.pinyin_match = false
        this.disable_mmseg = false
    }
}
