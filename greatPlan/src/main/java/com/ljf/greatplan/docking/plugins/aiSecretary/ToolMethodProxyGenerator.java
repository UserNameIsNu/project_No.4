/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具方法代理生成器</br>
 * 用于代理核心包的工具方法，给其加上Tool注解用。
 * 方便喂给AI。
 */
@Component
@Slf4j
public class ToolMethodProxyGenerator {
    /**
     * 解析注释</br>
     * 用于根据传入的File对象集合，逐个解析方法的注释。
     * 可以把指定类的方法的注释搞出来。
     * 基本就是class转java，取文本，最后用工具包解析文本中的注释段。
     * @param files 需要解析的File集合
     * @return map<类名, map<方法名, 注释>>
     */
    public Map<String, Map<String, String>> parseNotes(List<File> files) {
        // 注释映射
        Map<String, Map<String, String>> notesMapping = new HashMap<>();
        // 解析器
        JavaParser javaParser = new JavaParser();
        // 遍历File集合
        for (File file : files) {
            try {
                // 试图找到.class对应的.java
                File javaFile = findJavaSourceFile(file);
                // 拿出内容
                String content = Files.readString(javaFile.toPath());
                // 解析内容，获取注释
                ParseResult<CompilationUnit> parseResult = javaParser.parse(content);
                // 保存解析结果
                Optional<CompilationUnit> optionalCu = parseResult.getResult();
                // 获取解析值
                CompilationUnit cu = optionalCu.get();
                // 获取类中的方法集
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                // 获取类名
                String pkg = cu.getPackageDeclaration()
                        .map(p -> p.getNameAsString())
                        .orElse("");
                String className = pkg + "." + cu.getType(0).getNameAsString();
                // 内映射
                Map<String, String> methodNoteMap = new HashMap<>();

                // 遍历方法集
                for (MethodDeclaration method : methods) {
                    // 获取方法名
                    String methodName = method.getNameAsString();
                    // 获取对应的方法注释（没有注释就留空）
                    String methodNotes = method.getComment()
                            .map(c -> c.getContent())
                            .orElse("");
                    // 组装值
                    methodNoteMap.put(methodName, methodNotes);
                }

                // 装填注释映射
                notesMapping.put(className, methodNoteMap);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        // 最终组装注解
        return notesMapping;
    }

    /**
     * 找.java</br>
     * 根据.class试图找对应的.java。
     * 就是改一下路径。
     * @param classFile .class地址
     * @return .java地址
     */
    private File findJavaSourceFile(File classFile) {
        // 取绝对路径
        String classPath = classFile.getAbsolutePath();
        // 若这玩意是从target里面来的
        if (classPath.contains("target\\classes")) {
            // 换成src，再换后缀，这样理应就是拼出.java源文件的位置的
            String javaPath = classPath
                    .replace("target\\classes", "src\\main\\java")
                    .replace(".class", ".java");
            return new File(javaPath);
        }
        return null;
    }

    /**
     * 字节码代理</br>
     * 用一个牛逼库，从字节码层面对类与方法进行代理。
     * 就是因为插件不能动核心包的源码，模型需要使用的工具方法又在核心包，而又必须给方法打上Tool注解模型才认。
     * 所以只能为核心包的工具方法做代理。
     * 在运行时创建虚拟代理类，模型调用代理方法，代理方法直接导向源方法执行其逻辑。
     * @param mapMap 源类与其方法和对应注释的映射表
     * @param clazzs 需要被代理的类集合
     * @return 创建好的代理类集合
     */
    public List<Class<?>> byteBuddyProxy(Map<String, Map<String, String>> mapMap, List<Class<?>> clazzs) {
        // 结果容器
        List<Class<?>> classes = new ArrayList<>();

        // 遍历源类集合
        for (Class<?> clazz : clazzs) {
            // 创建字节码操作实例
            ByteBuddy byteBuddy = new ByteBuddy();
            // 创建构建器实例，构建一个继承自指定类的类，定义类名
            DynamicType.Builder<?> builder = byteBuddy.subclass(clazz)
                    .name(clazz.getName() + "$ByteBuddyProxy");

            // 遍历源类方法集合
            for (Method method : clazz.getDeclaredMethods()) {
                // 若为静态方法就下一个（静态方法无法被重写，自然无法被代理）
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                // 若为常量也不可被重写
                if (Modifier.isFinal(method.getModifiers())) {
                    continue;
                }
                // 私有方法也是
                if (Modifier.isPrivate(method.getModifiers())) {
                    continue;
                }

                // 定义方法名
                String proxyName = method.getName() + "_Proxy";

                // 获取当前正在创建代理的类的对应的注释集
                Map<String, String> commentaryCollection = mapMap.get(clazz.getName());
                // 定义注解实例
                AnnotationDescription annotation = AnnotationDescription.Builder
                        // 注解类型
                        .ofType(Tool.class)
                        // 注解值
                        .define(
                                "description",
                                commentaryCollection.get(method.getName())
                        )
                        // 构建
                        .build();

                // 装载构建器实例
                builder = builder
                        // 定义方法名，继承源方法的返回类型，设置访问修饰
                        .defineMethod(proxyName, method.getReturnType(), Visibility.PUBLIC)
                        // 定义形参，把源方法的拉过来保持一致
                        .withParameters(method.getParameterTypes())
                        // 定义方法体，使用自定义结构，传入原方法
                        .intercept(MethodDelegation.to(new ProxyInterceptor(method)))
                        // 添加注解
                        .annotateMethod(annotation);
            }

            // 创建代理类，执行构建器实例
            Class<?> dynamicClass = builder.make()
                    // 加载到类加载器里面，使用与源类一致的类加载器，注入策略就是将这个代理类加入到现有的类加载器里
                    .load(clazz.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    // 获取加载后的代理类
                    .getLoaded();

            // 将完成构建的代理类塞进返回集合
            classes.add(dynamicClass);
        }
        // 返回所有处理好的代理类
        return classes;
    }
}
