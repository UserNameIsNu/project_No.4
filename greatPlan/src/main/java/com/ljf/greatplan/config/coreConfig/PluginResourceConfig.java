/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.config.coreConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 插件资源配置器<br/>
 * 设置插件资源的路径映射配置
 */
@Configuration
public class PluginResourceConfig implements WebMvcConfigurer {
    /**
     * 添加资源处理器
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 在资源处理器注册表中添加插件资源的映射路径
        registry.addResourceHandler("/plugins/**")
                .addResourceLocations("file:src/main/resources/static/plugins/");
    }
}
