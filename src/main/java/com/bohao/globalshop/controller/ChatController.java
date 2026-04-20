package com.bohao.globalshop.controller;

import com.bohao.globalshop.agent.CustomerServiceAgent;
import com.bohao.globalshop.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final CustomerServiceAgent agent;

    @GetMapping
    public Result<String> talk(HttpServletRequest request, @RequestParam String message) {
        Long userId = (Long) request.getAttribute("currentUserId");

        // ⚡ 参数1：给记忆库用的钥匙；参数2：告诉AI的真实身份；参数3：你说的话
        String aiReply = agent.chat(userId, userId, message);

        return Result.success(aiReply);
    }
}
