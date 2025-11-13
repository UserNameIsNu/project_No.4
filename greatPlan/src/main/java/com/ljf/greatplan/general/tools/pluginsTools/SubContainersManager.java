/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.pluginsTools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子容器管理器<br/>
 * 用于管理插件子容器的生命周期。
 * 也就是创建和销毁。
 */
@Component
@Slf4j
public class SubContainersManager {
    /**
     * 应用程序上下文（父容器）
     */
    private final ApplicationContext parentContext;

    /**
     * 插件独立上下文（子容器）
     */
    private final Map<String, ConfigurableApplicationContext> pluginContexts = new ConcurrentHashMap<>();

    /**
     * 构造器
     * @param parentContext 应用程序上下文（父容器）
     */
    public SubContainersManager(ApplicationContext parentContext) {
        this.parentContext = parentContext;
    }

    /**
     * 挂载子容器<br/>
     * 创建指定插件的独立子容器，并继承自父容器。
     * @param pluginName 插件名
     * @param pluginClasses 需要加入的Bean集合
     * @param pluginDir 编译输出目录
     */
    public void mountSubContainer(String pluginName, List<Class<?>> pluginClasses, File pluginDir) throws MalformedURLException {
        // 检查子容器是否已存在（虽然一般应该不会存在）
        if (pluginContexts.containsKey(pluginName)) {
            unloadSubContainer(pluginName);
        }
        // 创建子容器
        AnnotationConfigApplicationContext pluginContext = new AnnotationConfigApplicationContext();
        pluginContext.setParent(parentContext);
        // 指定输出路径（和插件编译器中的一样，变成豆子后就要进编译目录了）
        File outputDir = new File(pluginDir, "out-classes");
        if (!outputDir.exists()) outputDir.mkdirs();
        // 继承主容器的类加载器
        ClassLoader parentLoader = parentContext.getClassLoader();
        URLClassLoader pluginClassLoader = (new URLClassLoader(new URL[]{outputDir.toURI().toURL()}, parentLoader));
        pluginContext.setClassLoader(pluginClassLoader);
        // 注册插件中的Bean
        for (Class<?> clazz : pluginClasses) {
            pluginContext.register(clazz);
        }
        // 启动子容器
        pluginContext.refresh();
        // 把子容器加入插件独立上下文保存（保存引用）
        pluginContexts.put(pluginName, pluginContext);
        log.info("__________创建了子容器{}", pluginName);
    }

    /**
     * 卸载子容器<br/>
     * 删除指定插件的子容器，所有相关的Bean都会被一并卸载。
     * @param pluginName 插件名
     */
    public void unloadSubContainer(String pluginName) {
        // 删除插件独立上下文中的子容器
        ConfigurableApplicationContext context = pluginContexts.remove(pluginName);
        // 关闭子容器，关闭时其中的Bean都会被自动销毁
        if (context != null) {
            try {
                context.close();
                log.info("__________子容器{}已被销毁", pluginName);
            } catch (Exception e) {
                log.error("__________子容器{}销毁失败", pluginName, e);
            }
        } else {
            log.error("__________子容器{}不存在", pluginName);
        }
    }
}
