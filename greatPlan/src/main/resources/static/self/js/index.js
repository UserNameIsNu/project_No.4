/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

const allNodes = {};
const roots = { list: [] }; // 小改动：用对象包装更安全

/** 统一的请求封装函数（适配后端 StandardViewResponseObject<T>） */
async function fetchStandard(url, options = {}) {
    try {
        const res = await fetch(url, options);
        const json = await res.json();
        if (json.code !== 200) throw new Error(json.message || "请求失败");
        return json.data;
    } catch (err) {
        console.error(`[Fetch Error] ${url}:`, err.message);
        throw err;
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const treeContainer = document.getElementById("treeContainer");
    fetchRootDirectory(treeContainer);
});

document.getElementById("directoryForm").addEventListener("submit", function (e) {
    e.preventDefault();
    const formData = new FormData(this);
    const treeContainer = document.getElementById("treeContainer");

    fetchStandard("http://127.0.0.1:8080/api/file", {
        method: "POST",
        body: formData,
    })
        .then((data) => {
            mergeAndRepairTree(data);
            renderAllRoots(treeContainer);
        })
        .catch((error) => console.error("扫描错误:", error.message));
});

window.CoreAPI = { log: (msg) => console.log("[CoreAPI]", msg) };
window.loadedPlugins = {};
window.lastPluginRegistry = [];

/** 卸载插件（清理CSS、JS、HTML） */
function unloadPlugin(pluginId) {
    document.querySelectorAll(`[data-plugin="${pluginId}"]`).forEach((e) => e.remove());
    const container = document.getElementById("plugin-" + pluginId);
    if (container) container.remove();
    delete window.loadedPlugins[pluginId];
    CoreAPI.log(`插件 ${pluginId} 已卸载`);
}

/** 获取根目录（POST /api/file/load） */
function fetchRootDirectory(container) {
    fetchStandard("http://127.0.0.1:8080/api/file/load", { method: "POST" })
        .then((data) => {
            mergeAndRepairTree(data);
            renderAllRoots(container);
        })
        .catch((err) => console.error("获取根目录失败:", err.message));
}

/**
 * 🔹 合并节点数据（不重绘整个树）
 * 🔹 若仅热度变化，则只更新热度显示
 */
function mergeAndRepairTree(newData) {
    for (const [id, node] of Object.entries(newData)) {
        const existing = allNodes[id];
        if (!existing) {
            allNodes[id] = structuredClone(node);
        } else {
            // 检查热度是否变化
            if (node.clickHeat !== undefined && node.clickHeat !== existing.clickHeat) {
                existing.clickHeat = node.clickHeat;
                updateNodeHeatDisplay(id, node.clickHeat);
            }

            // 修复树结构
            if ((!existing.childNode || existing.childNode.length === 0) && node.childNode?.length > 0)
                existing.childNode = [...node.childNode];
            if (!existing.parentNode && node.parentNode)
                existing.parentNode = node.parentNode;
        }
    }

    for (const node of Object.values(allNodes)) {
        if (node.parentNode && allNodes[node.parentNode]) {
            const parent = allNodes[node.parentNode];
            parent.childNode = parent.childNode || [];
            if (!parent.childNode.includes(node.id)) parent.childNode.push(node.id);
        }
    }

    const recalculatedRoots = Object.values(allNodes)
        .filter((node) => !node.parentNode || !allNodes[node.parentNode])
        .map((node) => node.id);

    roots.list.length = 0;
    roots.list.push(...new Set(recalculatedRoots));
}

/** 🔸 仅更新节点热度显示（不刷新整棵树） */
function updateNodeHeatDisplay(id, newHeat) {
    const el = document.querySelector(`[data-node-id="${id}"] .node-label`);
    if (!el) return;

    const oldText = el.textContent.replace(/\(\d+\)$/, "").trim();
    el.textContent = `${oldText} (${newHeat})`;

    el.classList.add("heat-updated");
    setTimeout(() => el.classList.remove("heat-updated"), 300);
}

