/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.listener.pluginsListener;

import com.ljf.greatplan.general.tools.pluginsTools.PluginCompiler;
import com.ljf.greatplan.general.tools.pluginsTools.SubContainersManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.List;

/**
 * 插件（源码）监听器<br/>
 * 监听插件源码放置目录，引发装载行为
 */
@Component
@Slf4j
public class PluginSourceListener {
    /**
     * 插件源码地址
     */
    @Value("${great-plan.plugin.source-dir}")
    private String pluginSourceDir;

    /**
     * 子容器管理器
     */
    private final SubContainersManager subContainersManager;

    /**
     * 插件编译器
     */
    private final PluginCompiler pluginCompiler;

    /**
     * 构造器
     * @param subContainersManager 子容器管理器
     * @param pluginCompiler 插件编译器
     */
    public PluginSourceListener(SubContainersManager subContainersManager, PluginCompiler pluginCompiler) {
        this.subContainersManager = subContainersManager;
        this.pluginCompiler = pluginCompiler;
    }

    /**
     * 源码监听器<br/>
     * 创建一个源码监听线程，用来在运行时监听插件源码目录的变化
     */
    @PostConstruct
    public void listeningSource() {
        // 指定监听目录
        File dir = new File(pluginSourceDir);

        // 创建监听线程
        new Thread(() -> {
            try {
                // 创建用于监听目录文件变化的对象（Java NIO自带的，头一次见）
                WatchService watchService = FileSystems.getDefault().newWatchService();
                // 监听指定目录的一种事件
                dir.toPath().register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE);
                log.info("__________开始监听插件源码目录：{}", dir.getAbsolutePath());

                // 掐个死循环
                while (true) {
                    // 持续获取监听对象是否被触发的标记
                    WatchKey key = watchService.take();

                    // 遍历触发了监听事件的事件列表
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // 获取事件类型与触发的目录
                        WatchEvent.Kind<?> kind = event.kind();
                        Path fileName = (Path) event.context();

                        // 若目录出现变化（有新东西）就注册里面的Bean（如果有）
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            File newPluginDir = new File(dir, fileName.toString());
                            if (newPluginDir.isDirectory()) {
                                registerPluginBeans(newPluginDir);
                            }
                        // 插件拔掉了就卸载对应的子容器
                        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            subContainersManager.unloadSubContainer(fileName.toString());
                        }
                    }

                    // 重置事件触发标记
                    key.reset();
                }

            } catch (Exception e) {
                log.error("__________插件源码目录监听启动失败", e);
            }
        }, "Source-Listening").start();
    }

    /**
     * 注册插件中的豆子<br/>
     * 会试图把所有的java做成Bean
     * @param pluginDir 存在需要注册Bean的目录
     */
    private void registerPluginBeans(File pluginDir) {
        try {
            // 根据给定的地址，试图将这里面的所有玩意转成class对象
            List<Class<?>> classes = pluginCompiler.sourceHotCompiler(pluginDir);
            log.info("__________插件{}热编译完成", pluginDir.getName());
            // 使用动态豆子注册器注册这些所有class对象为Bean至主容器
            try {
                log.info("__________准备创建子容器并注册插件");
                subContainersManager.mountSubContainer(pluginDir.getName(), classes, pluginDir);
            } catch (MalformedURLException e) {
                log.error("__________子容器创建失败：{}", pluginDir.getName(), e);
            }
            log.info("__________子容器创建成功：{}", pluginDir.getName());
        } catch (Exception e) {
            log.error("__________子容器创建失败：{}", pluginDir.getName(), e);
        }
    }
}
