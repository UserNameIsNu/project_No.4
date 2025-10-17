/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.listener.plugins;

import com.ljf.greatplan.core.service.PluginService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.*;

/**
 * 插件（资源）监听器<br/>
 * 监听插件资源放置目录，引发装载与卸载行为
 */
@Component
@Slf4j
public class PluginResourceListening {
    /**
     * 插件资源地址
     */
    @Value("${plugin.resource-dir}")
    private String pluginSResourceDir;

    /**
     * 插件管理器
     */
    private final PluginService pluginService;

    /**
     * 构造器
     * @param pluginService
     */
    public PluginResourceListening(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    /**
     * 资源监听器<br/>
     * 创建一个资源监听线程，用来在运行时监听插件资源目录的变化
     */
    @PostConstruct
    public void listeningResource() {
        // 指定监听目录
        File dir = new File(pluginSResourceDir);

        // 扫描插件目录，注册装载存在的插件
        pluginService.pluginsScanner();

        // 创建监听线程
        new Thread(() -> {
            try {
                // 创建用于监听目录文件变化的对象（Java NIO自带的，头一次见）
                WatchService watchService = FileSystems.getDefault().newWatchService();
                // 监听指定目录的两种事件
                dir.toPath().register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE);
                log.info("开始监听资源目录：{}", dir.getAbsolutePath());

                // 掐个死循环
                while (true) {
                    // 持续获取监听对象是否被触发的标记
                    WatchKey key = watchService.take();
                    // 是否需要重扫插件
                    boolean changed = false;

                    // 遍历触发了监听事件的事件列表
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // 获取事件类型与触发的目录
                        WatchEvent.Kind<?> kind = event.kind();
                        Path fileName = (Path) event.context();

                        // 若目录出现变化（有新东西或没了啥东西）就标记需要重扫目录
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                                kind == StandardWatchEventKinds.ENTRY_DELETE) {
                            changed = true;
                            // 若出现了删除事件，就卸载这个被删除的插件
                            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                pluginService.removePlugin(fileName.toString());
                            }
                        }
                    }

                    // 检查重扫标记
                    if (changed) {
                        // 若需要重扫，等待1s再扫（太急了怕炸）
                        Thread.sleep(1000);
                        // 调用插件扫描器，重新扫描注册
                        pluginService.pluginsScanner();
                    }

                    // 重置事件触发标记
                    key.reset();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            // 定义线程名，并启动
        }, "Resource-Listening").start();
    }
}
