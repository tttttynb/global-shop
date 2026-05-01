package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.RefundApplyDto;
import com.bohao.globalshop.entity.RefundOrder;

import java.util.List;

public interface RefundService {
    Result<String> applyRefund(Long userId, RefundApplyDto dto);

    Result<List<RefundOrder>> getMyRefunds(Long userId);

    Result<RefundOrder> getRefundDetail(Long userId, Long refundId);

    Result<String> cancelRefund(Long userId, Long refundId);

    Result<String> approveRefund(Long userId, Long refundId);

    Result<String> rejectRefund(Long userId, Long refundId, String reason);

    Result<List<RefundOrder>> getShopRefunds(Long userId);
}
