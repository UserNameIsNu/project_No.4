/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import com.ljf.greatplan.core.annotations.DonNotRegisterAsABean;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bind.annotation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * 代理拦截器</br>
 * 用于增强代理方法的逻辑。
 * AOP切不到代理方法，那就给代理方法再包一层。
 */
@Slf4j
@DonNotRegisterAsABean
public class ProxyInterceptor {
    /**
     * 原始方法
     */
    private final Method original;

    /**
     * 构造器
     * @param original 原始方法
     */
    public ProxyInterceptor(Method original) {
        this.original = original;
    }

    /**
     * 拦截</br>
     * 增强原方法的自定义逻辑。
     * @param callingMethod 原始方法
     * @param proxy 二级代理实例
     * @param args 原始方法参数
     * @return 原始方法返回
     * @throws Exception 增强逻辑
     */
    // 允许方法参数和返回值的类型在运行时自动转换
    // 如方法返回String，ByteBuddy会自动转换为拦截器定义的Object
    @RuntimeType
    public Object intercept(
            // 获取拦截的原始实例，这里选择获取原始方法
            @Origin Method callingMethod,
            // 获取当前实例，就是二级代理
            @This Object proxy,
            // 获取拦截方法（也就是原始方法）的所有参数
            @AllArguments Object[] args
    ) throws Exception {
        log.info("__________进入一级代理，送由二级代理定向，指向{}方法", callingMethod.getName());
        Object result = original.invoke(proxy, args);
        log.info("__________二级代理返回：{}", result);
        return result;
    }
}
