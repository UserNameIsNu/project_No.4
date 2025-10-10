package com.ljf.greatplan.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 插件资源映射器<br/>
 * 在核心包试图访问加入的补充包中的资源时，不要去静态资源目录和插件目录找，直接去插件资源副本目录找
 */
@Configuration
@Slf4j
public class PluginResourceConfig implements WebMvcConfigurer {
    /**
     * 插件资源映射地址
     */
    @Value("${plugin.runtime-dir}")
    private String pluginRuntimeDir;

    /**
     * 映射读取目录<br/>
     * 将试图访问根目录下的`plugins/`下的任意文件时，将访问地址改为配置文件提供的路径
     * @param registry 静态资源注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("插件资源映射器读取（存放）的目录路径：{}", pluginRuntimeDir);
        // 映射目录
        registry.addResourceHandler("/plugins/**")
                .addResourceLocations("file:" + pluginRuntimeDir.replace("\\", "/") + "/");
    }
}
