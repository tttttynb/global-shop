package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.LiveCreateDto;
import com.bohao.globalshop.dto.LiveProductDto;
import com.bohao.globalshop.service.LiveService;
import com.bohao.globalshop.vo.LiveRoomVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveController {
    private final LiveService liveService;

    // 创建直播间
    @PostMapping("/create")
    public Result<LiveRoomVo> createLiveRoom(HttpServletRequest request, @RequestBody LiveCreateDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return liveService.createLiveRoom(userId, dto);
    }

    // 开始直播
    @PostMapping("/start/{id}")
    public Result<LiveRoomVo> startLive(HttpServletRequest request, @PathVariable("id") Long roomId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return liveService.startLive(userId, roomId);
    }

    // 停止直播
    @PostMapping("/stop/{id}")
    public Result<String> stopLive(HttpServletRequest request, @PathVariable("id") Long roomId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return liveService.stopLive(userId, roomId);
    }

    // 获取直播列表
    @GetMapping("/list")
    public Result<List<LiveRoomVo>> getLiveList(@RequestParam(required = false) Integer status) {
        return liveService.getLiveList(status);
    }

    // 获取直播详情
    @GetMapping("/{id}/detail")
    public Result<LiveRoomVo> getLiveDetail(@PathVariable("id") Long roomId) {
        return liveService.getLiveDetail(roomId);
    }

    // 添加直播间商品
    @PostMapping("/{id}/products")
    public Result<String> addProducts(HttpServletRequest request, @PathVariable("id") Long roomId, @RequestBody LiveProductDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return liveService.addProducts(userId, roomId, dto);
    }

    // 设置正在讲解的商品
    @PostMapping("/{id}/explaining/{productId}")
    public Result<String> setExplainingProduct(HttpServletRequest request, @PathVariable("id") Long roomId, @PathVariable("productId") Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return liveService.setExplainingProduct(userId, roomId, productId);
    }

    // 开关AI助理
    @PostMapping("/{id}/ai-assistant")
    public Result<String> toggleAiAssistant(HttpServletRequest request, @PathVariable("id") Long roomId, @RequestParam Boolean enabled) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return liveService.toggleAiAssistant(userId, roomId, enabled);
    }

    // 获取历史弹幕
    @GetMapping("/{id}/messages")
    public Result<Map<String, Object>> getHistoryMessages(
            @PathVariable("id") Long roomId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        return liveService.getHistoryMessages(roomId, page, size);
    }

    // 获取直播间商品列表（完整信息）
    @GetMapping("/{id}/products")
    public Result<List<Map<String, Object>>> getLiveProducts(@PathVariable("id") Long roomId) {
        return liveService.getLiveProducts(roomId);
    }
}
