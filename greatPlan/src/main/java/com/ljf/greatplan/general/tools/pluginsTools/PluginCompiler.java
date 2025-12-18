/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.pluginsTools;

import com.ljf.greatplan.general.tools.generalTools.FileIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * 插件编译器<br/>
 * 用于在运行时将新加入的插件源码编译掉
 */
@Slf4j
@Component
public class PluginCompiler {
    /**
     * 文件IO工具类
     */
    private FileIO fileIO;

    /**
     * 构造器
     * @param fileIO 文件IO工具类
     */
    public PluginCompiler(FileIO fileIO) {
        this.fileIO = fileIO;
    }

    /**
     * 源码热编译器<br/>
     * 用于加载给定的目录，并将其中所有的java做成class
     * @param pluginDir 有需要编译为class文件的文件的目录
     * @return 编译后的class对象集合
     */
    public List<Class<?>> sourceHotCompiler(File pluginDir) {
        // 检查这个目录是否存在
        if (!pluginDir.exists()) {
            log.error("__________不存在的目录：{}", pluginDir.getAbsolutePath());
            throw new RuntimeException();
        }

        // 延迟半秒，但凡电脑太快，文件还没复制完监听就抓到目录出现了，扫目录就啥都找不到了
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 所有.java文件集合
        List<File> javaFiles = fileIO.fileCollector(pluginDir, ".java");

        // 检查是否有拉到.java
        if (javaFiles.isEmpty()) {
            log.error("__________插件{}中没有找到可供编译的 .java 文件", pluginDir);
            return Collections.emptyList();
        }

        // 编译输出路径（插件自己独立的class输出目录）
        File outputDir = new File(pluginDir, "out-classes");
        if (!outputDir.exists()) outputDir.mkdirs();

        // 判断任执行结果
        if (!startCompiler(javaFiles, outputDir)) {
            log.error("__________插件{}编译失败", pluginDir.getName());
            throw new RuntimeException();
        }
        log.info("__________插件{}编译成功", pluginDir.getName());

        // 创建类加载器实例
        URLClassLoader loader = null;
        try {
            // 将编译后的输出路径，和当前类加载器传入（用来实现每个插件均有独立的类加载器，不会交叉污染编译结果）
            loader = new URLClassLoader(
                    new URL[]{outputDir.toURI().toURL()},
                    this.getClass().getClassLoader()
            );
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // 这回就是扫描编译结果输出目录，把编译后的.class文件拉出来
        List<File> classFiles = fileIO.fileCollector(outputDir, ".class");

        // 用于存放最后的所有文件编译后且转换后的class对象
        List<Class<?>> loadedClasses = new ArrayList<>();
        // 循环所有编译后文件
        for (File classFile : classFiles) {
            // 获取源文件绝对路径与编译输出路径
            String absolutePath = classFile.getAbsolutePath();
            String outputPath = outputDir.getAbsolutePath();

            // 统一分隔符
            absolutePath = absolutePath.replace("\\", "/");
            outputPath = outputPath.replace("\\", "/");

            // 去掉前缀路径得到相对路径
            String relativePath = absolutePath.substring(outputPath.length() + 1);

            // 转成类名
            String className = relativePath
                    .replace("/", ".")
                    .replaceAll("\\.class$", "");

            try {
                // 根据相对路径获取这个文件，并获取它的class对象
                Class<?> clazz = loader.loadClass(className);
                // 加入临时容器
                loadedClasses.add(clazz);
                log.info("__________已加载：{}", className);
            } catch (ClassNotFoundException e) {
                log.error("__________无法加载：{}", className, e);
            }
        }
    // 返回所有处理好的class对象
    return loadedClasses;
    }

    /**
     * 启动编译<br/>
     * 创建并启动编译任务。
     * @param files 需要编译的文件集合
     * @param outputDir 编译输出目录
     * @return 是否成功
     */
    private boolean startCompiler(List<File> files, File outputDir) {
        // 创建编译器实例
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            log.error("__________未找到Java编译器，确保使用JDK运行");
            throw new IllegalStateException();
        }
        // 配置编译任务
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        // 将.java文件集合转为编译单元（任务单元）
        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(files);
        // 指定输出路径
        List<String> options = Arrays.asList("-d", outputDir.getAbsolutePath());
        // 创建编译任务（默认输出流, 文件管理器, 不使用自定义诊断监听, 编译参数, 不限制编译目标类名, 源文件集）
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
        // 执行编译任务并获取任务执行结果
        boolean success = task.call();
        try {
            // 关闭文件管理器
            fileManager.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return success;
    }
}
