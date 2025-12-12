/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

document.getElementById('sendBtn').addEventListener('click', function() {
    const input = document.getElementById('inputBox');
    const text = input.value.trim();

    if (!text) {
        alert('请输入内容');
        return;
    }

    // 显示发送中状态
    document.getElementById('response').innerText = '发送中...';

    // 发送POST请求
    const formData = new FormData();
    formData.append('str', text);

    fetch('/api/chat', {
        method: 'POST',
        body: formData
    })
        .then(response => response.json())
        .then(data => {
            if (data.code === 200) {
                document.getElementById('response').innerText = 'AI回复: ' + data.data;
            } else {
                document.getElementById('response').innerText = '错误: ' + data.message;
            }
            console.log(data)
        })
        .catch(error => {
            console.error('请求失败:', error);
            document.getElementById('response').innerText = '请求失败，请检查网络';
        });
});
