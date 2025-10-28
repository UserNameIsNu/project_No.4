/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.exceptionControll;

import com.ljf.greatplan.general.tools.generalTools.FileIO;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 自定义异常切面<br/>
 * 用于发现全局的任意异常时，侵入并进行日志写入。
 */
@Aspect
@Component
@Slf4j
public class CustomExceptionAspect {
    /**
     * 文件IO工具类
     */
    private FileIO fileIO;

    /**
     * 构造器
     * @param fileIO
     */
    public CustomExceptionAspect(FileIO fileIO) {
        this.fileIO = fileIO;
    }

    /**
     * 定义切入点<br/>
     * 这个切面作用于整个程序的所有类的所有方法（除了日志写入方法）
     */
    @Pointcut("execution(* com.ljf.greatplan..*(..)) && " +
            "!execution(* com.ljf.greatplan.general.tools.generalTools.FileIO.exceptionWrite(..))")
    public void globalMethods() {}

    /**
     * 定义后置处理器<br/>
     * 在异常被抛出后执行，这里执行写入日志的操作。
     * @param e 异常实例
     */
    @AfterThrowing(pointcut = "globalMethods()", throwing = "e")
    public void handleException(Throwable e) {
        fileIO.exceptionWrite(e);
    }
}
