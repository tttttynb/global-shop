package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.ProductPublishDto;
import com.bohao.globalshop.dto.ShopApplyDto;

public interface MerchantService {
    //申请开店
    Result<String> applyShop(Long userId, ShopApplyDto dto);

    //上架商品
    Result<String> publishProduct(Long userId, ProductPublishDto dto);
}
