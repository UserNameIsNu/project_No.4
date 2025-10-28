/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.scanner;

import com.ljf.greatplan.core.entity.DirectoryNode;
import com.ljf.greatplan.core.entity.FileNode;
import com.ljf.greatplan.core.entity.Node;
import com.ljf.greatplan.core.entity.NodeTree;
import com.ljf.greatplan.core.enums.NodeType;
import com.ljf.greatplan.core.enums.ScanStatus;
import com.ljf.greatplan.general.tools.generalTools.FileIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.DosFileAttributes;
import java.util.*;

/**
 * 指定目录扫描器<br/>
 * 用于扫描提供的指定目录，包括其中的任何文件或其它子目录。
 * 并将其全部封装为节点，且将进一步构建为节点树。
 * 扫描深度和扫描类型均由配置文件决定。
 * 强烈建议不要对超深目录进行全量扫描，现在的逻辑撑不住。
 */
@Component
public class SpecifyDirectoryScanner {
    /**
     * 文件IO工具类
     */
    private FileIO fileIO;

    /**
     * 扫描深度
     */
    @Value("${scan.depth}")
    private String maxDepth;

    /**
     * 扫描类型
     */
    @Value("${scan.show-type}")
    private String showType;

    /**
     * 构造器
     * @param fileIO
     */
    public SpecifyDirectoryScanner(FileIO fileIO) {
        this.fileIO = fileIO;
    }

    /**
     * 扫描一组目录<br/>
     * 扫描后把这组玩意塞进节点树里。
     * @param roots 目录集合
     * @return 节点树
     */
    public Map<String, Node> scanDirList(List<String> roots) {
        // 这里是初始化认盘，只要显示所有的盘符即可
        // 所以扫描深度0就行，不用下探
        String max = maxDepth;
        maxDepth = "0";
        Map<String, Node> nodeTree = new HashMap<>();
        for (String root : roots) {
            // 获取一个盘，拿到树
            Map<String, Node> tree = initialScanner(root).getTree();
            // 塞进树
            nodeTree.putAll(tree);
        }
        // 恢复扫描深度
        maxDepth = max;
        return nodeTree;
    }

    /**
     * 初始扫描器<br/>
     * 启动整个扫描任务的扫描器。
     * 由这玩意进行首次扫描，并调用深度扫描器按照扫描深度对目标目录进行递归下探。
     * 将命中的所有文件与目录打成节点，再打进节点树。
     * @param startPath 起始目录
     * @return 节点树
     */
    public NodeTree initialScanner(String startPath) {
        // 创建节点树骨架
        Map<String, Node> nodeMap = new HashMap<>();

        // 打开目录
        File startDir = new File(startPath);
        // 非空/是否为目录判断
        if (!startDir.exists() || !startDir.isDirectory()) {
            throw new IllegalArgumentException("指定的路径不存在或不是目录：" + startPath);
        }

        // 创建根节点（就是创建目录节点（根一定是目录的嘛））
        DirectoryNode rootNode = createDirectoryNode(startDir, null);
        // 加入节点树
        nodeMap.put(rootNode.getId(), rootNode);

        // 递归扫描这个目录
        depthScanner(startDir, rootNode, nodeMap, 0);

        // 返回最终的节点树
        return new NodeTree(nodeMap);
    }

