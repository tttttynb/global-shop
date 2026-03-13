package com.bohao.globalshop.vo;

import com.bohao.globalshop.entity.TradeOrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVo {
    private Long id;//主订单号
    private BigDecimal totalAmount;//订单总金额
    private Integer status;//支付状态
    private LocalDateTime createTime;//下单时间
    private List<TradeOrderItem> items;//装主订单下的所有子商品明细
}
