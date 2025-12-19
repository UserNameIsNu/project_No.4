/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import com.ljf.greatplan.general.scanner.SpecifyDirectoryScanner;
import com.ljf.greatplan.general.tools.generalTools.DateAndTime;
import com.ljf.greatplan.general.tools.generalTools.FileIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.io.File;
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
    public static ToolMethodProxyGenerator toolMethodProxyGenerator;

    /**
     * 工具实例
     */
    public static Object[] toolInstances = new Object[0];

    /**
     * 文件IO工具类
     */
    public static FileIO fileIO;

    /**
     * 指定目录扫描器
     */
    private SpecifyDirectoryScanner scanner;

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
    public Chat(ChatClient chatClient, FileIO fileIO, ToolMethodProxyGenerator toolMethodProxyGenerator, SpecifyDirectoryScanner scanner) {
        this.chatClient = chatClient;
        this.toolMethodProxyGenerator = toolMethodProxyGenerator;
        this.fileIO = fileIO;
        this.scanner = scanner;

        // 扫描自己的目录，把自己加进节点/监听树
        File selfPath = new File("src/main/java/com/ljf/greatplan/docking/plugins/aiSecretary");
        scanner.initialScanner(selfPath.getAbsolutePath());
        // 再把AI自建工具类目录加进去
        File toolMethodPath = new File("src/main/java/com/ljf/greatplan/docking/plugins/aiSecretary/toolMethodFromAI");
        scanner.initialScanner(toolMethodPath.getAbsolutePath());
    }

    /**
     * 根据绝对路径获取class对象</br>
     * 工具方法加载只能认class对象，但添加新方法（如AI自己新加的）时只能是获取新文件的绝对路径。
     * 因为新加入的类可以被核心包自动识别并编译加载。
     * 所以就能根据绝对路径，通过改动后缀与目录前段，进到编译目录里面寻找这个新类的存在。
     * 然后就能拉出新类的class对象了。
     * @param absolutePath 目标类的绝对路径
     * @return 目标类的class对象
     */
    public static Class<?> getClassFromAbsolutePath(String absolutePath) {
        // 标准化路径
        String normalizedPath = absolutePath.replace("\\", "/");

        // 如果是 .java 文件，转换为 .class 文件路径
        if (normalizedPath.endsWith(".java")) {
            // 将 /src/main/java/ 替换为 /target/classes/
            if (normalizedPath.contains("/src/main/java/")) {
                normalizedPath = normalizedPath.replace("/src/main/java/", "/target/classes/")
                        .replace(".java", ".class");
            }
            // 或者将源文件目录替换为编译输出目录
            else if (normalizedPath.contains("/greatPlan/src/")) {
                normalizedPath = normalizedPath.replace("/greatPlan/src/", "/greatPlan/target/classes/")
                        .replace(".java", ".class");
            }
        }

        // 查找 classes 目录
        int classesIndex = normalizedPath.indexOf("/target/classes/");
        if (classesIndex == -1) {
            classesIndex = normalizedPath.indexOf("/classes/");
        }

        if (classesIndex == -1) {
            // 尝试其他可能的位置
            if (normalizedPath.contains("/bin/")) {
                classesIndex = normalizedPath.indexOf("/bin/");
                normalizedPath = normalizedPath.replace("/bin/", "/target/classes/");
            } else {
                throw new IllegalArgumentException("不是标准的类文件路径: " + absolutePath);
            }
        }

        // 提取类路径部分
        String classPath = normalizedPath.substring(classesIndex + "/target/classes/".length());

        // 移除 .class 后缀
        if (classPath.endsWith(".class")) {
            classPath = classPath.substring(0, classPath.length() - 6);
        }

        // 将路径转换为类名
        String className = classPath.replace("/", ".");

        // 使用当前线程的 ClassLoader 加载类
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("类未找到: " + className, e);
        }
    }

    /**
     * 首次加载检查</br>
     * 用于检查工具方法是否已被初始化过了。
     */
    private void fristLoadCheck() {
        // 检查标记
        if (!fristLoad) {
            // 加载工具方法
            toolMethodLoad(toolMethodProxyGenerator, fileIO, new ArrayList<>(List.of(
                    DateAndTime.class,
                    FileIO.class,
                    AddNewMethod.class
            )));
            fristLoad = true;
        }
    }

    /**
     * 工具方法加载</br>
     * 用于在类加载时，提前准备好给AI看的可用工具方法表。
     * 就是有哪些方法，叫啥，合并方法注释。
     * 但死人AI只吃打了Tool注解的方法，所以只能是做一大串代理包装原工具方法了。
     * @param toolMethodProxyGenerator 工具方法代理生成器
     * @param fileIO 文件IO工具类
     * @param targetClasses 要添加的工具类集合
     */
    public static void toolMethodLoad(ToolMethodProxyGenerator toolMethodProxyGenerator, FileIO fileIO, List<Class<?>> targetClasses) {
        // 创建这些可用类的代理类，包括其中的代理方法
        List<Class<?>> newClasses = toolMethodProxyGenerator.byteBuddyProxy(
                // 获取可用类中所有方法的对应注释
                toolMethodProxyGenerator.parseNotes(fileIO.getTargetFileABPaths(targetClasses)),
                targetClasses
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

        // 合并
        Object[] merged = new Object[toolInstances.length + instances.toArray().length];
        System.arraycopy(toolInstances, 0, merged, 0, toolInstances.length);
        System.arraycopy(instances.toArray(), 0, merged, toolInstances.length, instances.toArray().length);
        // 传出去
        toolInstances = merged;
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
