package com.ljf.greatplan.docking.listener;

import com.ljf.greatplan.config.DynamicBeanRegistrar;
import com.ljf.greatplan.util.PluginCompiler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.List;

/**
 * 插件（源码）监听器<br/>
 * 监听插件源码放置目录，引发装载行为
 */
@Component
@Slf4j
public class PluginSourceListening {
    /**
     * 插件源码地址
     */
    @Value("${plugin.source-dir}")
    private String pluginSourceDir;

    /**
     * 动态豆子注册器
     */
    private final DynamicBeanRegistrar dynamicBeanRegistrar;

    /**
     * 插件编译器
     */
    private final PluginCompiler pluginCompiler;

    /**
     * 构造器
     * @param dynamicBeanRegistrar
     */
    public PluginSourceListening(DynamicBeanRegistrar dynamicBeanRegistrar, PluginCompiler pluginCompiler) {
        this.dynamicBeanRegistrar = dynamicBeanRegistrar;
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
                        StandardWatchEventKinds.ENTRY_CREATE);
                log.info("开始监听源码目录：{}", dir.getAbsolutePath());

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
                        }
                    }

                    // 重置事件触发标记
                    key.reset();
                }

            } catch (Exception e) {
                log.error("插件源码监听出错", e);
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
            log.info("准备热编译并注册插件：{}", pluginDir.getName());
            // 根据给定的地址，试图将这里面的所有玩意转成class对象
            List<Class<?>> classes = pluginCompiler.sourceHotCompiler(pluginDir);
            // 使用动态豆子注册器注册这些所有class对象为Bean至主容器
            for (Class<?> clazz : classes) {
                dynamicBeanRegistrar.registerBeanDynamically(clazz);
                log.info("✅ 成功注册插件 Bean：{}", clazz.getName());
            }
        } catch (Exception e) {
            log.error("❌ 插件注册失败：{}", pluginDir.getName(), e);
        }
    }
}
