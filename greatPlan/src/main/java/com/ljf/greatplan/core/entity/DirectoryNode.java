/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.entity;

import com.ljf.greatplan.core.enums.ScanStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目录节点对象<br/>
 * 继承节点对象，补充目录节点的独有字段。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DirectoryNode extends Node{
    /**
     * 是否完全扫描
     */
    private ScanStatus scanStatus;
}