    /**
     * 深度扫描器<br/>
     * 由初始扫描器触发。
     * 用于目录的递归下探，直到到达深度限制或递归极限。
     * 可以是目录探完的极限，也可以是目录太深搞得递归爆炸:)
     * @param dir 目标目录
     * @param parentNode 这个目录的父节点
     * @param nodeMap 节点树
     * @param currentDepth 当前已达深度
     */
    private void depthScanner(File dir,
                               DirectoryNode parentNode,
                               Map<String, Node> nodeMap,
                               int currentDepth) {
        // 是否超出扫描深度限制（全量扫描跳过）
        if (!maxDepth.equals("all")) {
            if (currentDepth >= Integer.parseInt(maxDepth)) {
                // 标记为部分扫描状态（因为到达深度限制，而不是扫到底）
                parentNode.setScanStatus(ScanStatus.PARTIAL_SCAN);
                // 退出扫描
                return;
            }
        }

        // 打开扫描目录
        File[] files = dir.listFiles();
        // 若是个空目录就直接返回
        if (files == null) return;
        // 子节点ID集
        List<String> childIds = new ArrayList<>();

        // 遍历当前目录
        for (File file : files) {
            // 获取Dos文件属性？？？
            // 为了知道文件是不是奇怪的隐藏文件就要用这玩意
            DosFileAttributes attrs;
            try {
                attrs = Files.readAttributes(file.toPath(), DosFileAttributes.class);
            } catch (IOException e) {
                // 如果文件被锁定，或是没有访问权限就跳过得了
                continue;
            }

            // 根据展示级别过滤
            // 最低级
            if (showType.equals("NORMAL")) {
                if (file.isHidden() || attrs.isSystem()) {
                    continue;
                }
            // 允许一般隐藏文件
            } else if (showType.equals("SHOW_HIDDEN")) {
                if (attrs.isSystem()) {
                    continue;
                }
            }
            // 全展示就不用过滤啦

            // 若命中目录
            if (file.isDirectory()) {
                // 把这个目录创建为目录类型节点
                DirectoryNode childDir = createDirectoryNode(file, parentNode.getId());
                // 加入节点树
                nodeMap.put(childDir.getId(), childDir);
                // 节点ID加入节点ID集
                childIds.add(childDir.getId());

                // 进行递归，继续探这个子目录
                depthScanner(file, childDir, nodeMap, currentDepth + 1);
            // 若命中文件
            } else {
                // 把这个目录创建为文件类型节点
                FileNode fileNode = createFileNode(file, parentNode.getId());
                // 加入节点树
                nodeMap.put(fileNode.getId(), fileNode);
                // 节点ID加入节点ID集
                childIds.add(fileNode.getId());
            }
        }

        // 给父节点（就是当前这个方法中命中的目录）设置子节点集
        parentNode.setChildNode(childIds);
        // 给父节点设置扫描状态为完全扫描，没有更深的目录或文件了
        parentNode.setScanStatus(ScanStatus.FULLY_SCANNED);
    }

    /**
     * 创建目录节点<br/>
     * 字面意思
     * @param dir 目标目录
     * @param parentId 父节点ID
     * @return 目录节点对象
     */
    private DirectoryNode createDirectoryNode(File dir, String parentId) {
        // 创建一个目录节点骨架
        DirectoryNode node = new DirectoryNode();

        // 填充基本字段（目录节点继承的那家伙的字段）
        // 子节点集不设置，在扫描结束后才会赋进去
        // 节点ID
        node.setId(fileIO.generateId(dir));
        // 节点名
        node.setName(dir.getName());
        // 节点类型
        node.setNodeType(NodeType.DIRECTORY);
        // 节点路径（取绝对路径）
        node.setPath(dir.getAbsolutePath());
        // 父节点ID
        node.setParentNode(parentId);

        // 填充特有字段
        // 扫描状态（默认给未扫描）
        node.setScanStatus(ScanStatus.NOT_SCANNED);

        return node;
    }

    /**
     * 创建文件节点<br/>
     * 字面意思
     * @param file 目标文件
     * @param parentId 父节点ID
     * @return 文件节点对象
     */
    private FileNode createFileNode(File file, String parentId) {
        // 创建一个文件节点骨架
        FileNode node = new FileNode();

        // 也是填充基本字段
        // 节点ID
        node.setId(fileIO.generateId(file));
        // 节点名
        node.setName(fileIO.stripExtension(file.getName()));
        // 节点类型
        node.setNodeType(NodeType.File);
        // 节点路径
        node.setPath(file.getAbsolutePath());
        // 父节点ID
        node.setParentNode(parentId);

        // 填充特有字段
        // 文件大小
        node.setSize(String.valueOf(file.length()));
        // 文件类型（后缀嘛）
        node.setFileType(fileIO.getFileExtension(file.getName()));

        return node;
    }
}
