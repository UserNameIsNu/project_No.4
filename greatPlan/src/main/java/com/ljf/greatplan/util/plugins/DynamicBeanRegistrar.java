/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.util.plugins;

import com.ljf.greatplan.listener.plugins.PluginResourceListening;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 动态豆子注册器（弃用）<br/>
 * 用于将加入的插件中的Bean（若有需要）注册进核心包的主容器。
 * 不过注册后就算卸载插件，与其相关的Bean是不会被删除的，不过留几个幽灵豆子倒是没啥影响。
 * 且一般就是给运行时加入的插件用，核心包启动时就存在的插件会在核心包启动时一并加载管理。
 * <br/>——————<br/>
 * V0.6.23.8中弃用，并被{@link SubContainersManager}代替。
 */
@Deprecated
//@Configuration
public class DynamicBeanRegistrar {
    /**
     * 应用程序上下文
     */
//    private final ApplicationContext context;

    /**
     * 构造器
     * @param context 上下文对象
     */
//    public DynamicBeanRegistrar(ApplicationContext context) {
//        this.context = context;
//    }

    /**
     * 动态注册豆子
     * @param clazz 需要被管理的类Class对象
     */
//    public void registerBeanDynamically(Class<?> clazz) {
//        // 强制转换对象类型，要把常规上下文对象换成可以提供操作豆子能力的对象
//        ConfigurableApplicationContext configurableContext = (ConfigurableApplicationContext) context;
//        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) configurableContext.getBeanFactory();
//        // 造一个豆子对象
//        GenericBeanDefinition beanDef = new GenericBeanDefinition();
//        // 把目标对象传入，表示要把这个对象做成豆子
//        beanDef.setBeanClass(clazz);
//        // 设置装配类型，这里设置为创建这个豆子时会尝试把所需依赖按类型注入
//        beanDef.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
//        // 定义豆子名字
//        String beanName = clazz.getSimpleName();
//        // 注册这个豆子
//        registry.registerBeanDefinition(beanName, beanDef);
//    }
}
