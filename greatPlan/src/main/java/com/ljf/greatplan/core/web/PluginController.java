package com.ljf.greatplan.core.web;

import com.ljf.greatplan.core.service.PluginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 插件控制器<br/>
 * 关于插件的信息的相关请求的目的地
 */
@RestController
public class PluginController {
    /**
     * 插件服务器
     */
    private final PluginService pluginService;

    /**
     * 构造器
     * @param pluginService
     */
    public PluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    /**
     * 获取插件注册表
     * @return 插件注册表信息
     */
    @GetMapping("/api/plugins")
    public Iterable<Map<String, Object>> getPlugins() {
        return pluginService.getAllPlugins();
    }
}
