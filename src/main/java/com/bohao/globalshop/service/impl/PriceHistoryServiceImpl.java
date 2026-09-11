package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.PriceHistory;
import com.bohao.globalshop.mapper.PriceHistoryMapper;
import com.bohao.globalshop.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryServiceImpl implements PriceHistoryService {

    private final PriceHistoryMapper priceHistoryMapper;

    /**
     * 异步落库，不阻塞商品发布/更新主流程
     */
    @Override
    @Async
    public void recordPrice(Long productId, BigDecimal price) {
        if (productId == null || price == null) {
            return;
        }
        try {
            // 查最近一条记录，价格没变就不重复落库
            QueryWrapper<PriceHistory> qw = new QueryWrapper<>();
            qw.eq("product_id", productId).orderByDesc("id").last("LIMIT 1");
            PriceHistory last = priceHistoryMapper.selectOne(qw);
            if (last != null && last.getPrice().compareTo(price) == 0) {
                return;
            }
            PriceHistory record = new PriceHistory();
            record.setProductId(productId);
            record.setPrice(price);
            priceHistoryMapper.insert(record);
        } catch (Exception e) {
            // 价格历史是辅助功能，失败只记日志，不影响主流程
            log.error("❌ 记录价格历史失败: productId={}, price={}", productId, price, e);
        }
    }

    @Override
    public List<PriceHistory> getHistory(Long productId, int days) {
        QueryWrapper<PriceHistory> qw = new QueryWrapper<>();
        qw.eq("product_id", productId)
                .ge("create_time", LocalDateTime.now().minusDays(days))
                .orderByAsc("create_time")
                .last("LIMIT 200");
        return priceHistoryMapper.selectList(qw);
    }
}
