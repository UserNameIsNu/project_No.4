/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import com.ljf.greatplan.general.tools.generalTools.DateAndTime;
import com.ljf.greatplan.general.tools.generalTools.FileIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天类</br>
 * 用于设置聊天参数，然后发送与AI的聊天请求。
 */
@Component
@Slf4j
public class Chat {
    /**
     * 聊天实例
     */
    private final ChatClient chatClient;

    /**
     * 工具方法代理生成器
     */
    private final ToolMethodProxyGenerator toolMethodProxyGenerator;

    /**
     * 工具实例
     */
    private Object[] toolInstances;

    /**
     * 文件IO工具类
     */
    private FileIO fileIO;

    /**
     * 首次加载标记
     */
    private Boolean fristLoad = false;

    /**
     * 构造器
     * @param chatClient 聊天实例
     * @param fileIO 文件IO工具类
     * @param toolMethodProxyGenerator 工具方法代理生成器
     */
    public Chat(ChatClient chatClient, FileIO fileIO, ToolMethodProxyGenerator toolMethodProxyGenerator) {
        this.chatClient = chatClient;
        this.toolMethodProxyGenerator = toolMethodProxyGenerator;
        this.fileIO = fileIO;
    }

    /**
     * 首次加载检查</br>
     * 用于检查工具方法是否已被初始化过了。
     */
    private void fristLoadCheck() {
        // 检查标记
        if (!fristLoad) {
            // 加载工具方法
            toolMethodLoad(toolMethodProxyGenerator, fileIO);
            fristLoad = true;
        }
    }

    /**
     * 工具方法初始化</br>
     * 用于在类加载时，提前准备好给AI看的可用工具方法表。
     * 就是有哪些方法，叫啥，合并方法注释。
     * 但死人AI只吃打了Tool注解的方法，所以只能是做一大串代理包装原工具方法了。
     * @param toolMethodProxyGenerator 工具方法代理生成器
     * @param fileIO 文件IO工具类
     */
    public void toolMethodLoad(ToolMethodProxyGenerator toolMethodProxyGenerator, FileIO fileIO) {
        // 指定AI可用的工具类
        List<Class<?>> classes = new ArrayList<>(List.of(
                DateAndTime.class,
                FileIO.class
        ));

        // 创建这些可用类的代理类，包括其中的代理方法
        List<Class<?>> newClasses = toolMethodProxyGenerator.byteBuddyProxy(
                // 获取可用类中所有方法的对应注释
                toolMethodProxyGenerator.parseNotes(fileIO.getTargetFileABPaths(classes)),
                classes
        );

        // 代理类实例集合
        List<Object> instances = new ArrayList<>();
        // 遍历创建好的代理类，因为是class，AI不吃，所以还要做成Object
        for (Class<?> proxyClass : newClasses) {
            try {
                // 获取构造器
                Constructor<?> constructor = proxyClass.getConstructor();
                // 调用创建
                Object instance = constructor.newInstance();
                // 加入集合
                instances.add(instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 传出去
        toolInstances = instances.toArray();
    }

    /**
     * 开始聊天</br>
     * 用于发送一次对话，包括一些基本配置参数。
     * @param userInput 用户输入
     * @return 模型返回
     */
    public String startChat(String userInput) {
        // 首次加载检查
        fristLoadCheck();

        // 用户输入检查（没有输入就为反思模式）
        if(userInput == null) {
            userInput =
                    """
                    请根据上一轮与用户的对话，尽可能全面详细的总结用户的喜好，习惯，事件，经验，让你做过什么事情，你怎么做的，用户满不满意等。
                    且事件本身也需要详细解释，如看了时间就记录看到几点，总结文件就记录总结了什么文件。
                    若用户明确要求记住什么，请完整记下来。
                    使用writeMemory()工具方法将总结记忆保存在记忆文件中。
                    总结的记忆需包括：
                    "user": "用户的称呼"
                    "you": "你的称呼"
                    "preferences": "用户的偏好"
                    "history": "你刚才做了什么"
                    "experiences": "你对于刚才的对话总结的经验"
                    """;
        }

        // 系统提示，合并记忆
        String system = String.format("""
                回答请尽量口语化。
                以下是你的记忆：
                    %s
                """, fileIO.getFileContent("src/main/java/com/ljf/greatplan/docking/plugins/aiSecretary/memory.txt"));

        // 返回聊天实例
        return chatClient.prompt()
                // 定义提示词
                .system(system)
                // 加入用户输入
                .user(userInput)
                // 传递可用工具方法
                .tools(toolInstances)
                // 聊天选项，OpenAI专用的一些配置
                .options(OpenAiChatOptions.builder()
                        // 对话词元数量限制
                        .maxTokens(1000)
                        // 温度（说是定义什么情感色彩？？？感觉没点毛用。0.0又不说话了，那就拉满！倒要看看你能变得多有温度）
                        .temperature(1.0)
                        // 构建
                        .build())
                // 调用
                .call()
                // 返回内容
                .content();
    }
}
