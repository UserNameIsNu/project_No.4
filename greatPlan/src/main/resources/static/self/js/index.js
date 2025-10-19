window.CoreAPI = {
    log: msg => console.log("[CoreAPI]", msg)
};

// 存储已加载的插件资源版本
window.loadedPlugins = {};

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
    if (meta.css && meta.css.length) {
        meta.css.forEach(cssFile => {
            const hash = meta.versions?.[cssFile] || '';
            if (loaded[cssFile] !== hash) { // 仅在版本变化时刷新
                // 移除旧的 link
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

    // HTML
    if (meta.html && meta.html.length) {
        const htmlFile = meta.html[0];
        const hash = meta.versions?.[htmlFile] || '';
        if (loaded[htmlFile] !== hash) {
            const html = await fetch(base + htmlFile + "?v=" + hash).then(r => r.text());
            container.innerHTML = html;
            loaded[htmlFile] = hash;
        }
    }

    // JS
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

async function refreshPlugins() {
    const res = await fetch("/api/plugins");
    const list = await res.json();

    for (const plugin of list) {
        await loadPlugin(plugin);
    }
}

window.addEventListener("DOMContentLoaded", refreshPlugins);
setInterval(refreshPlugins, 500);
