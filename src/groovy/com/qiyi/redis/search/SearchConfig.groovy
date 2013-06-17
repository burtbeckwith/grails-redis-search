package com.qiyi.redis.search

/**
 * @author kxc
 */
class SearchConfig {
    def redisPool
    Boolean debug = false
    Integer complete_max_length = 100
    Boolean pinyin_match = false
    Boolean disable_mmseg = false
}
