/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.enums;

/**
 * 节点类型枚举<br/>
 * 定义节点为文件还是目录。
 */
public enum NodeType {
    File("文件"),
    DIRECTORY("目录");

    private final String description;

    NodeType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
