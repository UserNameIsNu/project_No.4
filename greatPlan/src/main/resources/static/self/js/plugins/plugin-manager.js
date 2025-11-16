/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

import { fetchStandard } from '../core/api.js';

/**
 * 卸载指定插件<br/>
 * （导出方法）<br/>
 * 清理插件相关的所有DOM元素和全局状态，实现插件的完全卸载
 * @param pluginId 要卸载的插件唯一标识符
 */
export function unloadPlugin(pluginId) {
    // 步骤1：移除所有带有该插件标识的DOM元素
    // 使用属性选择器查找所有 data-plugin 属性等于 pluginId 的元素
    // 这些元素包括：CSS链接、JS脚本、其他插件创建的DOM元素等
    document.querySelectorAll(`[data-plugin="${pluginId}"]`).forEach((e) => e.remove());

    // 步骤2：移除插件的主容器元素
    // 插件的主容器通常是一个div，ID格式为 "plugin-{pluginId}"
    const container = document.getElementById("plugin-" + pluginId);
    // 如果容器存在，则从DOM中移除
    if (container) container.remove();

    // 步骤3：清理全局状态记录
    // 从 window.loadedPlugins 对象中删除该插件的记录
    // 这确保系统知道该插件已被卸载，可以重新加载
    delete window.loadedPlugins[pluginId];

    // 步骤4：记录卸载日志
    // 使用 CoreAPI 记录插件卸载信息，便于调试和监控
    CoreAPI.log(`插件 ${pluginId} 已卸载`);
}

/**
 * 加载或刷新插件<br/>
 * （导出方法）<br/>
 * （异步方法）<br/>
 * 根据插件元数据加载CSS、HTML和JS资源，支持版本控制和增量更新
 * @param meta 插件元数据对象，包含插件ID、资源文件和版本信息
 * @returns {Promise<void>} 异步操作，不返回具体值但在所有资源加载完成后resolve
 */
export async function loadPlugin(meta) {
    // 获取插件区域DOM元素，所有插件都将放置在这个区域内
    const area = document.getElementById("plugins-area");
    // 查找是否已存在该插件的容器元素
    let container = document.getElementById("plugin-" + meta.id);
    // 如果插件容器不存在，则创建一个新的div元素作为容器
    if (!container) {
        container = document.createElement("div");
        // 设置容器ID，格式为"plugin-{插件ID}"，便于后续查找和管理
        container.id = "plugin-" + meta.id;
        // 将新创建的容器添加到插件区域中
        area.appendChild(container);
    }

    // 设置插件资源的基础路径，所有插件资源都从这个路径加载
    const base = "/plugins/";
    // 获取该插件当前的加载状态记录，如果不存在则初始化为空对象
    const loaded = window.loadedPlugins[meta.id] || {};
    // 更新全局加载状态记录，确保后续操作使用最新的状态
    window.loadedPlugins[meta.id] = loaded;

    // CSS资源加载部分
    // 检查插件元数据中是否有CSS文件配置（使用可选链操作符避免空指针错误）
    if (meta.css?.length) {
        // 遍历所有CSS文件配置
        meta.css.forEach((cssFile) => {
            // 获取该CSS文件的版本哈希值，如果不存在则使用空字符串
            const hash = meta.versions?.[cssFile] || "";

            // 检查该CSS文件是否已加载且版本未变化
            if (loaded[cssFile] !== hash) {
                // 版本发生变化或首次加载，先移除旧的CSS链接（如果存在）
                // 使用可选链操作符避免在元素不存在时报错
                document.querySelector(`link[data-plugin="${meta.id}"][href*="${cssFile}"]`)?.remove();

                // 创建新的link元素用于加载CSS
                const link = document.createElement("link");
                // 设置rel属性为stylesheet，表明这是一个样式表链接
                link.rel = "stylesheet";
                // 设置data-plugin属性，标记这个CSS属于哪个插件
                link.setAttribute("data-plugin", meta.id);
                // 设置CSS文件路径，添加版本参数避免浏览器缓存旧版本
                link.href = base + cssFile + "?v=" + hash;
                // 将CSS链接添加到文档的head部分
                document.head.appendChild(link);

                // 更新加载状态记录，记录当前加载的版本哈希
                loaded[cssFile] = hash;
            }
        });
    }

    // HTML内容加载部分
    // 检查插件是否有HTML文件配置
    if (meta.html?.length) {
        // 目前设计只支持一个HTML文件，取数组中的第一个元素
        const htmlFile = meta.html[0];
        // 获取HTML文件的版本哈希值
        const hash = meta.versions?.[htmlFile] || "";
        // 检查HTML文件是否已加载且版本未变化
        if (loaded[htmlFile] !== hash) {
            // 异步获取HTML文件内容
            // 使用fetch API请求HTML文件，添加版本参数避免缓存
            const html = await fetch(base + htmlFile + "?v=" + hash).then((r) => r.text());
            // 将获取的HTML内容设置到插件容器中
            container.innerHTML = html;
            // 更新加载状态记录
            loaded[htmlFile] = hash;
        }
    }

    // JavaScript脚本加载部分
    // 检查插件是否有JS文件配置
    if (meta.js?.length) {
        // 使用for...of循环（而非forEach）以便使用await实现顺序加载
        for (const jsFile of meta.js) {
            // 获取JS文件的版本哈希值
            const hash = meta.versions?.[jsFile] || "";

            // 检查JS文件是否已加载且版本未变化
            if (loaded[jsFile] !== hash) {
                // 先移除旧的JS脚本（如果存在）
                document.querySelector(`script[data-plugin="${meta.id}"][src*="${jsFile}"]`)?.remove();

                // 使用Promise包装脚本加载过程，确保当前脚本加载完成后再加载下一个
                await new Promise((resolve, reject) => {
                    // 创建新的script元素
                    const script = document.createElement("script");
                    // 设置JS文件路径，添加版本参数
                    script.src = base + jsFile + "?v=" + hash;
                    // 设置data-plugin属性，标记这个脚本属于哪个插件
                    script.setAttribute("data-plugin", meta.id);
                    // 设置脚本加载成功时的回调函数
                    script.onload = resolve;
                    // 设置脚本加载失败时的回调函数
                    script.onerror = reject;
                    // 将脚本添加到文档的body部分
                    document.body.appendChild(script);
                });

                // 更新加载状态记录
                loaded[jsFile] = hash;
            }
        }
    }
    CoreAPI.log(`插件 ${meta.id} 已加载/刷新`);
}

