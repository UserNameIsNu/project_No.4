package com.ljf.greatplan.core.service;

import com.ljf.greatplan.util.other.IOAndSerializationAndString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * 插件服务器<br/>
 * 在运行时扫描插件目录，注册或卸载插件资源
 */
@Service
@Slf4j
public class PluginService {
    /**
     * 插件资源目录
     */
    @Value("${plugin.resource-dir}")
    private String pluginResourcePath;

    /**
     * 资源格式白名单
     */
    @Value("${plugin.pass-format}")
    private String passFormat;

    /**
     * 插件注册表管理器
     */
    private final PluginRegistryManager pluginRegistryManager;

    /**
     * 构造器
     * @param pluginRegistryManager
     */
    public PluginService(PluginRegistryManager pluginRegistryManager) {
        this.pluginRegistryManager = pluginRegistryManager;
    }

    /**
     * 插件资源扫描器<br/>
     * 扫描插件资源目录，然后写入插件注册表注册。
     * 不管插件源码目录。
     */
    public void pluginsScanner() {
        // 清空插件注册表
        pluginRegistryManager.clear();

        // 获取插件资源格式白名单
        Set<String> formatSet = IOAndSerializationAndString.getAllFormat(passFormat);
        Map<String, Map<String, Object>> pluginMetaMap = new HashMap<>();

        try {
            // 扫描资源目录
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("file:" + pluginResourcePath + "/**/*.*");
            // 遍历所有资源
            for (Resource res : resources) {
                // 获取资源名
                String filename = res.getFilename().toLowerCase();
                // 获取插件名
                String pluginName = res.getURL().toString()
                        .replaceFirst(".*/static/plugins/", "")
                        .split("/")[0];
                // 创建插件资源信息对象
                pluginMetaMap.computeIfAbsent(pluginName, k -> {
                    Map<String, Object> meta = new HashMap<>();
                    // 初始化一个标记，就放个插件名，标记这个对象的所属插件
                    meta.put("id", pluginName);
                    return meta;
                });
                // 取出上面创建并初始化的插件资源信息对象
                Map<String, Object> meta = pluginMetaMap.get(pluginName);
                // 遍历插件资源格式白名单
                for (String ext : formatSet) {
                    // 判断当前资源文件名是否是插件资源格式白名单中允许的格式
                    if (filename.endsWith(ext)) {
                        // 获取资源类型
                        String type = ext.substring(1);
                        // 创建资源类型对应的资源集合对象
                        meta.computeIfAbsent(type, k -> new ArrayList<String>());
                        // 资源相对路径，去掉资源路径的前缀路径，只取资源名
                        String relativePath = res.getURL().toString()
                                .replaceFirst(".*/static/plugins/", "");
                        // 添加资源（类型与名字）
                        ((List<String>) meta.get(type)).add(relativePath);
                        break;
                    }
                }
            }

            // 把插件资源信息对象注册进插件注册表
            pluginMetaMap.forEach((pluginName, meta) ->
                    pluginRegistryManager.registerPlugin(pluginName, meta)
            );

            log.info("已注册插件数：{}", pluginRegistryManager.getAll().size());

        } catch (IOException e) {
            throw new RuntimeException("扫描插件资源失败", e);
        }
    }

    /**
     * 卸载插件<br/>
     * 把指定插件名的插件从插件注册表中移除
     * @param id 插件名
     */
    public void removePlugin(String id) {
        // 卸载指定插件
        pluginRegistryManager.uninstallPlugin(id);
    }
}
