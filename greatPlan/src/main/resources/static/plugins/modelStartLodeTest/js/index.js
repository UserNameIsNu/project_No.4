/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

document.getElementById("btn-hello").addEventListener("click", () => {
    CoreAPI.log("Hello 插件按钮被点击！");
    alert("Hello Plugin 启动成功！");
});
