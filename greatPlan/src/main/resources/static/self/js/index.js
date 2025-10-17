/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

window.CoreAPI = {
    log: msg => console.log("[CoreAPI]", msg)
};

async function loadPlugin(meta) {
    const area = document.getElementById("plugins-area");
    const container = document.createElement("div");
    container.id = "plugin-" + meta.id;
    area.appendChild(container);

    const base = "/plugins/"; // 直接映射到 plugins 目录

    // 加载 CSS
    if (meta.css && meta.css.length) {
        meta.css.forEach(cssFile => {
            const link = document.createElement("link");
            link.rel = "stylesheet";
            link.href = base + cssFile;  // meta.css 已经包含 pluginName
            document.head.appendChild(link);
        });
    }

    // 加载 HTML
    if (meta.html && meta.html.length) {
        const html = await fetch(base + meta.html[0]).then(r => r.text());
        container.innerHTML = html;
    }

    // 加载 JS
    if (meta.js && meta.js.length) {
        for (const jsFile of meta.js) {
            await new Promise((resolve, reject) => {
                const script = document.createElement("script");
                script.src = base + jsFile;
                script.onload = resolve;
                script.onerror = reject;
                document.body.appendChild(script);
            });
        }
    }

    CoreAPI.log(`插件 ${meta.id} 已加载`);
}

async function refreshPlugins() {
    const res = await fetch("/api/plugins");
    const list = await res.json();

    const area = document.getElementById("plugins-area");
    area.innerHTML = "";

    for (const plugin of list) {
        await loadPlugin(plugin); // 确保按顺序加载插件
    }
}

window.addEventListener("DOMContentLoaded", refreshPlugins);
setInterval(refreshPlugins, 3000);
