/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

import { fetchStandard } from '../core/api.js';

/**
 * 插件状态（{有没有插件被激活， 被激活的插件的资源注册表}）
 * @type {{metaMap: {}, activeId: null}}
 */
export const state = {
    activeId: null,
    metaMap: {},
};

/**
 * 获取插件容器元素<br/>
 * 用于获取页面中专门用于显示插件内容的DOM容器。
 * @returns {HTMLElement}
 */
function getPluginArea() {
    // 通过元素的ID选择器获取插件区域容器
    return document.getElementById("plugins-area");
}

/**
 * 显示插件占位<br/>
 * 用于插件加载失败、空状态或等待状态的用户提示。
 * @param message 要显示的提示文案
 */
function showPluginPlaceholder(message) {
    // 获取插件内容区域的DOM容器元素
    const area = getPluginArea();
    // 如果插件区域不存在，则提前退出函数
    if (!area) return;
    // 使用innerHTML替换插件区域的整个内容
    area.innerHTML = `<div class="plugin-placeholder">${message}</div>`;
}

/**
 * 渲染插件导航栏<br/>
 * 根据插件注册表数据动态生成导航栏，包含所有可用插件的切换按钮。
 * @param registry 插件元数据集合，包含所有可用插件的信息
 */
function renderPluginNav(registry) {
    // 获取导航栏的DOM容器元素
    const nav = document.getElementById("plugins-nav");
    // 如果导航栏容器不存在，则提前退出函数
    if (!nav) return;
    // 清空导航栏的现有内容，为重新渲染做准备
    nav.innerHTML = "";

    // 检查插件注册表是否为空数组
    // 如果没有可用的插件，显示空状态提示
    if (!registry.length) {
        // 在导航栏中显示无插件的提示信息
        nav.innerHTML = `<p class="plugin-placeholder">暂无插件</p>`;
        return;
    }

    // 创建导航栏标题元素
    const title = document.createElement("p");
    // 设置CSS类名，用于样式控制
    title.className = "plugin-nav__title";
    // 设置标题文本内容
    title.textContent = "插件列表";
    // 将标题元素添加到导航栏容器中
    nav.appendChild(title);

    // 创建插件按钮列表的容器div
    const list = document.createElement("div");
    // 设置列表容器的CSS类名
    list.className = "plugin-nav__list";

    // 遍历插件注册表中的每个插件
    registry.forEach(({ id }) => {
        // 为每个插件创建一个按钮元素
        const btn = document.createElement("button");
        // 设置按钮类型为button，避免在表单中意外提交
        btn.type = "button";
        // 在按钮的dataset中存储插件ID，便于后续识别和选择
        btn.dataset.pluginId = id;
        // 设置按钮的CSS类名
        btn.className = "plugin-nav__btn";
        // 设置按钮显示的文本内容为插件ID
        btn.textContent = id;
        // 为按钮添加点击事件监听器
        btn.addEventListener("click", () => activatePlugin(id));
        // 将创建好的按钮添加到列表容器中
        list.appendChild(btn);
    });

    // 将包含所有插件按钮的列表容器添加到导航栏中
    nav.appendChild(list);
    // 调用高亮函数，确保当前激活的插件按钮有视觉区分
    highlightActiveNav();
}

/**
 * 导航栏高亮状态<br/>
 * 通过添加/移除CSS类来视觉区分当前选中的插件。
 */
function highlightActiveNav() {
    // 获取导航栏内所有插件按钮元素
    document.querySelectorAll("#plugins-nav .plugin-nav__btn").forEach((btn) => {
        // 为每个按钮切换激活状态的CSS类
        btn.classList.toggle("plugin-nav__btn--active", btn.dataset.pluginId === state.activeId);
    });
}

/**
 * 插件版本是否发生变化<br/>
 * 通过比较前后两个版本的插件元数据，检测插件是否有更新。
 * @param prev 旧版本元数据
 * @param next 新版本元数据
 * @returns {boolean} 发生变化返回true，否则返回false
 */
function hasPluginChanged(prev, next) {
    // 之前没有插件，现在有了新插件
    if (!prev && next) return true;
    // 之前有插件，现在插件被移除了
    if (!next) return false;

    // 获取版本信息对象
    const prevVer = prev?.versions || {};
    const nextVer = next?.versions || {};
    // 获取前后版本中所有文件名的数组
    const prevKeys = Object.keys(prevVer);
    const nextKeys = Object.keys(nextVer);

    // 文件数量发生变化
    if (prevKeys.length !== nextKeys.length) return true;
    // 文件内容发生变化
    return nextKeys.some((key) => prevVer[key] !== nextVer[key]);
}

/**
 * 卸载指定插件<br/>
 * （导出方法）<br/>
 * 完全清理插件相关的所有DOM元素和加载状态，释放资源。
 * @param pluginId 插件ID
 */
