package com.ljf.greatplan.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册表管理器<br/>
 * 保存插件信息至插件注册表，并管理这个注册表
 */
@Component
@Slf4j
public class PluginRegistry {
    /**
     * 插件对象<br/>
     * 整个内部类，定义插件对象，储存插件信息。
     * 目前只存一个对象，包含：<插件（补充包）名, 插件包含的资源集合对象>。
     * 资源集合对象包含：<资源类型, 资源文件名>。
     */
    public static class PluginInfo {
        /**
         * 插件信息对象
         */
        public Map<String, Object> meta;
    }

    /**
     * 插件注册表<br/>
     * 包含：<插件名, 插件对象>
     */
    private final Map<String, PluginInfo> registry = new ConcurrentHashMap<>();

    /**
     * 注册插件信息
     * @param id 插件名
     * @param meta 插件包含的资源表
     */
    public void register(String id, Map<String, Object> meta) {
        // 创建插件对象
        PluginInfo info = new PluginInfo();
        // 储存插件信息对象
        info.meta = meta;
        // 将新的插件注册至插件注册表
        registry.put(id, info);
        log.info("注册了新的插件：{}", id);
    }

    /**
     * 删除指定插件信息
     * @param id 插件名
     */
    public void remove(String id) {
        // 跟据key删除插件注册表中指定插件对象
        registry.remove(id);
        log.info("删除了一个插件：{}", id);
    }

    /**
     * 获取指定插件对象
     * @param id 对象名
     * @return 插件对象
     */
    public PluginInfo get(String id) {
        // 根据key查询插件注册表
        return registry.get(id);
    }

    /**
     * 清理插件注册表的所有内容
     */
    public void clear() {
        // 清空插件注册表
        registry.clear();
    }

    /**
     * 获取插件注册表的所有内容
     * @return 插件注册表的内容
     */
    public Collection<PluginInfo> getAll() {
        // 获取插件注册表内容
        return registry.values();
    }
}
