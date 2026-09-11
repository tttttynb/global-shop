package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.service.ShipmentService;
import com.bohao.globalshop.vo.ShipmentVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 物流追踪控制器
 */
@RestController
@RequestMapping("/api/shipment")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    /**
     * 根据订单ID查询物流信息（买家/商家查看）
     */
    @GetMapping("/order/{orderId}")
    public Result<ShipmentVo> getByOrderId(HttpServletRequest request,
                                            @PathVariable Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return shipmentService.getShipmentByOrderId(userId, orderId);
    }

    /**
     * 根据运单号查询物流信息（公开接口，可用于分享）
     */
    @GetMapping("/track/{trackingNumber}")
    public Result<ShipmentVo> getByTrackingNumber(@PathVariable String trackingNumber) {
        return shipmentService.getShipmentByTrackingNumber(trackingNumber);
    }

    /**
     * 手动刷新物流轨迹（对接真实API后使用）
     */
    @PostMapping("/{id}/refresh")
    public Result<ShipmentVo> refresh(HttpServletRequest request, @PathVariable Long id) {
        return shipmentService.refreshTracking(id);
    }
}
