/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import com.ljf.greatplan.docking.plugins.aiSecretary.toolMethodFromAI.AddNewMethod;
import com.ljf.greatplan.general.scanner.SpecifyDirectoryScanner;
import com.ljf.greatplan.general.tools.generalTools.DateAndTime;
import com.ljf.greatplan.general.tools.generalTools.FileIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 插件专用ClassLoader
     */
    private static final Map<Path, URLClassLoader> PLUGIN_CLASS_LOADERS = new ConcurrentHashMap<>();

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
        try {
            // 1. 标准化 & 校验路径
            Path inputPath = Paths.get(absolutePath).toAbsolutePath().normalize();

            if (!Files.exists(inputPath)) {
                throw new IllegalArgumentException("路径不存在: " + inputPath);
            }

            // 2. 如果是 .java，尝试推导对应的 .class
            Path classFile = inputPath;
            if (inputPath.toString().endsWith(".java")) {
                classFile = guessClassFileFromJava(inputPath);
            }

            if (!classFile.toString().endsWith(".class")) {
                throw new IllegalArgumentException("不是 class 文件: " + classFile);
            }

            if (!Files.exists(classFile)) {
                throw new IllegalStateException("class 文件不存在: " + classFile);
            }

            // 3. 向上回溯，定位 classes 根目录
            Path classesRoot = findClassesRoot(classFile);

            // 4. 计算类的全限定名
            String className = classesRoot
                    .relativize(classFile)
                    .toString()
                    .replace(File.separatorChar, '.')
                    .replaceAll("\\.class$", "");

            // 5. 为该 classesRoot 获取或创建 ClassLoader
            URLClassLoader pluginLoader = PLUGIN_CLASS_LOADERS.computeIfAbsent(
                    classesRoot,
                    root -> {
                        try {
                            return new URLClassLoader(
                                    new URL[]{ root.toUri().toURL() },
                                    Chat.class.getClassLoader() // 父加载器：主应用
                            );
                        } catch (Exception e) {
                            throw new RuntimeException("创建插件 ClassLoader 失败: " + root, e);
                        }
                    }
            );

            // 6. 用插件 ClassLoader 加载类
            return pluginLoader.loadClass(className);

        } catch (Exception e) {
            throw new RuntimeException("通过路径加载类失败: " + absolutePath, e);
        }
    }

    /**
     * 找.class</br>
     * 根据java文件位置，依据固定的项目目录结构，找到对应的编译后的这个java的class。
     * @param javaFile java文件位置
     * @return class文件位置
     */
    private static Path guessClassFileFromJava(Path javaFile) {
        javaFile = javaFile.toAbsolutePath().normalize();

        // 1. java 文件所在目录
        Path javaDir = javaFile.getParent();

        // 2. 向上寻找 out-classes
        Path current = javaDir;
        Path outClassesDir = null;

        while (current != null) {
            Path candidate = current.resolve("out-classes");
            if (Files.isDirectory(candidate)) {
                outClassesDir = candidate;
                break;
            }
            current = current.getParent();
        }

        if (outClassesDir == null) {
            throw new IllegalStateException(
                    "从 java 文件路径向上未找到 out-classes 目录: " + javaFile
            );
        }

        // 3. 计算 java 文件到 src/main/java 的相对路径
        Path srcMainJava = findSrcMainJava(javaFile);
        Path relative = srcMainJava.relativize(javaFile);

        // 4. 拼 class 路径
        Path classFile = outClassesDir.resolve(
                relative.toString().replace(".java", ".class")
        );

        if (!Files.exists(classFile)) {
            throw new IllegalStateException(
                    "未在 out-classes 中找到 class 文件: " + classFile
            );
        }

        return classFile;
    }

    /**
     * 找根</br>
     * 从java开始往上找到项目根。
     * @param javaFile java文件位置
     * @return 项目根位置
     */
    private static Path findSrcMainJava(Path javaFile) {
        Path current = javaFile.toAbsolutePath().normalize();
        while (current != null) {
            if (current.endsWith(
                    Paths.get("src", "main", "java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "无法定位 src/main/java 根目录: " + javaFile
        );
    }

    /**
     * 定位class目录</br>
     * 根据class翻出它的所在目录。
     * 这里就是找到自定义的插件编译输出目录。
     * @param classFile class问价位置
     * @return 编译目录位置
     */
    private static Path findClassesRoot(Path classFile) {
        Path p = classFile.getParent();

        while (p != null) {
            String dir = p.getFileName().toString();

            // 支持你现在和未来可能用到的命名
            if (dir.equals("classes")
                    || dir.equals("out-classes")
                    || dir.equals("bin")) {
                return p;
            }

            p = p.getParent();
        }

        throw new IllegalStateException("无法定位 classes 根目录: " + classFile);
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
