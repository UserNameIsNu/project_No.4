/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

import { allNodes } from '../core/state.js';
import { fetchStandard } from '../core/api.js';
import { mergeAndRepairTree } from './tree-core.js';

/**
 * 更新节点热度显示<br/>
 * （导出方法）<br/>
 * 用于在界面上动态更新指定节点的热度数值，并添加视觉反馈效果。
 * @param id 节点的唯一标识符
 * @param newHeat 新的热度数值
 */
export function updateNodeHeatDisplay(id, newHeat) {
    // 通过CSS选择器查找指定节点的标签元素
    // 选择器格式：[data-node-id="节点ID"] .node-label
    // 这表示查找具有data-node-id属性且值为指定id的元素内部的.node-label类元素
    const el = document.querySelector(`[data-node-id="${id}"] .node-label`);
    // 如果找不到对应的DOM元素，直接返回，避免后续操作出错
    if (!el) return;

    // 移除旧的热度显示，保留纯节点名称
    // 使用正则表达式 /\(\d+\)$/ 匹配文本末尾的括号数字格式（如 "(5)"）
    // replace方法将其替换为空字符串，然后trim()去除首尾空格
    const oldText = el.textContent.replace(/\(\d+\)$/, "").trim();

    // 设置新的文本内容：原节点名称 + 新热度值（用括号包裹）
    el.textContent = `${oldText} (${newHeat})`;

    // 添加CSS类名，触发高亮或动画效果
    // 这个类通常用于实现热度更新时的视觉反馈，如颜色变化、闪烁等
    el.classList.add("heat-updated");

    // 设置300毫秒后移除高亮效果类名
    // 这创建了一个短暂的高亮效果，让用户感知到热度值的变化
    setTimeout(() => el.classList.remove("heat-updated"), 300);
}

/**
 * 渲染根节点<br/>
 * （导出方法）<br/>
 * 往前端节点树的DOM对象里面塞收到的设备根节点。
 * 一般这个方法也就是首次加载时用一次。
 * @param container
 */
export function renderAllRoots(container) {
    // 清空前端节点树的DOM对象
    container.innerHTML = "";
    // 创建标签
    const ul = document.createElement("ul");
    // 遍历根节点集
    roots.list.forEach((rootId) => {
        // 在前端节点树里获取对应的节点对象
        const rootNode = allNodes[rootId];
        // 若存在，塞进标签里
        if (rootNode) ul.appendChild(buildTreeHtml(rootNode));
    });
    // 塞进前端节点树的DOM对象里
    container.appendChild(ul);
}

/**
 * 渲染树<br/>
 * （导出方法）<br/>
 * 根据节点数据创建对应的DOM元素，包括图标、名称、热度显示和交互功能。
 * @param node 节点数据对象
 * @returns {HTMLLIElement} 构建好的li元素
 */
