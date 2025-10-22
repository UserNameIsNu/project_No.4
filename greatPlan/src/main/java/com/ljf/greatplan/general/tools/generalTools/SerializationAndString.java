/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.generalTools;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 序列化与字符串工具类<br/>
 * 封装了序列化操作与字符串操作相关的方法。
 */
public class SerializationAndString {
    /**
     * 分割字符串<br/>
     * 根据指定符号分割指定字符串。
     * @param str 要分割的字符串
     * @param separator 分割符
     * @return 分割后的成员集合
     */
    public static Set<String> splitStrings(String str, String separator) {
        // 转为流操作
        // 根据逗号分割这个字符串
        return Arrays.stream(str.split(separator))
                // 把割好的东西装进临时集合里
                .map(String::trim)
                // 只保留不为空的值
                .filter(s -> !s.isEmpty())
                // 把结果保存为Set集合
                .collect(Collectors.toSet());
    }
}
