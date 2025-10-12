package com.ljf.greatplan.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

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
     * 资源格式白名单
     */
    @Value("${plugin.pass-format}")
    private String passFormat;

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
     * 格式裁剪<br/>
     * 把配置文件中标定的字符串形式的资源格式白名单，以逗号裁剪为资源格式白名单集合
     * @return 资源格式白名单
     */
    private Set<String> getAllFormat() {
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

        // 拿到白名单格式集合
        Set<String> formatSet = getAllFormat();
        log.info("资源格式白名单：{}", formatSet);

        // 遍历插件资源（每个插件就是一个SB项目）
        for (File pluginDir : Objects.requireNonNull(rootDir.listFiles(File::isDirectory))) {
            // 在目录下找资源
            File staticDir = new File(pluginDir, "resources");
            // 没有资源就跳过这层目录
            if (!staticDir.exists()) continue;
            // 在映射目录内创建文件
            File targetDir = new File(runtimeDir, pluginDir.getName());
            // 把插件资源复制到映射目录内同名文件内
            try {
                copyFolder(staticDir.toPath(), targetDir.toPath(), formatSet);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 定义资源类型，与资源路径（相对于插件内的静态资源目录下的路径）
            Map<String, Object> meta = new HashMap<>();
            meta.put("id", pluginDir.getName());

            try {
                // 遍历资源目录
                Files.walk(staticDir.toPath())
                        // 若文件不是不存在或是个隐藏文件啥的特殊文件就继续注册
                        .filter(Files::isRegularFile)
                        // 循环处理
                        .forEach(path -> {
                            // 获取当前文件的格式
                            String fileName = path.getFileName().toString().toLowerCase();
                            // 遍历白名单
                            for (String ext : formatSet) {
                                // 若文件名末尾与白名单中允许的格式匹配
                                if (fileName.endsWith(ext)) {
                                    // 删掉格式前的‘.’
                                    String type = ext.substring(1);
                                    // 在注册表中找找有没有这个格式
                                    meta.computeIfAbsent(type, k -> new ArrayList<String>());
                                    // 获取路径
                                    String relativePath = staticDir.toPath().relativize(path).toString().replace("\\", "/");
                                    // 把格式和路径塞进集合里
                                    ((List<String>) meta.get(type)).add(relativePath);
                                    break;
                                }
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("扫描插件文件失败: " + pluginDir.getName(), e);
            }

            // 注册插件至插件注册表
            registry.register(pluginDir.getName(), meta);
            log.info("已注册的插件数量：{}", registry.getAll().size());
            log.info("允许并通过的文件类型：{}", meta.keySet());
        }
    }

    /**
     * 资源复制器<br/>
     * 将源目录内的东西复制到目标目录内
     * @param source 源目录
     * @param target 目标目录
     * @param formatSet 白名单集合
     */
    private void copyFolder(Path source, Path target, Set<String> formatSet) {
        try {
            // 遍历源目录
            Files.walk(source).forEach(s -> {
                try {
                    // 得到相对于源目录与目标目录的路径
                    Path d = target.resolve(source.relativize(s));
                    // 若是目录
                    if (Files.isDirectory(s)) {
                        // 是否存在
                        if (!Files.exists(d)) {
                            // 不存在就创建
                            Files.createDirectories(d);
                        }
                    // 若是文件
                    } else {
                        // 获取文件名
                        String fileName = s.getFileName().toString().toLowerCase();
                        // 若格式存在与白名单中
                        if (formatSet.stream().anyMatch(fileName::endsWith)) {
                            // 创建
                            Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                        }
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
    public Iterable<PluginRegistry.PluginInfo> getAllPlugins() {
        return registry.getAll();
    }

    /**
     * 卸载插件<br/>
     * 把指定插件名的插件从插件注册表中移除，映射目录内的与这个插件相关的资源也全部删掉
     * @param pluginId 插件名
     */
    public void removePlugin(String pluginId) {
        // 删除静态资源
        File targetDir = new File(pluginRuntimeDir, pluginId);
        if (targetDir.exists()) deleteFolder(targetDir.toPath());

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
