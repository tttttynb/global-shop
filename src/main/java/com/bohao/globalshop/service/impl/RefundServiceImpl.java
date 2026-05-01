package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.RefundApplyDto;
import com.bohao.globalshop.entity.*;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
    private final RefundOrderMapper refundOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    @Override
    public Result<String> applyRefund(Long userId, RefundApplyDto dto) {
        TradeOrderItem orderItem = tradeOrderItemMapper.selectById(dto.getOrderItemId());
        if (orderItem == null) {
            return Result.error(404, "订单商品不存在！");
        }
        TradeOrder order = traderOrderMapper.selectById(orderItem.getOrderId());
        if (order == null || !order.getUserId().equals(userId)) {
            return Result.error(403, "无权操作此订单！");
        }
        // 只有已支付(1)、已发货(3)、已收货(4)的订单才能申请退款
        if (order.getStatus() != 1 && order.getStatus() != 3 && order.getStatus() != 4) {
            return Result.error(400, "当前订单状态不支持退款！");
        }
        // 检查是否已有进行中的退款
        QueryWrapper<RefundOrder> existQw = new QueryWrapper<>();
        existQw.eq("order_item_id", dto.getOrderItemId()).in("status", 0, 1);
        if (refundOrderMapper.selectCount(existQw) > 0) {
            return Result.error(400, "该商品已有进行中的退款申请！");
        }

        RefundOrder refund = new RefundOrder();
        refund.setOrderId(order.getId());
        refund.setOrderItemId(dto.getOrderItemId());
        refund.setUserId(userId);
        refund.setShopId(order.getShopId());
        refund.setRefundAmount(dto.getRefundAmount());
        refund.setReason(dto.getReason());
        refund.setImages(dto.getImages());
        refund.setStatus(0);
        refundOrderMapper.insert(refund);
        return Result.success("退款申请已提交，请等待商家审核！");
    }

    @Override
    public Result<List<RefundOrder>> getMyRefunds(Long userId) {
        QueryWrapper<RefundOrder> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("create_time");
        return Result.success(refundOrderMapper.selectList(qw));
    }

    @Override
    public Result<RefundOrder> getRefundDetail(Long userId, Long refundId) {
        RefundOrder refund = refundOrderMapper.selectById(refundId);
        if (refund == null) {
            return Result.error(404, "退款记录不存在！");
        }
        if (!refund.getUserId().equals(userId)) {
            // 也可能是商家查看
            Shop shop = getShopByUserId(userId);
            if (shop == null || !shop.getId().equals(refund.getShopId())) {
                return Result.error(403, "无权查看此退款记录！");
            }
        }
        return Result.success(refund);
    }

    @Override
    public Result<String> cancelRefund(Long userId, Long refundId) {
        RefundOrder refund = refundOrderMapper.selectById(refundId);
        if (refund == null || !refund.getUserId().equals(userId)) {
            return Result.error(403, "无权操作！");
        }
        if (refund.getStatus() != 0) {
            return Result.error(400, "当前退款状态不支持取消！");
        }
        refund.setStatus(4);
        refundOrderMapper.updateById(refund);
        return Result.success("退款申请已取消！");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> approveRefund(Long userId, Long refundId) {
        RefundOrder refund = refundOrderMapper.selectById(refundId);
        if (refund == null) {
            return Result.error(404, "退款记录不存在！");
        }
        Shop shop = getShopByUserId(userId);
        if (shop == null || !shop.getId().equals(refund.getShopId())) {
            return Result.error(403, "无权操作此退款！");
        }
        if (refund.getStatus() != 0) {
            return Result.error(400, "该退款已处理！");
        }
        // 同意退款：退还用户余额 + 退还库存
        refund.setStatus(3); // 直接退款成功
        refundOrderMapper.updateById(refund);
        // 退还用户余额
        User buyer = userMapper.selectById(refund.getUserId());
        if (buyer != null) {
            buyer.setBalance(buyer.getBalance().add(refund.getRefundAmount()));
            userMapper.updateById(buyer);
        }
        // 退还库存
        TradeOrderItem orderItem = tradeOrderItemMapper.selectById(refund.getOrderItemId());
        if (orderItem != null) {
            Product product = productMapper.selectById(orderItem.getProductId());
            if (product != null) {
                product.setStock(product.getStock() + orderItem.getQuantity());
                productMapper.updateById(product);
            }
        }
        return Result.success("退款审核通过，已退还用户余额！");
    }

    @Override
    public Result<String> rejectRefund(Long userId, Long refundId, String reason) {
        RefundOrder refund = refundOrderMapper.selectById(refundId);
        if (refund == null) {
            return Result.error(404, "退款记录不存在！");
        }
        Shop shop = getShopByUserId(userId);
        if (shop == null || !shop.getId().equals(refund.getShopId())) {
            return Result.error(403, "无权操作此退款！");
        }
        if (refund.getStatus() != 0) {
            return Result.error(400, "该退款已处理！");
        }
        refund.setStatus(2);
        refund.setRejectReason(reason);
        refundOrderMapper.updateById(refund);
        return Result.success("已拒绝退款申请！");
    }

    @Override
    public Result<List<RefundOrder>> getShopRefunds(Long userId) {
        Shop shop = getShopByUserId(userId);
        if (shop == null) {
            return Result.error(403, "您还没有开通店铺！");
        }
        QueryWrapper<RefundOrder> qw = new QueryWrapper<>();
        qw.eq("shop_id", shop.getId()).orderByDesc("create_time");
        return Result.success(refundOrderMapper.selectList(qw));
    }

    private Shop getShopByUserId(Long userId) {
        QueryWrapper<Shop> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        return shopMapper.selectOne(qw);
    }
}
