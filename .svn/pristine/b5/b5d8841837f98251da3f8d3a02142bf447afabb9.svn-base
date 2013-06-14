package com.qiyi.redis.search.utils

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination

/**
 * Created with IntelliJ IDEA.
 * User: kxc
 * Date: 13-4-27
 * Time: 下午4:03
 * To change this template use File | Settings | File Templates.
 */
class PinyinUtil {
    public static String getHanyuPinyin(String strCN)
    throws BadHanyuPinyinOutputFormatCombination {
        if(null == strCN){
            return null;
        }
        StringBuffer spell = new StringBuffer();
        char[] charOfCN = strCN.toCharArray();
        HanyuPinyinOutputFormat defaultFormat = new HanyuPinyinOutputFormat();
        defaultFormat.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        defaultFormat.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        for (int i = 0; i < charOfCN.length; i++) {
            // 是否为中文字符
            if (charOfCN[i] > 128) {
                String[] spellArray = PinyinHelper.toHanyuPinyinStringArray(
                        charOfCN[i], defaultFormat);
                if (null != spellArray) {
                    spell.append(spellArray[0]);
                }else{
                    spell.append(charOfCN[i]);
                }
            } else {
                spell.append(charOfCN[i]);
            }
        }
        return spell.toString();
    }
}
