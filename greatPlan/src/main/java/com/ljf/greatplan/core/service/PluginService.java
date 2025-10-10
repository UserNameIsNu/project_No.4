package com.ljf.greatplan.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 插件服务器<br/>
 * 在运行时扫描插件目录，装载插件资源（复制资源至运行目录），卸载插件资源（删除运行目录）
 */
@Service
@Slf4j
public class PluginService {
    /**
     * 插件资源地址
     */
    @Value("${plugin.root-dir}")
    private String pluginRootDir;

    /**
     * 插件资源映射地址
     */
    @Value("${plugin.runtime-dir}")
    private String pluginRuntimeDir;

    /**
     * 插件注册表
     */
    private final PluginRegistry registry;

    /**
     * 构造器
     * @param registry
     */
    public PluginService(PluginRegistry registry) {
        this.registry = registry;
    }

    /**
     * 插件扫描器<br/>
     * 扫描指定目录，将其中插件的静态资源提取，资源定义，资源复制，并将其注册至插件注册表
     */
    public void scanPlugins() {
        // 按照两个地址，打开它们
        File rootDir = new File(pluginRootDir);
        File runtimeDir = new File(pluginRuntimeDir);

        // 清理插件注册表
        registry.clear();

        // 遍历插件资源（每个插件就是一个SB项目）
        for (File pluginDir : Objects.requireNonNull(rootDir.listFiles(File::isDirectory))) {
            // 在目录下找资源（等于就是直接去项目目录内的静态资源目录下找东西）
            File staticDir = new File(pluginDir, "src/main/resources/static");
            // 没有资源就跳过这层目录
            if (!staticDir.exists()) continue;
            // 在映射目录内创建文件
            File targetDir = new File(runtimeDir, pluginDir.getName());
            // 把插件资源复制到映射目录内同名文件内
            try {
                copyFolder(staticDir.toPath(), targetDir.toPath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 定义资源类型，与资源路径（相对于插件内的静态资源目录下的路径）
            Map<String, Object> meta = new HashMap<>();
            meta.put("id", pluginDir.getName());
            meta.put("html", "index.html");
            meta.put("js", "js/index.js");
            meta.put("css", "css/index.css");

            // 注册插件至插件注册表
            registry.register(pluginDir.getName(), meta);
            log.info("已注册的插件数量：{}", registry.getAll().size());
        }
    }

    /**
     * 资源复制器<br/>
     * 将源目录内的东西复制到目标目录内
     * @param source 源目录
     * @param target 目标目录
     */
    private void copyFolder(Path source, Path target) {
        try {
            // 遍历源目录
            Files.walk(source).forEach(s -> {
                try {
                    // 得到相对于源目录与目标目录的路径
                    Path d = target.resolve(source.relativize(s));
                    // 若拿到的东西是文件就复制进目标目录，是目录就创建
                    if (Files.isDirectory(s)) {
                        if (!Files.exists(d)) {
                            Files.createDirectories(d);
                        }
                    } else {
                        Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取所有插件信息<br/>
     * 就是把插件注册表里面注册的插件全部拿出来看看
     * @return 注册表内容（可以做增强循环的Map）
     */
    public Iterable<Map<String, Object>> getAllPlugins() {
        return registry.getAll();
    }

    /**
     * 卸载插件<br/>
     * 把指定插件名的插件从插件注册表中移除，映射目录内的与这个插件相关的资源也全部删掉
     * @param pluginId 插件名
     */
    public void removePlugin(String pluginId) {
        // 根据插件注册器的地址与给定的插件名打开这个目录
        File targetDir = new File(pluginRuntimeDir, pluginId);
        // 只要地址有效（有东西）
        if (targetDir.exists()) {
            try {
                // 删除文件夹
                deleteFolder(targetDir.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 清理插件注册表
        registry.remove(pluginId);
        log.info("已卸载一个插件：{}", pluginId);
    }

    /**
     * 删除目录<br/>
     * 删除给定的目录与其中的所有东西
     * @param path 目录地址
     */
    private void deleteFolder(Path path) {
        // 若地址无效（没东西）就直接退出
        if (!Files.exists(path)) return;
        try {
            // 拿到所有的文件与目录
            Files.walk(path)
                    // 对目录内容排序（反过来排序，一会儿从最深层开始删）
                    .sorted(Comparator.reverseOrder())
                    // 遍历
                    .forEach(p -> {
                        try {
                            // 删除所有路径指向的东西（不管是文件还是目录）
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
