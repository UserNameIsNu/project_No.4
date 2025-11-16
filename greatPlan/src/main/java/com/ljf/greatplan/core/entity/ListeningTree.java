/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import com.ljf.greatplan.core.enums.NodeType;
import com.ljf.greatplan.general.tools.generalTools.FileIO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听树对象<br/>
 * 代表所有路径段的集合状态，管理所有路径段。
 * 树为单例树，让容器代管。
 * 路径段代表某段路径（内包含任意数量或层级的其它文件或目录）。
 * 路径段中的成员是由有序，且必须存在直系关系的路径组成。
 * 路径段用于方便监听行为。
 * 直接监听整个文件系统太吓人了，完全跟随节点分别创建监听也受不了。
 * 所以引入监听树，管理监听段，尽可能组合相连的节点做监听，避免扫盘，也避免线程多的要死。
 */
@Component
@Getter
@Slf4j
public class ListeningTree {
    /**
     * 监听树（路径段最上级地址与最下级地址的哈希拼接出来的ID， 路径段组）
     */
    private Map<String, List<String>> tree = new ConcurrentHashMap<>();

    /**
     * 节点树对象
     */
    private NodeTree nodeTree;

    /**
     * 文件IO工具类
     */
    private FileIO fileIO;

    /**
     * 构造器
     * @param nodeTree 节点树
     * @param fileIO 文件IO工具类
     */
    public ListeningTree(NodeTree nodeTree, FileIO fileIO) {
        this.nodeTree = nodeTree;
        this.fileIO = fileIO;
    }

    /**
     * 根遍历<br/>
     * 用于从每个根节点开始，遍历它们的所有下级节点.
     * 这里也是重建监听树的起始点。
     * 这里会从根节点开始扫节点树进行重建的。
     */
    public void rootTraversal() {
        // 路径段组
        List<List<String>> pathSegmentsGroup = new ArrayList<>();

        // 取得根节点
        List<String> roots = nodeTree.getRootNode();
        // 遍历以切割树枝
        for (String root : roots) {
            // 把这个根节点拉出来先
            Node rootNode = nodeTree.getNodeById(fileIO.generateId(new File(root)));
            // 下探
            branchCut(pathSegmentsGroup, new ArrayList<>(), rootNode);
        }

        // 装填路径段组
        for (List<String> pathSegments : pathSegmentsGroup) {
            // 起始点与结束点拼接做哈希，当作key
            tree.put(getPathSegmentId(pathSegments), pathSegments);
        }

        log.info("__________路径段截取完成，监听树已建立");
    }

    /**
     * 分支切割<br/>
     * 用于裁切节点树中的节点。
     * 从根节点开始，向下递归。
     * 遇到分叉则截断，每个分叉独立继续向下递归。
     * 保证路径段为单链。
     * 路径段只塞起始点路径和结束点路径两个就行
     * @param pathSegmentsGroup 结果的路径段组
     * @param pathSegments 当前正在构建的路径段
     * @param node 指向节点
     */
    public void branchCut(List<List<String>> pathSegmentsGroup, List<String> pathSegments, Node node) {
        // 确保即将处理的玩意不是空的
        if (node == null) {
            return;
        }

        // 先把自己塞进去当路径段起始点
        pathSegments.add(node.getPath());
        // 获取它的子节点集
        List<String> childNodesId = node.getChildNode();

        // 检查一下有没有子节点
        if (childNodesId == null) {
            // 没孩子，把这一个塞进去就行，结束了
            pathSegmentsGroup.add(pathSegments);
            return;
        }

        // 收录目录类型子节点
        List<Node> dirNode = new ArrayList<>();
        for (String id : childNodesId) {
            if (nodeTree.getNodeById(id).getNodeType() == NodeType.DIRECTORY) {
                dirNode.add(nodeTree.getNodeById(id));
            }
        }

        // 若没有目录类型子节点
        if (dirNode.isEmpty()) {
            // 也是把自己塞进去就行了，结束了
            pathSegmentsGroup.add(pathSegments);
            return;
        }

        // 检查一下目录类型子节点数量是不是1个
        if (dirNode.size() == 1) {
            // 目录类型子节点只有一个
            // 加入中间段
            pathSegments.add(dirNode.getFirst().getPath());
            // 继续向下
            branchCut(pathSegmentsGroup, pathSegments, dirNode.getFirst());
        } else {
            // 超限，切割分支，结束了
            pathSegmentsGroup.add(pathSegments);
            // 拉出子节点集，并独立递归，开始做新的路径段
            for (Node n : dirNode) {
                branchCut(pathSegmentsGroup, new ArrayList<>(List.of(node.getPath())), n);
            }
        }
    }

    /**
     * 重置树<br/>
     * 重置监听树，把里面的路径段全部删掉
     */
    public void resetTree() {
        tree = new ConcurrentHashMap<>();
        log.info("__________监听树已重置");
    }

    /**
     * 获取指定路径段的id
     * @param pathSegment 路径段
     * @return 路径段的id
     */
    public String getPathSegmentId(List<String> pathSegment) {
        return fileIO.generateId(new File(pathSegment.getFirst() + pathSegment.getLast()));
    }
}
