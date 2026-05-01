package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.LiveCreateDto;
import com.bohao.globalshop.dto.LiveProductDto;
import com.bohao.globalshop.vo.LiveRoomVo;

import java.util.List;
import java.util.Map;

public interface LiveService {
    Result<LiveRoomVo> createLiveRoom(Long userId, LiveCreateDto dto);
    Result<LiveRoomVo> startLive(Long userId, Long roomId);
    Result<String> stopLive(Long userId, Long roomId);
    Result<List<LiveRoomVo>> getLiveList(Integer status);
    Result<LiveRoomVo> getLiveDetail(Long roomId);
    Result<String> addProducts(Long userId, Long roomId, LiveProductDto dto);
    Result<String> setExplainingProduct(Long userId, Long roomId, Long productId);
    Result<String> toggleAiAssistant(Long userId, Long roomId, Boolean enabled);
    Result<Map<String, Object>> getHistoryMessages(Long roomId, Integer page, Integer size);
    Result<List<Map<String, Object>>> getLiveProducts(Long roomId);
}
