/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.listener.fileSystemListener;

import com.ljf.greatplan.core.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件系统监听器<br/>
 * 监听整个文件系统，反应文件或目录的增删改事件。
 */
@Component
@Slf4j
public class FileSystemListener {
    /**
     * 监听树
     */
    private ListeningTree listeningTree;

    /**
     * 监听单元组
     */
    private ListeningUnitGroup listeningUnitGroup;

    /**
     * 上下文对象（取豆子的）
     */
    @Autowired
    private ApplicationContext context;

    /**
     * 上次重建时间（防抖，避免短时多次重建搞得炸机）
     */
    private final AtomicLong lastRebuildTime = new AtomicLong(0);

    /**
     * 防抖间隔（最少需要间隔多久才能触发一次监听组重建行为）
     */
    @Value("${great-plan.file-system.listener.anti-shake-intervals}")
    private Integer antiShakeIntervals;

    /**
     * 构造器
     * @param listeningTree 监听树对象
     * @param listeningUnitGroup 监听单元组对象
     */
    public FileSystemListener(ListeningTree listeningTree, ListeningUnitGroup listeningUnitGroup) {
        this.listeningTree = listeningTree;
        this.listeningUnitGroup = listeningUnitGroup;
    }

    /**
     * 构建监听组<br/>
     * 给每个路径段创建一个独立监听单元。
     * 收录到监听单元组后直接启动监听任务。
     */
    public void buildListeningGroup() {
        log.info("__________开始构建监听组");

        // 从监听树获取所有路径段
        List<List<String>> pathSegments = new ArrayList<>(listeningTree.getTree().values());
        // 遍历路径段组，给每个路径段都挂上一个监听单元进行事件监听
        for (List<String> segment : pathSegments) {
            // 新建监听单元
            ListeningUnit unit = context.getBean(ListeningUnit.class);
            // 负责的路径段
            unit.setPathSegment(segment);
            // 线程名
            unit.setName("ListeningUnit-" + segment.get(segment.size() - 1));
            // 加入监听单元组
            listeningUnitGroup.addListeningUnit(listeningTree.getPathSegmentId(segment), unit);
            // 启动
            unit.start();
            log.info("__________监听单元启动，指向{}路径段", segment);
        }

        log.info("__________监听组构建完成，组内共{}个单元", pathSegments.size());
    }

    /**
     * 重建监听组<br/>
     * 暂停所有监听单元的活动，删除所有监听单元，重建监听树，根据当前监听树保存的路径段重新构建监听组。
     * 打上锁，虽然已经有防抖筛了一次。
     * 毕竟任何监听单元都有权利申请重建，监听单元理论又是无限多的。
     * 只能是防一手了。
     */
    private synchronized void rebuildListeningGroup() {
        // 清空监听单元组
        listeningUnitGroup.delListeningUnitGroup();
        // 清空监听树
        listeningTree.resetTree();
        // 重建监听树
        listeningTree.rootTraversal();
        // 重建监听单元组
        buildListeningGroup();
    }

    /**
     * 请求重建<br/>
     * 不允许外部直接调用重建监听组的逻辑。
     * 由这个单一入口卡住，避免如多个监听单元同时检测到事件，同时申请重建监听组。
     */
    public synchronized void requestRebuild(String triggerName) {
        // 当前时间
        long now = System.currentTimeMillis();
        // 若相比上次重建时间的间隔小于1秒，就退出去
        // 不许重建！
        if (now - lastRebuildTime.get() < antiShakeIntervals) return;
        // 重新设置时间
        lastRebuildTime.set(now);
        // 重建（异步执行重建，免得和监听单元尤其和主程序流打架）
        log.info("__________收到监听组重建请求");
        log.info("__________请求申请者：{}", triggerName + "，时间: " + now);
        CompletableFuture.runAsync(this::rebuildListeningGroup);
    }
}
