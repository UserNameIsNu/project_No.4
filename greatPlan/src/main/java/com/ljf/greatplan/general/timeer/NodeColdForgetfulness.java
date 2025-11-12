/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.timeer;

import com.ljf.greatplan.core.entity.Node;
import com.ljf.greatplan.core.entity.NodeTree;
import com.ljf.greatplan.core.enums.NodeType;
import com.ljf.greatplan.general.listener.fileSystemListener.FileSystemListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点冷遗忘<br/>
 * 定时对树中的节点进行热度衰减，并同时进行冷节点裁剪。
 * 避免节点树过于庞大搞得哪里受不了。
 */
@Slf4j
@Component
public class NodeColdForgetfulness {
    /**
     * 节点树
     */
    private final NodeTree nodeTree;

    /**
     * 文件系统监听器
     */
    private FileSystemListener fileSystemListener;

    /**
     * 衰减量（每刻度衰减的热度）
     */
    @Value("${node-attenuation.num}")
    private Integer num;

    /**
     * 起始阈值（节点树中的节点数量超过这个值才会开始衰减）
     */
    @Value("${node-attenuation.starter-threshold}")
    private Integer starterThreshold;

    /**
     * 构造器
     * @param nodeTree
     * @param fileSystemListener
     */
    public NodeColdForgetfulness(NodeTree nodeTree, FileSystemListener fileSystemListener) {
        this.nodeTree = nodeTree;
        this.fileSystemListener = fileSystemListener;
    }

    /**
     * 定时任务（fixedRateString = 每隔多少毫秒执行一次）
     */
    @Scheduled(fixedRateString = "${node-attenuation.speed}")
    public void decayAndPrune() {
        System.out.println(nodeTree.getTree());
        // 阈值判断
        if (nodeTree.getTree().size() > starterThreshold) {
            // 衰减
            attenuation();
            // 清理
            clean();
        }
    }

    /**
     * 衰减<br/>
     * 遍历树的所有节点，对它们的点击热度进行衰减行为。
     */
    private void attenuation() {
        // 遍历树
        for (Node node : nodeTree.getTree().values()) {
            // 计算衰减后热度（不要变成负数）
            int newHeat = node.getClickHeat() - num;
            // 重赋值
            node.setClickHeat(Math.max(0, newHeat));
        }
    }

    /**
     * 清理<br/>
     * 遍历树中所有末节点，若发现热度归零的末节点则开始上追。
     * 上追会持续，直到发现热度不为零的节点，或根节点。
     * 若上追出现了目录节点，那么这个目录节点内的所有子节点也需要遍历检查热度，确保只有在全部子节点热度均归零才会继续上追。
     * 上追结束后会将所有结果枝条从树中剪裁删除，以实现冷遗忘。
     */
    public void clean() {
        // 获取末节点集合
        List<Node> nodes = nodeTree.getEndNodes();
        // 预备删除节点集合
        List<Node> delNodes = new ArrayList<>();

        // 遍历末节点集，开始逐个上追
        for (Node node: nodes) {
            // 若存在需要删除的，加入预备删除节点集合
            delNodes.addAll(tracedBack(node));
        }

        // 删除预备删除节点集合中的所有节点
        for (Node node : delNodes) {
            nodeTree.delNode(node);
        }

        // 若确实删了点啥，那就需要重建监听了
        if (!delNodes.isEmpty()) {
            fileSystemListener.requestRebuild("冷遗忘点");
        }
    }

    /**
     * 上追<br/>
     * 用于递归上追，直到根目录或出现热度未归零的节点时退出。
     * @param node 当前指向的节点
     */
    public List<Node> tracedBack(Node node) {
        // 预备删除节点集合
        List<Node> delNodes = new ArrayList<>();
        // 父节点ID
        String parentNodeId = node.getParentNode();

        // 检查有没有父节点（是否为根节点）
        if (parentNodeId == null) {
            // 没有
            // 退出递归
            return delNodes;
        } else {
            // 有
            // 热度标记
            boolean isHot = false;
            // 检查兄弟节点热度是否也全部归零
            // 先取这个末节点的父节点
            Node parentNode = nodeTree.getNodeById(parentNodeId);
            // 遍历这个节点的父节点的子节点集（我的爸爸的孩子们，就是我的兄弟们）
            for (String sonId : parentNode.getChildNode()) {
                // 检查所有子节点的热度，只要有一个子节点热度没有归零都不能删
                if (nodeTree.getNodeById(sonId).getClickHeat() > 0) {
                    isHot = true;
                    break;
                }
            }
            // 判断兄弟节点热度是否全部归零
            if (isHot) {
                // 没有全部归零
                // 退出递归
                return delNodes;
            } else {
                // 全部归零
                // 反向下探递归这些兄弟节点，保证所有兄弟节点可能存在的子节点的热度全部归零
                // 若上层节点被删除时，下层节点热度还没归零，就会导致没有归零的下层节点被连带删除，因为这是枝裁剪，不是指定节点裁剪
                // 所以要再套一层反向的下探逻辑，保证整根枝条确实是可以裁剪的
                boolean isNotZero = true;
                for (String sonId : parentNode.getChildNode()) {
                    // 只要有一个子树没有归零，就整体不能删
                    if (!excavate(nodeTree.getNodeById(sonId))) {
                        isNotZero = false;
                        break;
                    }
                }
                // 若确保了下层节点没有问题
                // 那么这个节点与所有兄弟节点全部加入预备集合
                if (isNotZero) {
                    for (String sonId : parentNode.getChildNode()) {
                        delNodes.add(nodeTree.getNodeById(sonId));
                    }
                    // 继续上追，结果与前面的合并保存
                    delNodes.addAll(tracedBack(parentNode));
                    // 返回最终的需要删除的节点集合
                    return delNodes;
                } else {
                    // 否则退出递归
                    return delNodes;
                }
            }
        }
    }

    /**
     * 下探<br/>
     * 用于递归下探，在上追时保证指向节点的所有下层节点可能存在的子节点都是可以被删除的状态。
     * 若上层节点被删除时，下层节点热度还没归零，就会导致没有归零的下层节点被连带删除，因为这是枝裁剪，不是指定节点裁剪。
     * 所以要再套一层反向的下探逻辑，保证整根枝条确实是可以裁剪的。
     * @return 所有节点热度是否全部归零，未全部归零返回false
     */
    public boolean excavate(Node node) {
        // 下探标记
        boolean flag = true;

        // 是否为文件节点
        if (node.getNodeType() == NodeType.File) {
            // 检查热度
            if (node.getClickHeat() > 0) {
                // 存在热度
                flag = false;
            }
            // 没有热度，直接滚到最下面了
        } else {
            // 不是文件节点，而是目录节点，那么就可能存在子节点
            // 获取子节点集
            List<String> nodesId = node.getChildNode();
            // 若没有子节点集，直接退出（自己也没热度，子节点也没有，可以安全删除啦）
            // 有就只能继续探了
            if (nodesId != null) {
                // 获取子节点集的实际节点对象
                List<Node> nodes = new ArrayList<>();
                for (String n : nodesId) {
                    nodes.add(nodeTree.getNodeById(n));
                }
                // 遍历子节点集
                for (Node n : nodes) {
                    // 检查热度
                    if (n.getClickHeat() > 0) {
                        // 存在热度（哪怕只有一个节点存在热度，整条枝就不能裁剪），直接退出
                        flag =  false;
                    } else {
                        // 没有热度，那就继续递归下探
                        // 若下层有热度，那就盖上来，不许删
                        flag = flag && excavate(n);
                    }
                }
            }
        }

        return flag;
    }
}
