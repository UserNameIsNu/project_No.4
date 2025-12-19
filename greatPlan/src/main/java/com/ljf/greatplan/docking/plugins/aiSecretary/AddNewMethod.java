/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.docking.plugins.aiSecretary;

import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 添加新方法。
 */
public class AddNewMethod {
    /**
     * 添加工具类</br>
     * 用于在运行时动态添加新的工具方法的方法。
     * @param abPath 类文件的绝对路径
     */
    @Tool(description = "用于在运行时动态添加新的工具方法的方法，传入目标类文件的绝对路径")
    public void addToolClass(String abPath) {
        // 加载新的工具方法
        Chat.toolMethodLoad(Chat.toolMethodProxyGenerator, Chat.fileIO, new ArrayList<>(List.of(
                Chat.getClassFromAbsolutePath(abPath)
        )));
    }
}
