/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.listener.pluginsListener;

import com.ljf.greatplan.core.service.PluginService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 插件（资源）监听器<br/>
 * 监听插件资源放置目录，引发装载与卸载行为
 */
@Component
@Slf4j
public class PluginResourceListener {
    /**
     * 插件资源地址
     */
    @Value("${great-plan.plugin.resource-dir}")
    private String pluginSResourceDir;

    /**
     * 插件管理器
     */
    private final PluginService pluginService;

    /**
     * 构造器
     * @param pluginService 插件服务器
     */
    public PluginResourceListener(PluginService pluginService) {
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

        try {
            // 创建文件观察者（会递归监听子目录）
            FileAlterationObserver observer = new FileAlterationObserver(dir);

            // 监听器：响应文件创建、删除、修改事件
            observer.addListener(new FileAlterationListenerAdaptor() {
                @Override
                public void onFileCreate(File file) {
                    log.info("__________检测到插件新增：{}", file.getName());
                    safeRescan();
                }

                @Override
                public void onFileDelete(File file) {
                    log.info("__________检测到插件删除：{}", file.getName());
                    pluginService.removePlugin(file.getName());
                    safeRescan();
                }

                @Override
                public void onFileChange(File file) {
                    log.info("__________检测到插件修改：{}", file.getName());
                    safeRescan();
                }

                private void safeRescan() {
                    try {
                        Thread.sleep(1000); // 防止文件写入未完成
                        pluginService.pluginsScanner();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("__________插件重新扫描失败", e);
                    }
                }
            });

            // 0.5秒刷新一次
            long interval = 500L;
            FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);

            // 启动独立线程
            new Thread(() -> {
                try {
                    monitor.start();
                    log.info("__________开始监听插件资源目录：{}", dir.getAbsolutePath());
                } catch (Exception e) {
                    log.error("__________插件资源目录监听启动失败", e);
                }
            }, "Plugin-Resource-Monitor").start();

        } catch (Exception e) {
            log.error("__________插件资源目录监听初始化失败", e);
        }
    }
}
