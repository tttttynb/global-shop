package com.bohao.globalshop.service.impl;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.OrderCreateDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.TradeOrder;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.TraderOrderMapper;
import com.bohao.globalshop.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private TraderOrderMapper traderOrderMapper;
    @Autowired
    private ProductMapper productMapper;

    @Override
    @Transactional// 开启数据库事务，保证扣库存和下订单同生共死！！！
    public Result<String> createOrder(Long userId, OrderCreateDto dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            return Result.error(400, "商品不存在！");
        }
        if (product.getStock() < dto.getQuantity()) {
            return Result.error(400, "抱歉，商品库存不足！");
        }
        product.setStock(product.getStock() - dto.getQuantity());
        productMapper.updateById(product);
        //计算总价 (单价 × 数量，BigDecimal 的乘法必须用 multiply 方法)
        BigDecimal totalAmount = product.getPrice().multiply(new BigDecimal(dto.getQuantity()));
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setProductId(dto.getProductId());
        order.setQuantity(dto.getQuantity());
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        traderOrderMapper.insert(order);
        return Result.success("太棒了！订单创建成功，总共消费：" + totalAmount + " 元！");
    }
}
