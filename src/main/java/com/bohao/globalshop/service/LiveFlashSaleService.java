package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.FlashSaleCreateDto;
import com.bohao.globalshop.vo.FlashSaleVo;

/**
 * 直播间闪购秒杀服务（Phase 2 - F3）
 * <p>
 * 库存模型（闭环一致）：
 * 活动开始 → 从 SKU 预占 totalQty（Redis Lua + DB 双层扣减）；
 * 抢购 → 扣活动独立池 flash:stock:{saleId} + remain_qty 原子扣减；
 * 结束/取消 → 未售出 remainQty 回补 SKU；
 * 秒杀订单超时取消 → 走现有 cancelSingleOrder 按 skuId 回补（预占来源，语义正确）。
 * </p>
 */
public interface LiveFlashSaleService {

    /** 主播发起秒杀（校验直播间归属+直播中，一个直播间同时只允许一场） */
    Result<FlashSaleVo> start(Long userId, FlashSaleCreateDto dto);

    /** 观众抢购（每人每场限购一次，返回新订单ID字符串） */
    Result<String> buy(Long userId, Long saleId, Integer quantity);

    /** 主播提前终止秒杀，剩余库存回补 SKU */
    Result<String> cancel(Long userId, Long saleId);

    /** 查询直播间当前进行中的秒杀（观众中途进入页面时恢复卡片） */
    Result<FlashSaleVo> getActive(Long roomId);

    /** 定时任务：结束所有过期活动并回补库存（FlashSaleExpireTask 调用） */
    void finishExpired();
}
