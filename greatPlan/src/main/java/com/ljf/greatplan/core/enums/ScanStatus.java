/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.enums;

/**
 * 扫描状态枚举<br/>
 * 定义节点的扫描状态，同时方便路径接续。
 */
public enum ScanStatus {
    NOT_SCANNED("未扫描"),
    PARTIAL_SCAN("部分扫描"),
    FULLY_SCANNED("完全扫描");

    private final String description;

    ScanStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
