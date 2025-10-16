package com.ljf.greatplan.util.other;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IO，序列化与字符串工具类<br/>
 * 封装了文件IO，序列及反序列化与字符串相关的方法。
 */
public class IOAndSerializationAndString {
    /**
     * 获取所有格式<br/>
     * 把配置文件中标定的字符串形式的资源格式白名单，以逗号裁剪为资源格式白名单集合
     * @return 资源格式白名单
     */
    public static Set<String> getAllFormat(String passFormat) {
        // 转为流操作
        // 根据逗号分割这个字符串
        return Arrays.stream(passFormat.split(","))
                // 把割好的东西装进临时集合里
                .map(String::trim)
                // 只保留不为空的值
                .filter(s -> !s.isEmpty())
                // 把结果保存为Set集合
                .collect(Collectors.toSet());
    }
}
