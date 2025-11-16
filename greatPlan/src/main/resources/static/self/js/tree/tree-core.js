/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

import { allNodes, roots } from '../core/state.js';
import { fetchStandard } from '../core/api.js';
import { buildTreeHtml, updateNodeHeatDisplay, renderAllRoots } from './tree-dom.js';

/**
 * 获取根目录<br/>
 * （导出方法）<br/>
 * 获取当前设备的根目录，首次加载时使用。
 * @param container 前端节点树的DOM对象
 */
export function fetchRootDirectory(container) {
    // 用标准请求体打请求
    // 首次加载，获取设备根目录，不要让页面空着
    fetchStandard("http://127.0.0.1:8080/api/file/load", { method: "POST" })
        // 收到返回
        .then((data) => {
            // 合并修复前端节点树
            mergeAndRepairTree(data);
            // 渲染根节点
            renderAllRoots(container);
        })
        .catch((err) => console.error("获取根目录失败:", err.message));
}

/**
 * 合并修复前端节点树<br/>
 * （导出方法）<br/>
 * 用于根据新返回的后端节点树修复整理前端节点树。
 * 主要就是修复树结构，修复父子引用关系，更新节点热度。
 * 合并原来没有扫描出关系（提升为假根级），但后续扫描出来关系的节点（回落到实际位置）。
 * 补全节点的父节点与子节点的关系。
 * 热度变化的更新热度即可。
 * 最后重渲染。
 * @param newData 后端给的新节点树
 */
export function mergeAndRepairTree(newData) {
    // 遍历新节点树，将新节点树当成键值对形式遍历
    for (const [id, node] of Object.entries(newData)) {
        // 试图从前端节点树中获取当前遍历指向的节点
        const existing = allNodes[id];

        // 看看存不存在
        if (!existing) {
            // 若是新来的就塞进前端节点树里
            allNodes[id] = structuredClone(node);
        } else {
            // 已有的
            // 检查热度是否变化
            if (node.clickHeat !== undefined && node.clickHeat !== existing.clickHeat) {
                // 如果存在热度，且新旧节点热度不同
                // 更新热度
                existing.clickHeat = node.clickHeat;
                // 刷新渲染的热度显示
                updateNodeHeatDisplay(id, node.clickHeat);
            }

            // 修复树结构
            // 若当前节点（前端节点树中找到的同一节点）没有子节点，但后端返回的同一节点出现了子节点
            if ((!existing.childNode || existing.childNode.length === 0) && node.childNode?.length > 0)
                // 把后端传来的子节点信息刷进前端的节点里
                existing.childNode = [...node.childNode];
            // 若当前节点（前端节点树中找到的同一节点）没有父节点，但后端返回的同一节点出现了父节点
            if (!existing.parentNode && node.parentNode)
                // 把后端传来的父节点信息刷进前端的节点里
                existing.parentNode = node.parentNode;
        }
    }

    // 修复父子引用
    fixParentChildReferencing();

    // 重建根节点
    rebuildRootNode(document.getElementById("treeContainer"));
}

/**
 * 重建根节点<br/>
 * （导出方法）<br/>
 * 清空页面已经渲染的节点树相关的所有DOM元素，依照新的节点树重渲染。
 * @param container 前端节点树的DOM对象
 */
export function rebuildRootNode(container) {
    // 清空树里所有元素（把标签删光）
    container.innerHTML = "";

    // 修复父子引用
    fixParentChildReferencing();

    // 重新计算根节点
    // 获取前端节点树，仅拉出没有父节点的节点，做成键值对形式保存
    const recalculatedRoots = Object.values(allNodes)
        .filter((node) => !node.parentNode || !allNodes[node.parentNode])
        .map((node) => node.id);
    // 清空原来的根节点集
    roots.list.length = 0;
    // 重新赋值
    roots.list.push(...new Set(recalculatedRoots));

    // 重新渲染
    // 创建无序列表标签
    const ul = document.createElement("ul");
    // 遍历根节点集
    for (const rootId of roots.list) {
        // 拉出当前遍历指向的根节点
        const rootNode = allNodes[rootId];
        // 若这个根节点存在，塞进ul标签里，然后装填它下属的所有子节点
        if (rootNode) ul.appendChild(buildTreeHtml(rootNode));
    }
    // 把ul标签塞进节点树容器里
    container.appendChild(ul);
}

