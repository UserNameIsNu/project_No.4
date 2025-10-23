/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

const allNodes = {};
const roots = [];

document.addEventListener('DOMContentLoaded', () => {
    const treeContainer = document.getElementById('treeContainer');

    // 🔹 页面加载时获取根目录
    fetchRootDirectory(treeContainer);
});

document.getElementById('directoryForm').addEventListener('submit', function(e) {
    e.preventDefault();

    const formData = new FormData(this);
    const treeContainer = document.getElementById('treeContainer');

    fetch('http://127.0.0.1:8080/api/file', {
        method: 'POST',
        body: formData
    })
        .then(response => response.json())
        .then(data => {
            mergeAndRepairTree(data);
            renderAllRoots(treeContainer);
        })
        .catch(error => console.error('扫描错误:', error));
});

window.CoreAPI = {
    log: msg => console.log("[CoreAPI]", msg)
};

// 存储已加载的插件资源版本（文件级）
window.loadedPlugins = {};
// 存储上次从后端拿到的插件注册表（插件级）
window.lastPluginRegistry = [];

/**
 * 卸载插件（清理CSS、JS、HTML）
 */
function unloadPlugin(pluginId) {
    // 移除 CSS/JS
    document.querySelectorAll(`[data-plugin="${pluginId}"]`).forEach(e => e.remove());
    // 移除 HTML 容器
    const container = document.getElementById("plugin-" + pluginId);
    if (container) container.remove();
    // 清理缓存记录
    delete window.loadedPlugins[pluginId];
    CoreAPI.log(`插件 ${pluginId} 已卸载`);
}

function fetchRootDirectory(container) {
    fetch('http://127.0.0.1:8080/api/file/load', {
        method: 'POST'
        // 不传 path 即返回根目录
    })
        .then(res => res.json())
        .then(data => {
            mergeAndRepairTree(data);
            renderAllRoots(container);
        })
        .catch(err => console.error('获取根目录失败:', err));
}