/** 渲染整棵树（首次加载时使用） */
function renderAllRoots(container) {
    container.innerHTML = "";
    const ul = document.createElement("ul");
    roots.list.forEach((rootId) => {
        const rootNode = allNodes[rootId];
        if (rootNode) ul.appendChild(buildTreeHtml(rootNode));
    });
    container.appendChild(ul);
}

/** 生成节点 DOM */
function buildTreeHtml(node) {
    const li = document.createElement("li");
    li.dataset.nodeId = node.id;

    const displayName = node.name || (node.path ? node.path.split(/[\\/]/).filter(Boolean).pop() : "未知");
    const heatInfo = node.clickHeat !== undefined ? ` (${node.clickHeat})` : "";

    const label = document.createElement("span");
    label.className = "node-label";
    label.textContent = (node.nodeType === "DIRECTORY" ? "📁 " : "📄 ") + displayName + heatInfo;
    li.appendChild(label);

    if (node.nodeType === "DIRECTORY") {
        li.classList.add("directory");
        label.addEventListener("click", (e) => {
            e.stopPropagation();
            const hasChildren = node.childNode?.length > 0;
            const fullyScanned = node.scanStatus === "FULLY_SCANNED";
            if (hasChildren || fullyScanned) li.classList.toggle("collapsed");
            else fetchSubDirectory(node, li);
        });

        const ul = document.createElement("ul");
        if (node.childNode?.length > 0) {
            node.childNode.forEach((childId) => {
                const childNode = allNodes[childId];
                if (childNode) ul.appendChild(buildTreeHtml(childNode));
            });
        }
        li.appendChild(ul);
    } else {
        li.classList.add("file");
    }

    return li;
}

/** 请求子目录内容并渲染 */
function fetchSubDirectory(node, liElement) {
    const formData = new FormData();
    formData.append("path", node.path);

    fetchStandard("http://127.0.0.1:8080/api/file", { method: "POST", body: formData })
        .then((data) => {
            mergeAndRepairTree(data);
            let ul = liElement.querySelector("ul");
            if (!ul) {
                ul = document.createElement("ul");
                liElement.appendChild(ul);
            }
            ul.innerHTML = "";
            node.childNode?.forEach((childId) => {
                const childNode = allNodes[childId];
                if (childNode) ul.appendChild(buildTreeHtml(childNode));
            });
            liElement.classList.remove("collapsed");
        })
        .catch((err) => console.error("获取子目录失败:", err.message));
}

/** 加载/刷新插件 */
async function loadPlugin(meta) {
    const area = document.getElementById("plugins-area");
    let container = document.getElementById("plugin-" + meta.id);
    if (!container) {
        container = document.createElement("div");
        container.id = "plugin-" + meta.id;
        area.appendChild(container);
    }

    const base = "/plugins/";
    const loaded = window.loadedPlugins[meta.id] || {};
    window.loadedPlugins[meta.id] = loaded;

    // CSS
    if (meta.css?.length) {
        meta.css.forEach((cssFile) => {
            const hash = meta.versions?.[cssFile] || "";
            if (loaded[cssFile] !== hash) {
                document.querySelector(`link[data-plugin="${meta.id}"][href*="${cssFile}"]`)?.remove();
                const link = document.createElement("link");
                link.rel = "stylesheet";
                link.setAttribute("data-plugin", meta.id);
                link.href = base + cssFile + "?v=" + hash;
                document.head.appendChild(link);
                loaded[cssFile] = hash;
            }
        });
    }

    // HTML
    if (meta.html?.length) {
        const htmlFile = meta.html[0];
        const hash = meta.versions?.[htmlFile] || "";
        if (loaded[htmlFile] !== hash) {
            const html = await fetch(base + htmlFile + "?v=" + hash).then((r) => r.text());
            container.innerHTML = html;
            loaded[htmlFile] = hash;
        }
    }

    // JS
    if (meta.js?.length) {
        for (const jsFile of meta.js) {
            const hash = meta.versions?.[jsFile] || "";
            if (loaded[jsFile] !== hash) {
                document.querySelector(`script[data-plugin="${meta.id}"][src*="${jsFile}"]`)?.remove();
                await new Promise((resolve, reject) => {
                    const script = document.createElement("script");
                    script.src = base + jsFile + "?v=" + hash;
                    script.setAttribute("data-plugin", meta.id);
                    script.onload = resolve;
                    script.onerror = reject;
                    document.body.appendChild(script);
                });
                loaded[jsFile] = hash;
            }
        }
    }

    CoreAPI.log(`插件 ${meta.id} 已加载/刷新`);
}