/**
 * 轮询并同步树<br/>
 * 就是拉取最新的节点树。
 * 对比旧树进行节点清理，热度更新和节点父子关系更新。
 * 然后重渲染。
 * @param container 前端节点树的DOM对象
 * @returns {Promise<void>} 异步操作完成后的任意返回值
 */
export async function pollAndSyncTree(container) {
    try {
        // 用标准请求体打请求
        // 获取后端最新的节点树
        const data = await fetchStandard("/api/file/tree", { method: "POST" });

        // 获取新旧两棵树的所有节点的ID（节点ID就是用文件路径取的哈希，用来检查存在性挺合适的）
        const newIds = new Set(Object.keys(data));
        const oldIds = new Set(Object.keys(allNodes));

        // 清理不再管理的节点（排除根）
        // 遍历旧树
        for (const id of oldIds) {
            // 若新树节点不存在这玩意，且不是根节点
            // 代表新的树中这玩意被删了
            if (!newIds.has(id) && !roots.list.includes(id)) {
                // 那就删掉！
                delete allNodes[id];
                // 获取页面中的标签，把这个节点对应渲染出来的标签也要清理调
                const el = container.querySelector(`[data-node-id="${id}"]`);
                // 若存在一并删掉
                if (el) el.remove();
            }
        }

        // 合并新数据并更新热度
        // 遍历新节点树
        for (const [id, node] of Object.entries(data)) {
            // 看看旧树有没有当前遍历指向的节点
            const existing = allNodes[id];
            // 若不存在对应节点
            if (!existing) {
                // 新节点，直接加入前端节点树
                allNodes[id] = structuredClone(node);
                // 在DOM中找到该节点的父级容器元素
                // 如果节点有父节点，就查找父节点对应的ul元素
                // 如果节点没有父节点（根节点），就查找根级的ul元素
                const parentEl = node.parentNode
                    ? container.querySelector(`[data-node-id="${node.parentNode}"] > ul`)
                    : container.querySelector("ul");

                // 如果找到了父级容器元素，就将新节点构建成DOM并添加进去
                if (parentEl) {
                    parentEl.appendChild(buildTreeHtml(node));
                }
            } else {
                // 节点已存在，需要更新数据
                // 检查热度是否有变化：新数据中有热度值且与旧值不同
                if (node.clickHeat !== undefined && node.clickHeat !== existing.clickHeat) {
                    // 更新前端节点树中的热度值
                    existing.clickHeat = node.clickHeat;
                    // 更新界面显示的热度数值
                    updateNodeHeatDisplay(id, node.clickHeat);
                }

                // 修复子节点信息
                // 如果现有节点没有子节点（或子节点数组为空），但新数据中有子节点
                if ((!existing.childNode || existing.childNode.length === 0) && node.childNode?.length > 0) {
                    // 将新数据中的子节点信息复制给现有节点
                    existing.childNode = [...node.childNode];
                }

                // 修复父节点信息
                // 如果现有节点没有父节点，但新数据中有父节点信息
                if (!existing.parentNode && node.parentNode) {
                    // 将新数据中的父节点信息设置给现有节点
                    existing.parentNode = node.parentNode;
                }
            }
        }

        // 重建根节点
        rebuildRootNode(container);
    } catch (err) {
        console.error("轮询同步节点树失败:", err.message);
    }
}

/**
 * 修复父子引用<br/>
 * 也就是补全节点的父节点与子节点的关系。
 */
function fixParentChildReferencing() {
    // 遍历前端节点树，获取每个节点
    for (const node of Object.values(allNodes)) {
        // 若某节点说自己存在父节点，且前端节点树里也找得到这个父节点
        if (node.parentNode && allNodes[node.parentNode]) {
            // 那么获取这个父节点
            const parent = allNodes[node.parentNode];
            // 确保父节点存在子节点数组（没有就赛一个进去）
            parent.childNode = parent.childNode || [];
            // 检查父节点的子节点集中是否有当前遍历指向的节点
            // 若没有就塞进去
            if (!parent.childNode.includes(node.id)) parent.childNode.push(node.id);
        }
    }
}