export function unloadPlugin(pluginId) {
    // 移除该插件相关的所有DOM元素
    document.querySelectorAll(`[data-plugin="${pluginId}"]`).forEach((e) => e.remove());
    // 移除插件的主容器元素
    const container = document.getElementById("plugin-" + pluginId);
    // 确保容器存在后再移除
    if (container) container.remove();
    // 清理内存中的加载状态记录
    delete window.loadedPlugins[pluginId];
    // 记录卸载日志
    CoreAPI.log(`插件 ${pluginId} 已卸载`);
}

/**
 * 加载插件<br/>
 * （导出方法）<br/>
 * （异步方法）<br/>
 * 负责动态加载插件的CSS、HTML和JavaScript资源，并管理版本控制。
 * @param meta 插件元数据
 * @returns {Promise<void>}
 */
export async function loadPlugin(meta) {
    // 获取插件内容区域的DOM容器
    const area = getPluginArea();
    // 如果插件区域不存在，直接返回
    if (!area) return;

    // 清理插件区域，确保只显示当前激活的插件
    Array.from(area.children).forEach((child) => {
        // 只保留当前插件的容器，移除其他插件的容器
        if (child.id !== "plugin-" + meta.id) child.remove();
    });

    // 获取或创建当前插件的容器元素
    let container = document.getElementById("plugin-" + meta.id);
    if (!container) {
        // 如果容器不存在，创建新的div元素作为插件容器
        container = document.createElement("div");
        // 设置容器ID，格式为 "plugin-{插件ID}"
        container.id = "plugin-" + meta.id;
        // 将容器添加到插件区域
        area.appendChild(container);
    }

    // 设置插件资源的基础路径
    const base = "/plugins/";
    // 获取该插件已加载资源的记录，如果不存在则初始化为空对象
    const loaded = window.loadedPlugins[meta.id] || {};
    // 确保全局状态中存在该插件的加载记录
    window.loadedPlugins[meta.id] = loaded;

    // 加载CSS资源
    if (meta.css?.length) {
        // 遍历所有CSS文件
        meta.css.forEach((cssFile) => {
            // 获取当前CSS文件的版本哈希值，如果不存在则使用空字符串
            const hash = meta.versions?.[cssFile] || "";
            // 检查该CSS文件是否需要重新加载（版本变化或首次加载）
            if (loaded[cssFile] !== hash) {
                // 移除已存在的同插件同CSS文件的link标签
                document.querySelector(`link[data-plugin="${meta.id}"][href*="${cssFile}"]`)?.remove();

                // 创建新的link元素
                const link = document.createElement("link");
                // 设置rel属性为stylesheet
                link.rel = "stylesheet";
                // 添加data-plugin属性标记插件来源
                link.setAttribute("data-plugin", meta.id);
                // 设置CSS文件路径，添加版本号防止缓存
                link.href = base + cssFile + "?v=" + hash;
                // 将link标签添加到document.head中
                document.head.appendChild(link);
                // 记录该CSS文件已加载的版本
                loaded[cssFile] = hash;
            }
        });
    }

    // 加载HTML内容
    if (meta.html?.length) {
        // 假设每个插件只有一个HTML文件，取第一个
        const htmlFile = meta.html[0];
        // 获取HTML文件的版本哈希值
        const hash = meta.versions?.[htmlFile] || "";
        // 检查HTML文件是否需要重新加载
        if (loaded[htmlFile] !== hash) {
            // 使用fetch API获取HTML文件内容
            const html = await fetch(base + htmlFile + "?v=" + hash).then((r) => r.text());
            // 将获取的HTML内容设置到插件容器中
            container.innerHTML = html;
            // 记录HTML文件已加载的版本
            loaded[htmlFile] = hash;
        }
    } else {
        // 如果没有HTML文件，清空容器内容
        container.innerHTML = "";
    }

// 加载JavaScript资源
    if (meta.js?.length) {
        // 使用for...of循环保证JS文件按顺序加载
        for (const jsFile of meta.js) {
            // 获取JS文件的版本哈希值
            const hash = meta.versions?.[jsFile] || "";
            // 检查JS文件是否需要重新加载
            if (loaded[jsFile] !== hash) {
                // 移除已存在的同插件同JS文件的script标签
                document.querySelector(`script[data-plugin="${meta.id}"][src*="${jsFile}"]`)?.remove();
                // 使用Promise包装脚本加载，确保顺序执行
                await new Promise((resolve, reject) => {
                    // 创建新的script元素
                    const script = document.createElement("script");
                    // 设置JS文件路径，添加版本号
                    script.src = base + jsFile + "?v=" + hash;
                    // 添加data-plugin属性标记插件来源
                    script.setAttribute("data-plugin", meta.id);
                    // 脚本加载成功时解析Promise
                    script.onload = resolve;
                    // 脚本加载失败时拒绝Promise
                    script.onerror = reject;
                    // 将script标签添加到document.body中
                    document.body.appendChild(script);
                });
                // 记录JS文件已加载的版本
                loaded[jsFile] = hash;
            }
        }
    }

    // 记录插件加载成功的日志
    CoreAPI.log(`插件 ${meta.id} 已加载/刷新`);
}

