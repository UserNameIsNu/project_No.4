/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI配置类</br>
 * 用于配置聊天模型与相关参数。
 */
@Configuration
public class AIConfig {
    /**
     * 聊天模型（SpringAI中用于指定模型的核心接口）
     */
    @Autowired
    private ChatModel chatModel;

    /**
     * 定义聊天实例</br>
     * 包括指定模型与上下文管理。
     * @return 聊天实例
     */
    @Bean
    public ChatClient chatModel(){
        // 返回聊天实例，指定聊天模型
        return ChatClient.builder(chatModel)
                // 定义默认上下文管理顾问，指定内存类型顾问，构建顾问
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                // 构建聊天实例
                .build();
    }

    /**
     * 定义上下文管理</br>
     * 顺便定义长度。
     * @return 上下文实例
     */
    @Bean
    public ChatMemory chatMemory(){
        // 返回基于内存的上下文实例
        return MessageWindowChatMemory.builder()
                // 指定上下文长度（用户与模型都算，一次问答就是2长度）
                .maxMessages(10)
                // 构建上下文实例
                .build();
    }
}
