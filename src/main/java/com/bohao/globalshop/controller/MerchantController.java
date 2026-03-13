package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;
import com.bohao.globalshop.service.MerchantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
