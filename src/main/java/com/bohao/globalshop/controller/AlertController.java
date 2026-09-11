package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.AlertStatusDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 降价/补货提醒接口（Tier 2.2）
 */
@Slf4j
@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class AlertController {

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductMapper productMapper;

    // ==================== 降价提醒 ====================

    @PostMapping("/price/{productId}")
    public Result<String> subscribePriceAlert(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String key = "alert:price:" + productId;

        stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
        stringRedisTemplate.expire(key, 90, TimeUnit.DAYS);

        // 初始化上次价格（用于后续对比）
        Product product = productMapper.selectById(productId);
        if (product != null && product.getPrice() != null) {
            stringRedisTemplate.opsForValue()
                    .setIfAbsent("alert:price:last:" + productId, product.getPrice().toString());
        }

        return Result.success("已订阅降价提醒");
    }

    @DeleteMapping("/price/{productId}")
    public Result<String> unsubscribePriceAlert(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        stringRedisTemplate.opsForSet().remove("alert:price:" + productId, String.valueOf(userId));
        return Result.success("已取消降价提醒");
    }

    // ==================== 补货提醒 ====================

    @PostMapping("/restock/{productId}")
    public Result<String> subscribeRestockAlert(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        // 检查是否确实缺货
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStock() == null || product.getStock() > 0) {
            return Result.error(400, "该商品目前有库存，无需补货提醒");
        }

        stringRedisTemplate.opsForSet().add("alert:restock:" + productId, String.valueOf(userId));
        stringRedisTemplate.expire("alert:restock:" + productId, 90, TimeUnit.DAYS);
        return Result.success("已订阅补货提醒");
    }

    @DeleteMapping("/restock/{productId}")
    public Result<String> unsubscribeRestockAlert(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        stringRedisTemplate.opsForSet().remove("alert:restock:" + productId, String.valueOf(userId));
        return Result.success("已取消补货提醒");
    }

    // ==================== 状态查询 ====================

    @GetMapping("/check/{productId}")
    public Result<AlertStatusDto> checkAlertStatus(HttpServletRequest request, @PathVariable Long productId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        AlertStatusDto status = new AlertStatusDto();

        Boolean priceMember = stringRedisTemplate.opsForSet()
                .isMember("alert:price:" + productId, String.valueOf(userId));
        status.setPriceAlert(Boolean.TRUE.equals(priceMember));

        Boolean restockMember = stringRedisTemplate.opsForSet()
                .isMember("alert:restock:" + productId, String.valueOf(userId));
        status.setRestockAlert(Boolean.TRUE.equals(restockMember));

        return Result.success(status);
    }

    // ==================== 通知获取 ====================

    @GetMapping("/notifications")
    public Result<List<Map<String, String>>> getNotifications(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String key = "alert:notif:" + userId;

        Set<String> notifs = stringRedisTemplate.opsForZSet().reverseRange(key, 0, -1);
        if (notifs == null || notifs.isEmpty()) {
            return Result.success(List.of());
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (String notif : notifs) {
            Map<String, String> item = new HashMap<>();
            // 格式：type:productId:message
            int firstColon = notif.indexOf(':');
            int lastColon = notif.lastIndexOf(':');
            if (firstColon > 0 && lastColon > firstColon) {
                item.put("type", notif.substring(0, firstColon));
                item.put("productId", notif.substring(firstColon + 1, lastColon));
                item.put("message", notif.substring(lastColon + 1));
            } else {
                item.put("message", notif);
            }
            result.add(item);
        }

        // 读取后清除
        stringRedisTemplate.delete(key);

        return Result.success(result);
    }
}
