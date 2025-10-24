/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.core.web;

import com.ljf.greatplan.core.entity.StandardViewResponseObject;
import org.springframework.http.HttpStatus;

/**
 * 基本控制器<br/>
 * 用于定义所有控制器的响应模板，使用标准视图响应对象封装响应。
 */
public class BaseController {
    /**
     * 成功<br/>
     * 定义成功的，且有返回值的响应。
     * @param data 返回值
     * @return 标准视图响应对象
     * @param <T> 任何类型的数据对象
     */
    protected <T> StandardViewResponseObject<T> success(T data) {
        StandardViewResponseObject<T> resultVO = new StandardViewResponseObject<>();
        resultVO.setCode(HttpStatus.OK.value());
        resultVO.setData(data);
        return resultVO;
    }

    /**
     * 成功<br/>
     * 定义成功的，且没有返回值的响应。
     * @return 标准视图响应对象
     * @param <T> 任何类型的数据对象
     */
    protected <T> StandardViewResponseObject<T> success() {
        StandardViewResponseObject<T> resultVO = new StandardViewResponseObject<>();
        resultVO.setCode(HttpStatus.OK.value());
        return resultVO;
    }

    /**
     * 失败<br/>
     * 定义失败的，包含错误信息与状态码的响应。
     * @param code 状态码
     * @param message 错误信息
     * @return 标准视图响应对象
     * @param <T> 任何类型的数据对象
     */
    protected <T> StandardViewResponseObject<T> error(int code, String message) {
        StandardViewResponseObject<T> resultVO = new StandardViewResponseObject<>();
        resultVO.setCode(code);
        resultVO.setMessage(message);
        return resultVO;
    }
}
