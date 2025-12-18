/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 反思线程</br>
 * 用于异步进行对话反思，不要堵住用户正常聊天。
 */
@Component
@Slf4j
public class Think extends Thread{
    /**
     * 聊天类
     */
    private final Chat chat;

    /**
     * 构造器
     * @param chat 聊天类
     */
    public Think(Chat chat) {
        this.chat = chat;
    }

    /**
     * 线程逻辑
     */
    @Override
    public void run() {
        // 进行反思推理
        String s = chat.startChat(null);
        log.info("__________反思结果：{}", s);
    }
}
