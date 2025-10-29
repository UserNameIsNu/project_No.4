/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.web;

import com.ljf.greatplan.core.entity.Node;
import com.ljf.greatplan.core.entity.StandardViewResponseObject;
import com.ljf.greatplan.core.service.FileSystemService;
import com.ljf.greatplan.general.scanner.SpecifyDirectoryScanner;
import com.ljf.greatplan.general.tools.generalTools.FileIO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件控制器<br/>
 * 用于获取文件相关信息的请求点
 */
@RestController
@RequestMapping("/api/file")
public class FileController extends BaseController{
    /**
     * 指定目录扫描器
     */
    private SpecifyDirectoryScanner scanner;

    /**
     * 文件系统服务器
     */
    private FileSystemService fileSystemService;

    /**
     * 文件IO工具类
     */
    private FileIO fileIO;

    /**
     * 构造器
     * @param scanner
     * @param fileSystemService
     * @param fileIO
     */
    public FileController(SpecifyDirectoryScanner scanner, FileSystemService fileSystemService, FileIO fileIO) {
        this.scanner = scanner;
        this.fileSystemService = fileSystemService;
        this.fileIO = fileIO;
    }

    /**
     * 获取指定路径的扫描结果<br/>
     * 仅对这个路径与深一级做扫描。
     * @param path 目标路径
     * @return 节点树
     */
    @PostMapping
    public StandardViewResponseObject<Map<String, Node>>  getDirectory(@RequestParam String path) {
        return success(scanner.initialScanner(path));
    }

    /**
     * 获取当前设备的根目录<br/>
     * 为了页面初始加载时显示点啥，深度统一使用配置文件里面定的。
     * @return 节点树
     */
    @PostMapping("/load")
    public StandardViewResponseObject<Map<String, Node>> getRootDirectory() {
        List<String> roots = fileIO.getRoot();
        return success(scanner.scanDirList(roots));
    }

    /**
     * 热度增长<br/>
     * 用于在用户点击任意节点时，为这个节点的点击热度做增长。
     * @return 无返回
     */
    @PostMapping("/click")
    public StandardViewResponseObject<Void> popularityIncreases(@RequestParam String nodeId) {
        fileSystemService.popularityIncreases(nodeId);
        return success();
    }

    /**
     * 获取树
     * @return 节点树
     */
    @PostMapping("/tree")
    public StandardViewResponseObject<Map<String, Node>> getTree() {
        return success(fileSystemService.getTree());
    }
}
