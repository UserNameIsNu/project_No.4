package com.ljf.greatplan.docking.plugins.aiSecretary.toolMethodFromAI;

import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;

/**
 * 字符串工具Bean，支持字符串反转等AI方法。
 */
@Component
public class test_1 {
    /**
     * 反转字符串（AI工具方法）
     * @param input 需要反转的字符串
     * @return 反转后的字符串
     */
    @Tool(description = "传入一个字符串并返回它的逆序字符串")
    public String reverseString(String input) {
        if (input == null) return null;
        return new StringBuilder(input).reverse().toString();
    }
}

