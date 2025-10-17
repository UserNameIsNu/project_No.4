/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册表管理器<br/>
 * 保存插件信息至插件注册表，并管理这个注册表.
 */
@Component
@Slf4j
public class PluginRegistryManager {
    /**
     * 插件对象<br/>
     * 内部类，定义插件对象，和储存插件信息。
     */
    @Data
    public static class Plugin {
        /**
         * 插件资源信息对象：<插件名, 资源集合对象>。<br/>
         * 资源集合对象：<资源类型, 资源文件名>。
         */
        public Map<String, Object> pluginResourceInfo;
        /**
         * 插件版本
         */
        private String version;
        /**
         * 源码路径
         */
        private String sourcePath;
        /**
         * 资源路径
         */
        private String resourcePath;
    }

    /**
     * 插件注册表<br/>
     * 包含：<插件名, 插件对象>
     */
    private final Map<String, Plugin> pluginRegistry = new ConcurrentHashMap<>();

    /**
     * 注册插件
     * @param id 插件名
     * @param pluginResourceInfo 插件资源信息对象
     */
    public void registerPlugin(String id, Map<String, Object> pluginResourceInfo) {
        // 创建插件对象
        Plugin info = new Plugin();
        // 储存插件资源信息对象
        info.pluginResourceInfo = pluginResourceInfo;
        // 将插件对象注册至插件注册表
        pluginRegistry.put(id, info);
        log.info("注册了一个插件：{}", id);
    }

    /**
     * 卸载插件
     * @param id 插件名
     */
    public void uninstallPlugin(String id) {
        // 跟据插件名删除插件注册表中指定插件对象
        pluginRegistry.remove(id);
        log.info("卸载了一个插件：{}", id);
    }

    /**
     * 清空插件注册表
     */
    public void clear() {
        // 清空插件注册表
        pluginRegistry.clear();
    }

    /**
     * 获取所有被注册的插件
     * @return 插件注册表中被注册的插件对象
     */
    public Collection<Plugin> getAll() {
        // 获取插件注册表的所有值
        return pluginRegistry.values();
    }
}
