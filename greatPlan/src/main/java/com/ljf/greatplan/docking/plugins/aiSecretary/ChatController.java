/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import com.ljf.greatplan.core.entity.StandardViewResponseObject;
import com.ljf.greatplan.core.web.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;


/**
 * 聊天控制器<br/>
 * 用于与AI聊天的请求点
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController extends BaseController {
    /**
     * 聊天类
     */
    private Chat chat;

    /**
     * 最近一次反思时间
     */
    private final AtomicLong lastReflectionTime = new AtomicLong(0);

    /**
     * 最小反思间隔
     */
    private static final long REFLECTION_INTERVAL = 3000; // 3秒

    /**
     * 构造器
     * @param chat 聊天类
     */
    public ChatController(Chat chat) {
        this.chat = chat;
    }

    /**
     * 进行一次聊天
     * @param str 用户输入
     * @return AI返回
     */
    @PostMapping
    public StandardViewResponseObject<String> chat(@RequestParam String str) {
        log.info("__________试图进行一次AI对话的请求");
        // 首次推理
        StandardViewResponseObject<String> success = success(chat.startChat(str));

        // 拉出当前时间与最近一次反思时间
        long currentTime = System.currentTimeMillis();
        long lastTime = lastReflectionTime.get();
        // 若小于最小反思间隔则跳过
        if (currentTime - lastTime < REFLECTION_INTERVAL) {
            log.info("__________反思线程限流：距离上次反思仅 {} 毫秒，跳过本次",
                    currentTime - lastTime);
        } else {
            // 否则进行反思
            new Think(chat).start();
        }
        return success;
    }
}
