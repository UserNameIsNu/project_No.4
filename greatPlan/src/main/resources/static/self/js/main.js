/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

import { fetchRootDirectory, mergeAndRepairTree, pollAndSyncTree } from './tree/tree-core.js';
import { refreshPlugins } from './plugins/plugin-manager.js';
import { fetchStandard } from './core/api.js';

// 初始化全局API和状态
// 创建CoreAPI全局对象，提供日志功能
window.CoreAPI = { log: (msg) => console.log("[CoreAPI]", msg) };
// 初始化已加载插件存储对象
window.loadedPlugins = {};
// 初始化上一次插件注册表缓存
window.lastPluginRegistry = [];

// 所有DOM操作都在DOMContentLoaded事件中执行
// 等待DOM完全加载完成后执行初始化代码
document.addEventListener("DOMContentLoaded", () => {
    console.log("✅ DOM 已就绪");
    // 获取DOM元素引用
    // 获取树状结构容器元素
    const treeContainer = document.getElementById("treeContainer");
    // 获取目录扫描表单元素
    const form = document.getElementById("directoryForm");

    // 步骤1：初始化根目录
    // 从服务器加载根目录数据并渲染到树状结构中
    fetchRootDirectory(treeContainer);

    // 步骤2：绑定表单提交事件
    form.addEventListener("submit", function (e) {
        // 阻止表单默认提交行为（页面跳转）
        e.preventDefault();
        // 从表单元素中获取表单数据
        const formData = new FormData(this);
        // 发送目录扫描请求到服务器
        fetchStandard("http://127.0.0.1:8080/api/file", {
            method: "POST",
            body: formData,
        })
            .then((data) => {
                // 请求成功：合并新数据到现有树状结构中
                mergeAndRepairTree(data);
                // 重新渲染所有根节点
                renderAllRoots(treeContainer);
            })
            .catch((error) => console.error("扫描错误:", error.message));
    });

    // 步骤3：启动轮询机制
    // 每1秒执行一次树状结构同步
    setInterval(() => pollAndSyncTree(treeContainer), 1000);

    // 步骤4：初始化插件系统
    // 立即执行一次插件刷新
    refreshPlugins();
    // 每1秒执行一次插件刷新检查
    setInterval(refreshPlugins, 1000);
});

export { fetchStandard } from './core/api.js';
export { mergeAndRepairTree, pollAndSyncTree } from './tree/tree-core.js';
