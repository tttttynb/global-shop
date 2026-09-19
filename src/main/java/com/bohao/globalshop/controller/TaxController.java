package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.TaxRule;
import com.bohao.globalshop.service.TaxCalcService;
import com.bohao.globalshop.vo.TaxEstimateVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 跨境税费计算接口（Phase 3 - F6）
 * <p>
 * 商品详情页"预估到手价 = 商品价 + 国际运费 + 跨境综合税"明细试算。
 * </p>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/tax")
public class TaxController {

    private final TaxCalcService taxCalcService;

    /**
     * 到手价试算：GET /api/tax/estimate?productId=1&skuId=2&quantity=1&destination=CN
     */
    @GetMapping("/estimate")
    public Result<TaxEstimateVo> estimate(
            @RequestParam Long productId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(defaultValue = "1") Integer quantity,
            @RequestParam(defaultValue = "CN") String destination) {
        TaxEstimateVo vo = taxCalcService.estimate(productId, skuId, quantity, destination);
        if (vo == null) {
            return Result.error(404, "商品不存在");
        }
        return Result.success(vo);
    }

    /**
     * 全部税率规则（品类 × 目的国档位一览）
     */
    @GetMapping("/rules")
    public Result<List<TaxRule>> listRules() {
        return Result.success(taxCalcService.listRules());
    }
}