/** 刷新插件注册表 */
async function refreshPlugins() {
    try {
        const registry = await fetchStandard("/api/plugins");
        const oldMap = Object.fromEntries(window.lastPluginRegistry.map((p) => [p.id, p]));
        const newMap = Object.fromEntries(registry.map((p) => [p.id, p]));

        for (const id in newMap) {
            if (!oldMap[id]) {
                CoreAPI.log(`检测到新插件：${id}`);
                await loadPlugin(newMap[id]);
            } else {
                const oldVer = oldMap[id].versions || {};
                const newVer = newMap[id].versions || {};
                const changedFiles = Object.keys(newVer).filter((f) => newVer[f] !== oldVer[f]);
                if (changedFiles.length > 0) {
                    CoreAPI.log(`检测到插件 ${id} 文件变化：${changedFiles.join(", ")}`);
                    await loadPlugin(newMap[id]);
                }
            }
        }

        for (const id in oldMap) {
            if (!newMap[id]) {
                CoreAPI.log(`检测到插件被移除：${id}`);
                unloadPlugin(id);
            }
        }

        window.lastPluginRegistry = registry;
    } catch (err) {
        console.error("刷新插件失败:", err.message);
    }
}

/** 轮询同步最新节点树（热度、增删节点） */
async function pollAndSyncTree(container) {
    try {
        const data = await fetchStandard("/api/file/tree", { method: "POST" });

        const newIds = new Set(Object.keys(data));
        const oldIds = new Set(Object.keys(allNodes));

        // 1️⃣ 删除前端已有但后端不存在的节点（排除根节点）
        for (const id of oldIds) {
            if (!newIds.has(id) && !roots.list.includes(id)) {
                delete allNodes[id];
                const el = container.querySelector(`[data-node-id="${id}"]`);
                if (el) el.remove();
            }
        }

        // 2️⃣ 合并新数据并更新热度
        for (const [id, node] of Object.entries(data)) {
            const existing = allNodes[id];
            if (!existing) {
                // 新节点，直接加入
                allNodes[id] = structuredClone(node);
                // 这里可以选择立即渲染到 DOM
                // 例如找到父节点的 UL
                const parentEl = node.parentNode
                    ? container.querySelector(`[data-node-id="${node.parentNode}"] > ul`)
                    : container.querySelector("ul"); // 根节点
                if (parentEl) parentEl.appendChild(buildTreeHtml(node));
            } else {
                // 已有节点，只更新热度
                if (node.clickHeat !== undefined && node.clickHeat !== existing.clickHeat) {
                    existing.clickHeat = node.clickHeat;
                    updateNodeHeatDisplay(id, node.clickHeat);
                }
                // 修复 parent/child 信息
                if ((!existing.childNode || existing.childNode.length === 0) && node.childNode?.length > 0)
                    existing.childNode = [...node.childNode];
                if (!existing.parentNode && node.parentNode)
                    existing.parentNode = node.parentNode;
            }
        }

        // 3️⃣ 更新根节点集合
        const recalculatedRoots = Object.values(allNodes)
            .filter((node) => !node.parentNode || !allNodes[node.parentNode])
            .map((node) => node.id);

        roots.list.length = 0;
        roots.list.push(...new Set(recalculatedRoots));

    } catch (err) {
        console.error("轮询同步节点树失败:", err.message);
    }
}

// 启动轮询，每秒一次
const treeContainer = document.getElementById("treeContainer");
setInterval(() => pollAndSyncTree(treeContainer), 1000);

window.addEventListener("DOMContentLoaded", refreshPlugins);
setInterval(refreshPlugins, 1000);
