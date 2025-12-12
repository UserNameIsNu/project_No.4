/*
 * Copyright (c) 2025 404
 * Licensed under the MIT License.
 * See LICENSE file in the project root for license information.
 *
 */

package com.ljf.greatplan.general.tools.generalTools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期与时间工具类<br/>
 * 封装了日期与时间操作相关的方法。
 */
@Component
public class DateAndTime {
    /**
     * 获取当前时间（年月日）
     * @return 当前时间
     */
    public String getNowTime_YMD() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * 获取当前时间（年月日-时分秒）
     * @return 当前时间
     */
    public String getNowTime_HSM() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
