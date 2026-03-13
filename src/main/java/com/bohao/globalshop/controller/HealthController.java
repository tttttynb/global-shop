package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api")
public class HealthController {
    @GetMapping("/health")
    public Result<String> checkHealth() {
        return Result.success("后端服务已成功启动！且已按照大厂规范改造完毕！");
    }
}
