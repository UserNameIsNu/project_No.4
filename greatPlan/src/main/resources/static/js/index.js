window.CoreAPI = {
    log: msg => console.log("[CoreAPI]", msg)
};

async function loadPlugin(meta) {
    const area = document.getElementById("plugins-area");
    const container = document.createElement("div");
    container.id = "plugin-" + meta.id;
    area.appendChild(container);

    const base = "/plugins/" + meta.id + "/"; // runtime-dir 映射

    // 加载 CSS
    if (meta.css && meta.css.length) {
        meta.css.forEach(cssFile => {
            const link = document.createElement("link");
            link.rel = "stylesheet";
            link.href = base + cssFile;
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
                script.onload = resolve;   // 确保 JS 加载完才执行下一个
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
        await loadPlugin(plugin.meta); // 确保按顺序加载插件
    }
}

window.addEventListener("DOMContentLoaded", refreshPlugins);
setInterval(refreshPlugins, 3000);
