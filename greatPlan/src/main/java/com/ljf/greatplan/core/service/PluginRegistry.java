package com.ljf.greatplan.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
     * 插件注册表<br/>
     * <插件名, <资源类型, 资源于插件内的静态访问地址>>
     */
    private final Map<String, Map<String, Object>> registry = new ConcurrentHashMap<>();

    /**
     * 注册插件信息
     * @param id 插件名
     * @param meta 插件包含的资源表
     */
    public void register(String id, Map<String, Object> meta) {
        registry.put(id, meta);
        log.info("注册了新的插件：{}", id);
    }

    /**
     * 删除插件信息
     * @param id 插件名
     */
    public void remove(String id) {
        registry.remove(id);
        log.info("删除了一个插件：{}", id);
    }

    /**
     * 清理插件注册表的所有内容
     */
    public void clear() {
        registry.clear();
    }

    /**
     * 获取插件注册表的所有内容
     * @return 插件注册表的内容
     */
    public Collection<Map<String, Object>> getAll() {
        return registry.values();
    }
}
