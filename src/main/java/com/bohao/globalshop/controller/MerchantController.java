package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.service.MerchantService;
import com.bohao.globalshop.vo.OrderVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {
    public final MerchantService merchantService;

    //申请开店接口
    @PostMapping("/shop/apply")
    public Result<String> applyShop(HttpServletRequest request, @RequestBody ShopApplyDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.applyShop(userId, dto);
    }

    // 上架商品接口
    @PostMapping("/product/publish")
    public Result<String> publishProduct(HttpServletRequest request, @RequestBody ProductPublishDto dto) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.publishProduct(userId, dto);
    }

    // 查看本店订单列表
    @GetMapping("/order/list")
    public Result<List<OrderVo>> getShopOrders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.getShopOrders(userId);
    }

    // 本店订单发货
    @PostMapping("/order/deliver/{id}")
    public Result<String> deliverOrder(HttpServletRequest request, @PathVariable("id") Long orderId) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return merchantService.deliverOrder(userId, orderId);
    }
}
