/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.util.other;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class FileIO {
    /**
     * 文件收集器<br/>
     * 用于从指定目录内递归收集所有指定格式的文件，以集合形式返回。
     * @param files 目标目录
     * @param format 目标文件格式（.后缀名）
     * @return 收集到的文件集合
     */
    public static List<File> fileCollector(File files, String format) {
        List<File> outcome = null;
        try {
            // 打开目录并递归遍历
            outcome = Files.walk(files.toPath())
                    // 仅抓取指定格式的文件
                    .filter(p -> p.toString().endsWith(format))
                    // 加入一个临时集合
                    .map(java.nio.file.Path::toFile)
                    // 转换为List
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outcome;
    }
}
