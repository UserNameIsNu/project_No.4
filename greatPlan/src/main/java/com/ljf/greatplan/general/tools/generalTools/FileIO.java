/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.generalTools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件IO工具类<br/>
 * 封装了文件操作与IO操作相关的方法。
 */
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

    /**
     * 获取当前运行设备的根目录<br/>
     * 如windows，我这电脑就有C，D两个盘根。
     * @return
     */
    public static List<String> getRoot() {
        // 根目录集合
        List<String> rootPaths = new ArrayList<>();
        // 获取文件对象的所有根（似乎是会直接取到文件系统的）
        File[] roots = File.listRoots();
        // 遍历所有根
        for (File root : roots) {
            // 把路径拎出来
            String path = root.getAbsolutePath();
            // 把盘符剪出来（C:/变成C:）
            if (path.contains("\\")) {
                path = path.substring(0, 2) + "/";
            }
            // 塞进集合
            rootPaths.add(path);
        }
        return rootPaths;
    }

    /**
     * 获取文件后缀<br/>
     * 根据文件路径切割，取最后面的后缀段。
     * 路径不管是绝对路径，还是单个文件名都可以。
     * 直接切割带来的自信。
     * @param filePath 文件路径
     * @return 文件后缀
     */
    public static String getFileExtension(String filePath) {
        int dot = filePath.lastIndexOf(".");
        return (dot == -1) ? "" : filePath.substring(dot + 1);
    }

    /**
     * 生成ID<br/>
     * 根据文件或目录的路径创建哈希ID.
     * @param file 文件（目录）目标
     * @return 这个文件（目录）的ID
     */
    public static String generateId(File file) {
        return Integer.toHexString(file.getAbsolutePath().hashCode());
    }

    /**
     * 删除文件后缀<br/>
     * 根据路径切割，获取没有带文件后缀的路径。
     * @param name 文件路径
     * @return 没后缀的文件路径
     */
    public static String stripExtension(String name) {
        int dot = name.lastIndexOf(".");
        return (dot == -1) ? name : name.substring(0, dot);
    }
}
