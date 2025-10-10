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
    if (meta.css) {
        const link = document.createElement("link");
        link.rel = "stylesheet";
        link.href = base + meta.css;
        document.head.appendChild(link);
    }

    // 加载 HTML
    const html = await fetch(base + meta.html).then(r => r.text());
    container.innerHTML = html;

    // 加载 JS
    if (meta.js) {
        const script = document.createElement("script");
        script.src = base + meta.js;
        document.body.appendChild(script);
    }

    CoreAPI.log(`插件 ${meta.id} 已加载`);
}

async function refreshPlugins() {
    const res = await fetch("/api/plugins");
    const list = await res.json();

    const area = document.getElementById("plugins-area");
    area.innerHTML = "";

    list.forEach(loadPlugin);
}

window.addEventListener("DOMContentLoaded", refreshPlugins);
setInterval(refreshPlugins, 3000);
