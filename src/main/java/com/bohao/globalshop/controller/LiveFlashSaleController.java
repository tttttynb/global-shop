package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.FlashSaleCreateDto;
import com.bohao.globalshop.service.LiveFlashSaleService;
import com.bohao.globalshop.vo.FlashSaleVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 直播间闪购秒杀（Phase 2 - F3）
 * <p>
 * 主播端：发起 / 终止；观众端：抢购 / 查询进行中活动。
 * 实时进度通过直播间 WebSocket 广播（type=flash_sale）。
 * </p>
 */
@RestController
@RequestMapping("/api/live/flash-sale")
@RequiredArgsConstructor
public class LiveFlashSaleController {

    private final LiveFlashSaleService flashSaleService;

    /** 主播发起秒杀 */
    @PostMapping("/start")
    public Result<FlashSaleVo> start(HttpServletRequest request, @RequestBody FlashSaleCreateDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return flashSaleService.start(userId, dto);
    }

    /** 观众抢购（body 可选 {"quantity": 1}，每人限购一次） */
    @PostMapping("/{id}/buy")
    public Result<String> buy(HttpServletRequest request,
                              @PathVariable("id") Long saleId,
                              @RequestBody(required = false) Map<String, Integer> body) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Integer quantity = body != null ? body.get("quantity") : null;
        return flashSaleService.buy(userId, saleId, quantity);
    }

    /** 主播提前终止秒杀 */
    @PostMapping("/{id}/cancel")
    public Result<String> cancel(HttpServletRequest request, @PathVariable("id") Long saleId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return flashSaleService.cancel(userId, saleId);
    }

    /** 查询直播间进行中的秒杀（观众中途进入时恢复卡片） */
    @GetMapping("/active")
    public Result<FlashSaleVo> getActive(@RequestParam("roomId") Long roomId) {
        return flashSaleService.getActive(roomId);
    }
}
