/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.listener.pluginsListener;

/**
 * 插件监听器（弃用）<br/>
 * 用运行时监听插件目录，引发装载或卸载行为
 * <br/>——————<br/>
 * V0.6.14.7中弃用，并被{@link PluginResourceListener}和{@link PluginSourceListener}代替。
 */
@Deprecated
//@Component
//@Slf4j
public class PluginWatcher {
    /**
     * 插件资源地址
     */
//    @Value("${plugin.root-dir}")
//    private String pluginRootDir;

    /**
     * 插件管理器
     */
//    private final PluginService pluginService;

    /**
     * 构造器
     * @param pluginService
     */
//    public PluginWatcher(PluginService pluginService) {
//        this.pluginService = pluginService;
//    }

    /**
     * 监听线程<br/>
     * 创建一个监听线程，用来在运行时监听插件目录的变化，且做个异步就不用和核心逻辑抢位置
     */
//    @PostConstruct
//    public void startWatching() {
//        // 指定监听目录
//        File dir = new File(pluginRootDir);
//
//        // 扫描插件目录，注册装载存在的插件
//        pluginService.scanPlugins();
//
//        // 创建监听线程，不要和核心包的主逻辑抢位置
//        new Thread(() -> {
//            try {
//                // 创建用于监听目录文件变化的对象（Java NIO自带的，头一次见）
//                WatchService watchService = FileSystems.getDefault().newWatchService();
//                // 监听指定目录的两种事件（子文件或目录被创建，子文件或目录被删除）
//                dir.toPath().register(watchService,
//                        StandardWatchEventKinds.ENTRY_CREATE,
//                        StandardWatchEventKinds.ENTRY_DELETE);
//                log.info("开始监听：{}", dir.getAbsolutePath());
//
//                // 掐个死循环
//                while (true) {
//                    // 持续获取监听对象是否被触发的标记
//                    WatchKey key = watchService.take();
//                    // 是否需要重扫插件
//                    boolean changed = false;
//
//                    // 遍历触发了监听事件的事件列表
//                    for (WatchEvent<?> event : key.pollEvents()) {
//                        // 获取事件类型与触发的目录
//                        WatchEvent.Kind<?> kind = event.kind();
//                        Path fileName = (Path) event.context();
//
//                        // 若目录出现变化（有新东西或没了啥东西）就标记需要重扫目录
//                        if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
//                                kind == StandardWatchEventKinds.ENTRY_DELETE) {
//                            changed = true;
//                            // 若出现了删除事件，就卸载这个被删除的插件
//                            if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
//                                pluginService.removePlugin(fileName.toString());
//                            }
//                        }
//                    }
//
//                    // 检查重扫标记
//                    if (changed) {
//                        // 若需要重扫，等待1s再扫（太急了怕炸）
//                        Thread.sleep(1000);
//                        // 调用插件扫描器，重新扫描注册并复制插件资源
//                        pluginService.scanPlugins();
//                    }
//
//                    // 重置事件触发标记
//                    key.reset();
//                }
//
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//            // 定义线程名，并启动
//        }, "Plugin-Watcher").start();
//    }
}