function mergeAndRepairTree(newData) {
    for (const [id, node] of Object.entries(newData)) {
        const existing = allNodes[id];
        if (!existing) {
            allNodes[id] = structuredClone(node);
        } else {
            if ((!existing.childNode || existing.childNode.length === 0) &&
                node.childNode && node.childNode.length > 0) {
                existing.childNode = [...node.childNode];
            }
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

    for (const node of Object.values(allNodes)) {
        let current = node;
        while (current.parentNode && allNodes[current.parentNode]) {
            const parent = allNodes[current.parentNode];
            parent.childNode = parent.childNode || [];
            if (!parent.childNode.includes(current.id)) parent.childNode.push(current.id);
            current = parent;
        }
    }

    const recalculatedRoots = Object.values(allNodes)
        .filter(node => !node.parentNode || !allNodes[node.parentNode])
        .map(node => node.id);

    roots.length = 0;
    roots.push(...new Set(recalculatedRoots));
}

function renderAllRoots(container) {
    container.innerHTML = '';
    const ul = document.createElement('ul');
    roots.forEach(rootId => {
        const rootNode = allNodes[rootId];
        if (rootNode) ul.appendChild(buildTreeHtml(rootNode));
    });
    container.appendChild(ul);
}

function buildTreeHtml(node) {
    const li = document.createElement('li');

    // 🔹 如果 name 为空，从 path 提取
    const displayName = node.name || (node.path ? node.path.split(/[\\/]/).filter(Boolean).pop() : '未知');

    if (node.nodeType === "DIRECTORY") {
        li.classList.add("directory");
        li.textContent = "📁 " + displayName;

        li.addEventListener("click", (e) => {
            e.stopPropagation();

            const hasChildren = node.childNode && node.childNode.length > 0;
            const fullyScanned = node.scanStatus === "FULLY_SCANNED";

            if (hasChildren || fullyScanned) {
                li.classList.toggle("collapsed");
            } else {
                fetchSubDirectory(node, li);
            }
        });

        const ul = document.createElement('ul');
        if (node.childNode && node.childNode.length > 0) {
            node.childNode.forEach(childId => {
                const childNode = allNodes[childId];
                if (childNode) ul.appendChild(buildTreeHtml(childNode));
            });
        }
        li.appendChild(ul);
    } else {
        li.classList.add("file");
        li.textContent = "📄 " + displayName + (node.fileType ? "." + node.fileType : '');
    }

    return li;
}

/** 请求子目录内容并合并渲染 */
function fetchSubDirectory(node, liElement) {
    const formData = new FormData();
    formData.append('path', node.path);

    fetch('http://127.0.0.1:8080/api/file', {
        method: 'POST',
        body: formData
    })
        .then(res => res.json())
        .then(data => {
            mergeAndRepairTree(data);

            // 清空旧的 UL
            let ul = liElement.querySelector('ul');
            if (!ul) {
                ul = document.createElement('ul');
                liElement.appendChild(ul);
            }
            ul.innerHTML = '';

            // 渲染新子节点
            if (node.childNode && node.childNode.length > 0) {
                node.childNode.forEach(childId => {
                    const childNode = allNodes[childId];
                    if (childNode) ul.appendChild(buildTreeHtml(childNode));
                });
            }

            liElement.classList.remove("collapsed"); // 展开
        })
        .catch(err => console.error('获取子目录失败:', err));
}

/**
 * 加载/刷新插件（文件级版本检测）
 */
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

    // 加载 CSS
    if (meta.css && meta.css.length) {
        meta.css.forEach(cssFile => {
            const hash = meta.versions?.[cssFile] || '';
            if (loaded[cssFile] !== hash) { // 文件版本变化才刷新
                const oldLink = document.querySelector(`link[data-plugin="${meta.id}"][href*="${cssFile}"]`);
                if (oldLink) oldLink.remove();

                const link = document.createElement("link");
                link.rel = "stylesheet";
                link.setAttribute("data-plugin", meta.id);
                link.href = base + cssFile + "?v=" + hash;
                document.head.appendChild(link);

                loaded[cssFile] = hash;
            }
        });
    }

    // 加载 HTML
    if (meta.html && meta.html.length) {
        const htmlFile = meta.html[0];
        const hash = meta.versions?.[htmlFile] || '';
        if (loaded[htmlFile] !== hash) {
            const html = await fetch(base + htmlFile + "?v=" + hash).then(r => r.text());
            container.innerHTML = html;
            loaded[htmlFile] = hash;
        }
    }

    // 加载 JS
    if (meta.js && meta.js.length) {
        for (const jsFile of meta.js) {
            const hash = meta.versions?.[jsFile] || '';
            if (loaded[jsFile] !== hash) {
                const oldScript = document.querySelector(`script[data-plugin="${meta.id}"][src*="${jsFile}"]`);
                if (oldScript) oldScript.remove();

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

/**
 * 刷新插件注册表（插件级对比 + 文件级版本检测）
 */
async function refreshPlugins() {
    try {
        const res = await fetch("/api/plugins");
        const registry = await res.json();

        const oldMap = Object.fromEntries(window.lastPluginRegistry.map(p => [p.id, p]));
        const newMap = Object.fromEntries(registry.map(p => [p.id, p]));

        // 检查新增插件
        for (const id in newMap) {
            if (!oldMap[id]) {
                CoreAPI.log(`检测到新插件：${id}`);
                await loadPlugin(newMap[id]);
            } else {
                // 检查版本变化（文件级）
                const oldVer = oldMap[id].versions || {};
                const newVer = newMap[id].versions || {};
                const changedFiles = Object.keys(newVer).filter(f => newVer[f] !== oldVer[f]);
                if (changedFiles.length > 0) {
                    CoreAPI.log(`检测到插件 ${id} 文件变化：${changedFiles.join(", ")}`);
                    await loadPlugin(newMap[id]);
                }
            }
        }

        // 检查被删除的插件
        for (const id in oldMap) {
            if (!newMap[id]) {
                CoreAPI.log(`检测到插件被移除：${id}`);
                unloadPlugin(id);
            }
        }

        // 更新注册表快照
        window.lastPluginRegistry = registry;
    } catch (err) {
        console.error("刷新插件失败:", err);
    }
}

// 启动自动刷新
window.addEventListener("DOMContentLoaded", refreshPlugins);
setInterval(refreshPlugins, 1000);
