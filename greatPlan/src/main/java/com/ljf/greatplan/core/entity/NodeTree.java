/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点树对象<br/>
 * 代表所有节点的集合状态，管理所有节点。
 * 树为单例树，让容器代管。
 */
@Component
@NoArgsConstructor
@Getter
public class NodeTree {
    /**
     * 节点树（节点ID（文件哈希）， 节点对象）
     */
    private final Map<String, Node> tree = new ConcurrentHashMap<>();

    /**
     * 添加节点
     * @param node 新节点对象
     */
    public void addNode(Node node) {
        tree.put(node.getId(), node);
    }

    /**
     * 添加一组节点（拼接另一棵树）
     * @param nodes 树形式的一组节点
     */
    public void addNodes(Map<String, Node> nodes) {
        tree.putAll(nodes);
    }

    /**
     * 删除节点
     * @param node 要死的节点对象
     */
    public void delNode(Node node) {
        // 看看这个节点是否有父节点
        String parentNodeId = node.getParentNode();
        if (parentNodeId != null) {
            // 若有，要在父节点的子节点集中也删除这个节点
            // 取得父节点
            Node parentNode = getNodeById(parentNodeId);
            // 在父节点的子节点集中删除这个节点
            parentNode.getChildNode().remove(node.getId());
            parentNode.setChildNode(parentNode.getChildNode());
        }
        // 最后删除自己在节点树中的存在
        tree.remove(node.getId());
    }

    /**
     * 按节点id获取节点对象
     * @param id 节点id
     * @return 节点对象
     */
    public Node getNodeById(String id) {
        return tree.get(id);
    }

    /**
     * 热度增长
     * @param nodeId 目标节点Id
     */
    public void popularityIncreases(String nodeId) {
        tree.get(nodeId).setClickHeat(tree.get(nodeId).getClickHeat() + 1);
    }

    /**
     * 获取末节点集合<br/>
     * 没有子节点集合的就是末节点。
     * @return 末节点集合
     */
    public List<Node> getEndNodes() {
        List<Node> nodes = new ArrayList<>();
        // 遍历所有节点
        for (Node node : tree.values()) {
            // 若这个节点没有子节点就加入末节点集合
            if (node.getChildNode().isEmpty()) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 获取根节点集合<br/>
     * 没有父节点的就是根节点。
     * @return 根节点结合
     */
    public List<String> getRootNode() {
        List<String> nodes = new ArrayList<>();
        // 遍历所有节点
        for (Node node : tree.values()) {
            // 若这个节点没有子节点就加入末节点集合
            if (node.getParentNode() == null) {
                nodes.add(node.getPath());
            }
        }
        return nodes;
    }
}
