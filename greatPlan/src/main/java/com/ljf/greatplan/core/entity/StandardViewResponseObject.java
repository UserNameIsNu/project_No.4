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
 * 标准视图响应对象<br/>
 * 用于统一所有URL接口的响应格式。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StandardViewResponseObject<T> {
    /**
     * 提示消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 状态码
     */
    private Integer code;
}
