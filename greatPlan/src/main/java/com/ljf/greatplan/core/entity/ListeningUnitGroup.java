/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听单元组对象<br/>
 * 用于管理所有被创建的监听单元。
 */
@Component
@Getter
public class ListeningUnitGroup {
    /**
     * 监听单元组<br/>
     * <路径段ID，管理这个路径段的监听单元>
     */
    private Map<String, ListeningUnit> listeningUnits = new HashMap<>();

    /**
     * 添加监听单元
     * @param pathSegmentId 监听单元监听的路径段id
     * @param listeningUnit 监听单元
     */
    public void addListeningUnit(String pathSegmentId, ListeningUnit listeningUnit) {
        listeningUnits.put(pathSegmentId, listeningUnit);
    }

    /**
     * 清空监听单元组<br/>
     * 暂停活动再清空组。
     */
    public void delListeningUnitGroup() {
        for (ListeningUnit unit : listeningUnits.values()) {
            // 停止单元
            unit.stopListening();
            // 重置监听单元组
            listeningUnits = new HashMap<>();
        }
    }
}
