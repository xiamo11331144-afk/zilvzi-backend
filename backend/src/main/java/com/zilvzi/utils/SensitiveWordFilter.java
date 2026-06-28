package com.zilvzi.utils;

import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class SensitiveWordFilter {
    private static final List<String> WORDS = Arrays.asList(
        "赌博","彩票","色情","裸聊","办证","刻章","枪支","毒品",
        "法轮功","邪教","反动","颠覆","暴恐","恐怖","炸弹",
        "傻逼","妈的","操你","fuck","shit","去死","废物","垃圾",
        "脑残","白痴","弱智","畜生","狗日的","他妈的","你妈",
        "草泥马","卧槽","妈逼","贱人","骚货","婊子"
    );

    public boolean containsSensitive(String text) {
        if (text == null) return false;
        for (String word : WORDS) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    public String findSensitiveWord(String text) {
        if (text == null) return null;
        for (String word : WORDS) {
            if (text.contains(word)) return word;
        }
        return null;
    }

    public String filter(String text) {
        if (text == null) return null;
        for (String word : WORDS) {
            text = text.replace(word, "***");
        }
        return text;
    }
}