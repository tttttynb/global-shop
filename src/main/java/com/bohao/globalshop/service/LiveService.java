package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.LiveCreateDto;
import com.bohao.globalshop.dto.LiveProductDto;
import com.bohao.globalshop.vo.LiveRoomVo;

import java.util.List;

public interface LiveService {
    Result<LiveRoomVo> createLiveRoom(Long userId, LiveCreateDto dto);
    Result<LiveRoomVo> startLive(Long userId, Long roomId);
    Result<String> stopLive(Long userId, Long roomId);
    Result<List<LiveRoomVo>> getLiveList(Integer status);
    Result<LiveRoomVo> getLiveDetail(Long roomId);
    Result<String> addProducts(Long userId, Long roomId, LiveProductDto dto);
    Result<String> setExplainingProduct(Long userId, Long roomId, Long productId);
    Result<String> toggleAiAssistant(Long userId, Long roomId, Boolean enabled);
}
