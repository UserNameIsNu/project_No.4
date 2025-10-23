/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件节点对象<br/>
 * 继承节点对象，补充文件节点的独有字段。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileNode extends Node{
    /**
     * 文件大小
     */
    private String size;

    /**
     * 文件类型
     */
    private String fileType;
}
