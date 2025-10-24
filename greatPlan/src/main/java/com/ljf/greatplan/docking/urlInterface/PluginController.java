/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.urlInterface;

import com.ljf.greatplan.core.entity.StandardViewResponseObject;
import com.ljf.greatplan.core.service.PluginRegistryManager;
import com.ljf.greatplan.core.web.BaseController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 插件控制器<br/>
 * 用于获取插件相关信息的请求点
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController extends BaseController {
    /**
     * 插件注册表管理器
     */
    private final PluginRegistryManager registryManager;

    /**
     * 构造器
     * @param registryManager
     */
    public PluginController(PluginRegistryManager registryManager) {
        this.registryManager = registryManager;
    }

    /**
     * 获取插件注册表<br/>
     * 用于给页面返回最新的插件资源信息对象集合，保存了所有当前状态下注册了的插件资源
     * @return 插件资源对象集合
     */
    @GetMapping
    public StandardViewResponseObject<List<Map<String, Object>>> getPluginsRegistry() {
        return success(
                registryManager.getAll().stream()
                .map(plugin -> plugin.getPluginResourceInfo()) // 直接返回注册信息
                .collect(Collectors.toList())
        );
    }
}
