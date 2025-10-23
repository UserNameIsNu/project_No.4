/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import com.ljf.greatplan.core.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点对象<br/>
 * 代表树中的任意一个节点。
 * 因为文件扫描与索引采用树状结构，文件与目录均抽象表示为节点。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Node {
    /**
     * 节点ID（使用文件哈希）
     */
    private String id;

    /**
     * 节点名（若是文件则不包括后缀）
     */
    private String name;

    /**
     * 节点类型（使用节点类型枚举）
     */
    private NodeType nodeType;

    /**
     * 节点路径
     */
    private String path;

    /**
     * 父节点
     */
    private String parentNode;

    /**
     * 子节点集
     */
    private List<String> childNode;
}