export function buildTreeHtml(node) {
    // 创建列表项元素，作为树节点的容器
    const li = document.createElement("li");
    // 设置数据属性，用于标识节点ID，便于后续查找和操作
    li.dataset.nodeId = node.id;

    // 计算显示名称：
    // 优先使用node.name，如果没有则从path中提取最后一部分，最后降级为"未知"
    const displayName = node.name || (node.path ? node.path.split(/[\\/]/).filter(Boolean).pop() : "未知");
    // 如果有热度值，则格式化为 "(热度值)" 的字符串，否则为空字符串
    const heatInfo = node.clickHeat !== undefined ? ` (${node.clickHeat})` : "";

    // 创建标签元素，用于显示节点名称和热度
    const label = document.createElement("span");
    // 添加CSS类名，用于样式控制
    label.className = "node-label";

    // 设置标签文本内容：
    // 目录使用文件夹图标📁，文件使用文档图标📄
    // 后面跟上显示名称和热度信息
    label.textContent = (node.nodeType === "DIRECTORY" ? "📁 " : "📄 ") + displayName + heatInfo;

    // 将标签添加到列表项中
    li.appendChild(label);

    // 判断节点类型，分别处理目录和文件
    if (node.nodeType === "DIRECTORY") {
        // 目录节点处理逻辑

        // 添加目录类名，用于CSS样式区分
        li.classList.add("directory");
        // 为目录标签添加点击事件监听器
        label.addEventListener("click", (e) => {
            // 阻止事件冒泡，避免触发父元素的点击事件
            e.stopPropagation();

            // 判断节点是否处于冷却状态（热度为0）
            const isCold = node.clickHeat === 0;
            // 查找当前目录下是否已有子节点列表
            const ul = li.querySelector("ul");
            // 检查是否有可见的子元素（已渲染的子节点）
            const hasVisibleChildren = ul && ul.children.length > 0;

            // 根据条件决定展开/收起还是请求数据
            if (hasVisibleChildren && !isCold) {
                // 情况1：DOM上已有子节点且节点未冷却
                // 切换展开/收起状态（通过CSS类名控制显示隐藏）
                li.classList.toggle("collapsed");
            } else {
                // 情况2：DOM上没有子节点 或 节点已冷却
                // 强制请求服务器扫描子目录数据
                fetchSubDirectory(node, li);
            }

            // 无条件发送热度增长请求
            // 每次点击目录都会增加该节点的热度值
            if (node.id) {
                fetch("/api/file/click", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: new URLSearchParams({ nodeId: node.id })
                }).catch(err => console.error("热度增长失败:", err.message));
            }
        });

        // 创建子节点容器（ul元素）
        const ul = document.createElement("ul");
        // 如果当前目录有子节点数据
        if (node.childNode?.length > 0) {
            // 遍历所有子节点ID
            node.childNode.forEach((childId) => {
                // 从全局节点集合中获取子节点数据
                const childNode = allNodes[childId];
                // 如果子节点存在，递归构建子节点HTML并添加到容器中
                if (childNode) ul.appendChild(buildTreeHtml(childNode));
            });
        }
        // 将子节点容器添加到当前目录节点
        li.appendChild(ul);
    } else {
        // 文件节点处理逻辑（相对简单）
        // 只需添加文件类名用于样式区分
        li.classList.add("file");
    }
    // 返回构建完成的列表项元素
    return li;
}

/**
 * 获取并渲染子目录内容<br/>
 * 当用户点击目录节点时，向服务器请求该目录的子节点数据，并更新DOM显示
 * @param node 当前目录节点数据对象
 * @param liElement 当前目录对应的DOM列表项元素
 */
function fetchSubDirectory(node, liElement) {
    // 创建表单数据对象，用于向服务器发送请求
    const formData = new FormData();
    // 添加路径参数，告诉服务器要扫描哪个目录
    formData.append("path", node.path);

    // 发送请求获取子目录数据
    fetchStandard("http://127.0.0.1:8080/api/file", { method: "POST", body: formData })
        .then((data) => {
            // 成功获取数据后，将新数据合并到全局节点树中
            // 这会更新allNodes和修复父子引用关系
            mergeAndRepairTree(data);
            // 查找当前目录节点下的ul元素（子节点容器）
            let ul = liElement.querySelector("ul");
            // 如果不存在子节点容器，则创建一个新的ul元素
            if (!ul) {
                ul = document.createElement("ul");
                liElement.appendChild(ul);
            }
            // 清空现有的子节点显示（准备重新渲染）
            ul.innerHTML = "";
            // 遍历当前目录的所有子节点ID
            node.childNode?.forEach((childId) => {
                // 从全局节点集合中获取子节点的完整数据
                const childNode = allNodes[childId];
                // 如果子节点数据存在，则构建对应的DOM元素并添加到容器中
                if (childNode) ul.appendChild(buildTreeHtml(childNode));
            });
            // 确保目录展开状态（移除折叠类名）
            liElement.classList.remove("collapsed");
        })
        .catch((err) => console.error("获取子目录失败:", err.message));
}
