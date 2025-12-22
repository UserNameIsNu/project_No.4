/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import com.ljf.greatplan.general.listener.fileSystemListener.FileSystemListener;
import com.ljf.greatplan.general.listener.pluginsListener.PluginSourceListener;
import com.ljf.greatplan.general.tools.pluginsTools.SubContainersManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 监听单元对象<br/>
 * 定义了监听器结构。
 * 每个路径段理应都会被分配一个单独的监听单元进行事件监听。
 */
@Component
@Slf4j
// 每次获取这个豆子时都给个新的
@Scope("prototype")
public class ListeningUnit extends Thread{
    /**
     * 路径段
     */
    private List<String> pathSegment;

    /**
     * 单元工作状态
     */
    private boolean running = true;

    /**
     * 监听服务
     */
    private WatchService watchService;

    /**
     * 文件系统监听器
     */
    private FileSystemListener fileSystemListener;

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
     * 插件（源码）监听器
     */
    private final PluginSourceListener pluginSourceListener;

    /**
     * 构造器
     * @param fileSystemListener 文件系统监听器
     */
    @Autowired
    public ListeningUnit(FileSystemListener fileSystemListener, SubContainersManager subContainersManager, PluginSourceListener pluginSourceListener) {
        this.fileSystemListener = fileSystemListener;
        this.subContainersManager = subContainersManager;
        this.pluginSourceListener = pluginSourceListener;
    }

    /**
     * 设置路径段
     * @param pathSegment 路径段
     */
    public void setPathSegment(List<String> pathSegment) {
        this.pathSegment = pathSegment;
    }

    /**
     * 停止单元
     */
    public void stopListening() {
        // 修改单元工作状态
        running = false;
        try {
            // 若存在服务
            if (watchService != null) {
                // 关闭服务
                watchService.close();
            }
        } catch (IOException e) {
            log.error("__________监听单元关闭失败：{}", pathSegment, e);
        }
    }

    /**
     * 路径段监听器<br/>
     * 继承了Thread的方法，不能改名字了:(
     * 定义了每个路径段通用的事件监听器结构与行为。
     * 指定注册监听给定路径段的范围。
     */
    @Override
    public void run() {
        try {
            // 创建用于监听目录文件变化的对象
            this.watchService = FileSystems.getDefault().newWatchService();
            try {
                // 内监听组，收录路径段内每个目录
                Map<WatchKey, Path> keyMap = new HashMap<>();
                // 注册路径段内的每个目录
                for (String dirPath : pathSegment) {
                    Path path = Paths.get(dirPath);
                    // 若为目录
                    if (Files.isDirectory(path)) {
                        // 绑定监听事件（创建/删除/修改）
                        WatchKey key = path.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                        keyMap.put(key, path);
                    }

                    // 若为AI插件的补充工具方法目录
                    if(path.toAbsolutePath().toString().equals("D:\\planOfElectronicHamster\\9_GreatPlan\\4_第四次尝试（进行中）\\greatPlan\\src\\main\\java\\com\\ljf\\greatplan\\docking\\plugins\\aiSecretary\\toolMethodFromAI")) {
                        // 强行卸载插件
                        subContainersManager.unloadSubContainer("aiSecretary");
                        // 创建路径对象
                        File dir = new File(pluginSourceDir);
                        File newPluginDir = new File(dir, "aiSecretary");
                        // 重编译，强制刷新，让新加入的工具类也能被编译进来
                        // 现在缺少专门面向单个文件的热编译逻辑，只能复用全量热编译
                        // 毕设要截至了，一个丑陋的补丁
                        // 史山就是这么堆出来的
                        pluginSourceListener.registerPluginBeans(newPluginDir);
                    }
                }

                // 掐个死循环
                while (running) {
                    try {
                        // 设置触发标记
                        WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                        if (key == null) continue;
                        // 触发事件
                        // 做个小判断，人机watchService在事件处理完之前会往死里喷“触发了事件”提示
                        // 所以卡一下，只让它喷一次
                        boolean rebuildScheduled = false;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (!rebuildScheduled) {
                                log.info("__________监听到一次事件发生");
                                // 重建监听组
                                fileSystemListener.requestRebuild(getName());
                                // 不要再喷提示啦！
                                rebuildScheduled = true;
                            }
                        }
                        // 重置触发标记
                        key.reset();
                    } catch (ClosedWatchServiceException e) {
                        if (running) {
                            log.error("__________监听单元异常关闭：{}", pathSegment, e);
                        } else {
                            log.info("__________监听单元正常关闭：{}", pathSegment);
                        }
                        break;
                    } catch (InterruptedException e) {
                        log.error("__________监听单元异常中断：{}", pathSegment, e);
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("__________监听单元发生未知异常：{}", pathSegment, e);
            } finally {
                // 若不管啥原因（目前只有如单元正在处理时，有家伙申请了监听组重建），这个单元在处理完一次，或没处理完事件后莫名被关闭了
                // 那么就直接销毁得了
                if (this.watchService != null) {
                    this.watchService.close();
                }
            }
        } catch (IOException e) {
            log.error("__________监听单元的依赖WatchService发生未知异常：{}", pathSegment, e);
        }
    }
}
