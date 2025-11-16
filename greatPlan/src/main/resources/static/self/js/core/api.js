/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

/**
 * 标准请求体<br/>
 * （导出方法）<br/>
 * （异步方法）<br/>
 * 用于统一核心包JS的请求格式，与后端的标准视图响应对象对应。
 * @param url 请求地址
 * @param options 请求参数（请求方法，请求头，请求体）
 * @returns {Promise<*>} 异步操作完成后的任意返回值
 */
export async function fetchStandard(url, options = {}) {
    try {
        // 使用原生API打请求，阻塞等待
        const res = await fetch(url, options);
        // 解析响应为json
        const json = await res.json();
        // 检查状态，正常则返回响应数据
        if (json.code !== 200) throw new Error(json.message || "请求失败");
        return json.data;
    } catch (err) {
        // 否则喷个报错
        console.error(`[Fetch Error] ${url}:`, err.message);
        throw err;
    }
}
