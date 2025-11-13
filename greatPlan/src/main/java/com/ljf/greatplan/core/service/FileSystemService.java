/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.service;

import com.ljf.greatplan.core.entity.Node;
import com.ljf.greatplan.core.entity.NodeTree;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 文件系统服务器<br/>
 * 用于核心包的，对文件系统进行操作或构建的一些功能。
 */
@Service
public class FileSystemService {
    /**
     * 节点树
     */
    private NodeTree nodeTree;

    /**
     * 构造器
     * @param nodeTree 节点树
     */
    public FileSystemService(NodeTree nodeTree) {
        this.nodeTree = nodeTree;
    }

    /**
     * 热度增长<br/>
     * 被用户的点击节点行为触发，用于将被点击的节点的点击热度做增长。
     * @param nodeId 节点ID
     */
    public void popularityIncreases(String nodeId) {
        nodeTree.popularityIncreases(nodeId);
    }

    /**
     * 获取树
     * @return 节点树
     */
    public Map<String, Node> getTree() {
        return nodeTree.getTree();
    }
}
