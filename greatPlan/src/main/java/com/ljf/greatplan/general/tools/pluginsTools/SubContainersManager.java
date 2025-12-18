/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.pluginsTools;

import com.ljf.greatplan.core.annotations.DonNotRegisterAsABean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子容器管理器<br/>
 * 用于管理插件子容器的生命周期。
 * 也就是创建和销毁。
 */
@Component
@Slf4j
public class SubContainersManager {
    /**
     * 应用程序上下文（父容器）
     */
    private final ApplicationContext parentContext;

    /**
     * 插件独立上下文（子容器）
     */
    private final Map<String, ConfigurableApplicationContext> pluginContexts = new ConcurrentHashMap<>();

    /**
     * Controller注册映射（备份映射表）
     */
    private Map<String, Set<RequestMappingInfo>> controllerRegisteredMappings =  new ConcurrentHashMap<>();

    /**
     * 构造器
     * @param parentContext 应用程序上下文（父容器）
     */
    public SubContainersManager(ApplicationContext parentContext) {
        this.parentContext = parentContext;
    }

    /**
     * 挂载子容器<br/>
     * 创建指定插件的独立子容器，并继承自父容器。
     * @param pluginName 插件名
     * @param pluginClasses 需要加入的Bean集合
     * @param pluginDir 编译输出目录
     */
    public void mountSubContainer(String pluginName, List<Class<?>> pluginClasses, File pluginDir) throws MalformedURLException {
        // 检查子容器是否已存在（虽然一般应该不会存在）
        if (pluginContexts.containsKey(pluginName)) {
            unloadSubContainer(pluginName);
        }
        // 创建子容器
        AnnotationConfigApplicationContext pluginContext = new AnnotationConfigApplicationContext();
        pluginContext.setParent(parentContext);
        // 指定输出路径（和插件编译器中的一样，变成豆子后就要进编译目录了）
        File outputDir = new File(pluginDir, "out-classes");
        if (!outputDir.exists()) outputDir.mkdirs();
        // 继承主容器的类加载器
        ClassLoader parentLoader = parentContext.getClassLoader();
        URLClassLoader pluginClassLoader = (new URLClassLoader(new URL[]{outputDir.toURI().toURL()}, parentLoader));
        pluginContext.setClassLoader(pluginClassLoader);
        // 注册插件中的Bean
        for (Class<?> clazz : pluginClasses) {
            // 若被标记就不要打成豆子
            if(clazz.isAnnotationPresent(DonNotRegisterAsABean.class)) {
                continue;
            }
            pluginContext.register(clazz);
        }
        // 启动子容器
        pluginContext.refresh();
        // 把子容器加入插件独立上下文保存（保存引用）
        pluginContexts.put(pluginName, pluginContext);
        log.info("__________创建了子容器{}", pluginName);

        // 从子容器里面抓Controller类
        Map<String, Object> controllers = pluginContext.getBeansWithAnnotation(Controller.class);
        controllers.putAll(pluginContext.getBeansWithAnnotation(RestController.class));
        log.info("__________取得了{}中，{}个请求接口", pluginName, controllers.size());

        // 遍历子容器的所有Controller
        for(Object controller: controllers.values()){
            // 注册进DispatcherServlet里面的主映射表
            RegisterControllerMap(controller, pluginName);
        }
    }

    /**
     * 卸载子容器<br/>
     * 删除指定插件的子容器，所有相关的Bean都会被一并卸载。
     * @param pluginName 插件名
     */
    public void unloadSubContainer(String pluginName) {
        // 删除插件独立上下文中的子容器
        ConfigurableApplicationContext context = pluginContexts.remove(pluginName);
        // 关闭子容器，关闭时其中的Bean都会被自动销毁
        if (context != null) {
            try {
                context.close();
                log.info("__________子容器{}已被销毁", pluginName);
            } catch (Exception e) {
                log.error("__________子容器{}销毁失败", pluginName, e);
            }
        } else {
            log.error("__________子容器{}不存在", pluginName);
        }

        // 清理DispatcherServlet主映射表
        ClearControllerMap(pluginName);
    }

    /**
     * 注册controller映射</br>
     * 将指定插件的所有controller中的所有请求接口全部拉出来。
     * 都注册到DispatcherServlet里面的主映射表。
     * 这样运行时加入的插件的请求接口也能用了。
     * @param controller 子容器
     * @param pluginName 插件名
     */
    public void RegisterControllerMap(Object controller, String pluginName) {
        // 若Controller注册映射中没有这个插件，那么新建一个位置给它
        // 否则不管，免得覆盖插件对应的controller
        // 这里是备份映射表，主映射表在DispatcherServlet里面
        controllerRegisteredMappings.computeIfAbsent(pluginName, k -> new HashSet<>());

        // DispatcherServlet里面的主映射表
        RequestMappingHandlerMapping handlerMapping = parentContext.getBean(RequestMappingHandlerMapping.class);

        // 取插件Controller中的所有方法
        Method[] methods = controller.getClass().getDeclaredMethods();
        // 遍历
        for(Method method : methods) {
            // 取出RequestMapping注解的controller方法
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            // 可能抓不到，若真的没抓到，那就抓全部类型的controller方法
            if (requestMapping == null) {
                requestMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            }
            // 若抓到controller方法了，那么就创建一个映射对象
            if(requestMapping != null) {
                RequestMappingInfo info = RequestMappingInfo.
                        paths(requestMapping.value()).
                        methods(requestMapping.method()).
                        build();

                // 加入主映射表
                handlerMapping.registerMapping(info, controller, method);
                // 加入备份映射表
                controllerRegisteredMappings.get(pluginName).add(info);
            }
        }

        log.info("__________{}已补充至DispatcherServlet主映射表", pluginName);
    }

    /**
     * 清理controller映射</br>
     * 将指定插件的所有已注册的所有controller中的所有请求接口全部从主映射表里面删掉。
     * @param pluginName 插件名
     */
    public void ClearControllerMap(String pluginName) {
        // DispatcherServlet里面的主映射表
        RequestMappingHandlerMapping handlerMapping =  parentContext.getBean(RequestMappingHandlerMapping.class);
        // 获取备份映射表中对应插件的controller映射集合
        Set<RequestMappingInfo> requestMappingInfos = controllerRegisteredMappings.get(pluginName);

        // 若没有就直接退出
        if(requestMappingInfos == null) {
            return;
        }
        // 否则遍历这个插件的映射集合
        for(RequestMappingInfo info : controllerRegisteredMappings.get(pluginName)) {
            // 在主映射表里面一个一个对着删
            handlerMapping.unregisterMapping(info);
        }

        // 清空备份映射表的对应插件的键值对
        controllerRegisteredMappings.remove(pluginName);
        log.info("__________{}已从DispatcherServlet主映射表卸载", pluginName);
    }
}