/**
 * 激活（渲染）插件<br/>
 * （导出方法）<br/>
 * （异步方法）<br/>
 * 这是插件系统的核心入口函数，负责管理插件的激活、切换和错误处理。
 * @param pluginId 要激活的插件ID
 * @param forceReload 是否强制重新加载插件，默认false
 * @returns {Promise<void>}
 */
export async function activatePlugin(pluginId, { forceReload = false } = {}) {
    // 从全局状态中获取插件的元数据
    const meta = state.metaMap[pluginId];
    // 如果插件元数据不存在，直接返回
    if (!meta) return;

    // 避免不必要的重复加载
    // 不是强制重新加载 / 当前已经是激活状态 / 插件容器已存在
    if (!forceReload && state.activeId === pluginId && document.getElementById("plugin-" + pluginId)) {
        // 只需要更新导航高亮状态，不需要重新加载插件
        highlightActiveNav();
        return;
    }

    // 如果当前有激活的插件，且不是要激活的同一个插件，先卸载当前插件
    // 这确保同一时间只有一个插件处于激活状态
    if (state.activeId && state.activeId !== pluginId) {
        unloadPlugin(state.activeId);
    }

    // 更新全局状态，记录当前激活的插件ID
    state.activeId = pluginId;
    // 获取插件区域容器
    const area = getPluginArea();
    // 清空插件区域内容，为加载新插件做准备
    if (area) area.innerHTML = "";

    // 使用try-catch包装插件加载过程，处理可能的加载失败
    try {
        // 异步加载插件资源
        await loadPlugin(meta);
    } catch (err) {
        // 捕获加载过程中的错误
        // 在控制台输出详细错误信息，便于调试
        console.error(`插件 ${pluginId} 加载失败:`, err.message);
        // 向用户显示友好的错误提示
        showPluginPlaceholder(`加载插件 ${pluginId} 失败，请查看日志。`);
        // 重置激活状态，因为加载失败了
        state.activeId = null;
    }

    // 更新导航栏的高亮状态，反映当前的激活状态
    highlightActiveNav();
}

/**
 * 刷新插件<br/>
 * （导出方法）<br/>
 * （异步方法）<br/>
 * 负责检测插件变化并刷新插件注册表和维护导航与激活状态。
 * @returns {Promise<void>}
 */
export async function refreshPlugins() {
    try {
        // 从服务器获取最新的插件注册表数据
        const registry = await fetchStandard("/api/plugins");
        // 构建新旧插件映射表用于比较变化
        const oldMap = Object.fromEntries(window.lastPluginRegistry.map((p) => [p.id, p]));
        const newMap = Object.fromEntries(registry.map((p) => [p.id, p]));

        // 更新全局状态中的插件元数据映射
        state.metaMap = newMap;
        // 重新渲染导航栏，反映最新的插件列表
        renderPluginNav(registry);

        // 清理被移除的插件
        // 遍历旧插件映射中的所有插件ID
        for (const id in oldMap) {
            // 检查插件是否在新映射中不存在（已被移除）
            if (!newMap[id]) {
                // 记录插件移除日志
                CoreAPI.log(`检测到插件被移除：${id}`);
                // 卸载被移除的插件，清理相关资源
                unloadPlugin(id);
                // 如果被移除的插件正好是当前激活的插件，重置激活状态
                if (state.activeId === id) {
                    state.activeId = null;
                }
            }
        }

        // 处理空插件列表的情况
        if (!registry.length) {
            // 显示空状态提示
            showPluginPlaceholder("暂无可用插件，请先在插件目录放置资源。");
            // 更新全局记录
            window.lastPluginRegistry = registry;
            // 更新导航高亮状态
            highlightActiveNav();
            return; // 提前返回，无需处理插件激活逻辑
        }

        // 如果当前没有激活的插件，默认激活列表中的第一个插件
        if (!state.activeId) {
            await activatePlugin(registry[0].id);
        }
        // 如果当前激活的插件有更新（版本变化），强制重新加载
        else if (hasPluginChanged(oldMap[state.activeId], newMap[state.activeId])) {
            CoreAPI.log(`检测到插件 ${state.activeId} 有更新，正在刷新`);
            await activatePlugin(state.activeId, { forceReload: true });
        }
        // 如果当前激活的插件没有变化，只需更新导航高亮
        else {
            highlightActiveNav();
        }

        // 保存当前插件注册表状态，用于下次比较
        window.lastPluginRegistry = registry;
    } catch (err) {
        console.error("刷新插件失败:", err.message);
    }
}
