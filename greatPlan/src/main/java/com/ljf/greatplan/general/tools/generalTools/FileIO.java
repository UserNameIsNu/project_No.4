/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.generalTools;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件IO工具类<br/>
 * 封装了文件操作与IO操作相关的方法。
 */
@Component
public class FileIO {
    /**
     * 异常日志地址
     */
    @Value("${error-log.path}")
    private String errorLogPath;

    /**
     * 调用链打印深度
     */
    @Value("${error-log.stackTrace-deep}")
    private String stackTraceDeep;

    /**
     * 文件收集器<br/>
     * 用于从指定目录内递归收集所有指定格式的文件，以集合形式返回。
     * @param files 目标目录
     * @param format 目标文件格式（.后缀名）
     * @return 收集到的文件集合
     */
    public List<File> fileCollector(File files, String format) {
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
     * @return 根目录集合
     */
    public List<String> getRoot() {
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
    public String getFileExtension(String filePath) {
        int dot = filePath.lastIndexOf(".");
        return (dot == -1) ? "" : filePath.substring(dot + 1);
    }

    /**
     * 生成ID<br/>
     * 根据文件或目录的路径创建哈希ID.
     * @param file 文件（目录）目标
     * @return 这个文件（目录）的ID
     */
    public String generateId(File file) {
        return Integer.toHexString(file.getAbsolutePath().hashCode());
    }

    /**
     * 删除文件后缀<br/>
     * 根据路径切割，获取没有带文件后缀的路径。
     * @param name 文件路径
     * @return 没后缀的文件路径
     */
    public String stripExtension(String name) {
        int dot = name.lastIndexOf(".");
        return (dot == -1) ? name : name.substring(0, dot);
    }

    /**
     * 异常写入<br/>
     * 用于将异常写进异常日志
     * @param e 异常实例
     */
    public void exceptionWrite(Throwable e) {
        // 创建字符串构建器
        StringBuilder sb = new StringBuilder();

        // 构建内容
        sb.append("\n================= " + LocalDateTime.now() + " =================\n");
        sb.append("错误类型：").append(e.getClass().getName()).append("\n");
        sb.append("错误消息：").append(e.getMessage()).append("\n");
        // 错误位置（其实就是调用链的最末节点）
        if (e.getStackTrace().length > 0) {
            StackTraceElement top = e.getStackTrace()[0];
            sb.append("位置：")
                    .append(top.getClassName())
                    .append(".")
                    .append(top.getMethodName())
                    .append(" (")
                    .append(top.getFileName())
                    .append(":")
                    .append(top.getLineNumber())
                    .append(")\n");
        }
        // 输出完整调用链
        sb.append("调用链：\n");
        StackTraceElement[] stackTrace = e.getStackTrace();
        // 根据配置文件决定调用链的打印深度
        Integer deep;
        // 全量打印
        if (stackTraceDeep.equals("all")) {
            deep = stackTrace.length;
        // 定量打印
        } else {
            deep = Integer.parseInt(stackTraceDeep);
        }
        for (int i = 0; i < deep; i++) {
            StackTraceElement element = stackTrace[i];
            sb.append("  [").append(i).append("] ")
                    .append(element.getClassName())
                    .append(".")
                    .append(element.getMethodName())
                    .append("(")
                    .append(element.getFileName())
                    .append(":")
                    .append(element.getLineNumber())
                    .append(")\n");
        }
        sb.append("========================================================\n");

        // 定义文件名（包括路径）
        String path = errorLogPath + "/error.log";
        // 创建并追加日志记录
        createFileByName(path);
        addToFile(sb, path);
    }

    /**
     * 在指定位置创建目录<br/>
     * 允许递归创建深度目录。
     * 避免预期目录和实际目录之间没有直接或间接的线性从属关系，导致无法接续。
     * @param path 文件地址
     */
    public void createFileByName(String path) {
        // 打开地址
        Path logFile = Paths.get(path);
        try {
            // 若目录不存在就创建
            Files.createDirectories(logFile.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 追加内容至指定文件
     * @param content 内容
     * @param path 文件地址
     */
    public void addToFile(StringBuilder content, String path) {
        // 打开地址
        Path logFile = Paths.get(path);
        try {
            // 写入
            Files.writeString(
                    logFile, // 写进这里
                    content, // 写这些东西进去
                    StandardOpenOption.CREATE, // 若文件不存在就先创建
                    StandardOpenOption.APPEND // 追加模式
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
