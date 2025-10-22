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
