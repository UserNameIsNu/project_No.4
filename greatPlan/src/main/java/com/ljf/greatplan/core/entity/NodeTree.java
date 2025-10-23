/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 节点树对象<br/>
 * 代表所有节点的集合状态，管理所有节点。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeTree {
    /**
     * 节点树（节点ID（文件哈希）， 节点对象）
     */
    private Map<String, Node> tree;
}