/**
 * 刷新插件注册表<br/>
 * （导出方法）<br/>
 * （异步方法）<br/>
 * 从服务器获取最新的插件列表，与当前已加载的插件进行比较，
 * 实现插件的动态加载、更新和卸载，支持热更新
 * @returns {Promise<void>} 异步操作，不返回具体值
 */
export async function refreshPlugins() {
    try {
        // 步骤1：从服务器获取最新的插件注册表
        // 使用 fetchStandard 函数发送请求，获取插件列表数据
        const registry = await fetchStandard("/api/plugins");

        // 步骤2：构建插件映射表，便于快速查找
        // 将上一次的插件注册表转换为 {插件ID: 插件数据} 的映射对象
        const oldMap = Object.fromEntries(window.lastPluginRegistry.map((p) => [p.id, p]));
        // 将最新的插件注册表同样转换为映射对象
        const newMap = Object.fromEntries(registry.map((p) => [p.id, p]));

        // 步骤3：处理新增和更新的插件
        // 遍历新插件映射表中的所有插件ID
        for (const id in newMap) {
            // 检查插件是否在旧映射表中不存在（新增插件）
            if (!oldMap[id]) {
                // 记录新增插件日志
                CoreAPI.log(`检测到新插件：${id}`);
                // 加载新增插件
                await loadPlugin(newMap[id]);
            } else {
                // 插件已存在，检查是否有文件更新

                // 获取旧版本和新版本的版本信息对象
                // 使用空对象作为默认值，避免undefined错误
                const oldVer = oldMap[id].versions || {};
                const newVer = newMap[id].versions || {};

                // 找出所有版本发生变化的文件
                // 遍历新版本中的所有文件，筛选出版本哈希值发生变化的文件
                const changedFiles = Object.keys(newVer).filter((f) => newVer[f] !== oldVer[f]);

                // 如果有文件发生变化
                if (changedFiles.length > 0) {
                    // 记录文件变化日志，显示具体哪些文件发生了变化
                    CoreAPI.log(`检测到插件 ${id} 文件变化：${changedFiles.join(", ")}`);
                    // 重新加载插件（热更新）
                    await loadPlugin(newMap[id]);
                }
                // 如果没有文件变化，则跳过，继续使用当前加载的版本
            }
        }

        // 步骤4：处理被移除的插件
        // 遍历旧插件映射表中的所有插件ID
        for (const id in oldMap) {
            // 检查插件是否在新映射表中不存在（插件已被移除）
            if (!newMap[id]) {
                // 记录插件移除日志
                CoreAPI.log(`检测到插件被移除：${id}`);
                // 卸载被移除的插件
                unloadPlugin(id);
            }
        }

        // 步骤5：更新插件注册表缓存
        // 将当前最新的插件注册表保存到全局变量中，供下一次比较使用
        window.lastPluginRegistry = registry;
    } catch (err) {
        console.error("刷新插件失败:", err.message);
    }
}
