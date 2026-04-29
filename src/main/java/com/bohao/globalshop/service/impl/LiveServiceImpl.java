package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.LiveCreateDto;
import com.bohao.globalshop.dto.LiveProductDto;
import com.bohao.globalshop.entity.LiveProduct;
import com.bohao.globalshop.entity.LiveRoom;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.mapper.LiveProductMapper;
import com.bohao.globalshop.mapper.LiveRoomMapper;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.service.AliyunLiveService;
import com.bohao.globalshop.service.LiveService;
import com.bohao.globalshop.vo.LiveRoomVo;
import com.bohao.globalshop.websocket.LiveSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@RequiredArgsConstructor
@Service
public class LiveServiceImpl implements LiveService {
    private final LiveRoomMapper liveRoomMapper;
    private final LiveProductMapper liveProductMapper;
    private final ShopMapper shopMapper;
    private final ProductMapper productMapper;
    private final AliyunLiveService aliyunLiveService;
    private final LiveSessionManager liveSessionManager;


    @Override
    public Result<LiveRoomVo> createLiveRoom(Long userId, LiveCreateDto dto) {
        // 1. 验证用户有店铺
        QueryWrapper<Shop> shopQw = new QueryWrapper<>();
        shopQw.eq("user_id", userId);
        Shop shop = shopMapper.selectOne(shopQw);
        if (shop == null) {
            return Result.error(403, "您还没有开通店铺，无法创建直播间！");
        }
        if (shop.getStatus() != 1) {
            return Result.error(403, "您的店铺目前处于非营业状态，无法创建直播间！");
        }

        // 2. 生成streamKey
        String streamKey = UUID.randomUUID().toString().replace("-", "");

        // 3. 创建LiveRoom记录
        LiveRoom room = new LiveRoom();
        room.setShopId(shop.getId());
        room.setUserId(userId);
        room.setTitle(dto.getTitle());
        room.setCoverImage(dto.getCoverImage());
        room.setStatus(0); // 未开始
        room.setStreamKey(streamKey);
        room.setViewerCount(0);
        room.setAiAssistantEnabled(false);
        liveRoomMapper.insert(room);

        // 4. 组装VO返回
        LiveRoomVo vo = buildLiveRoomVo(room, shop);
        return Result.success(vo);
    }

    @Override
    public Result<LiveRoomVo> startLive(Long userId, Long roomId) {
        // 1. 查询直播间
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }
        // 2. 验证权限
        if (!room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此直播间！");
        }
        // 3. 检查状态
        if (room.getStatus() == 1) {
            return Result.error(400, "直播间已经在直播中了！");
        }
        if (room.getStatus() == 2) {
            return Result.error(400, "直播间已结束，请创建新的直播间！");
        }

        // 4. 通过阿里云直播服务生成推流/拉流URL（带鉴权，24小时有效）
        String pushUrl = aliyunLiveService.generatePushUrl(room.getStreamKey());
        String pullUrl = aliyunLiveService.generatePullUrl(room.getStreamKey());

        // 5. 更新直播间
        room.setStatus(1);
        room.setPushUrl(pushUrl);
        room.setPullUrl(pullUrl);
        room.setStartTime(LocalDateTime.now());
        liveRoomMapper.updateById(room);

        // 6. 组装VO返回
        Shop shop = shopMapper.selectById(room.getShopId());
        LiveRoomVo vo = buildLiveRoomVo(room, shop);
        // 开播时返回pushUrl给主播
        vo.setPushUrl(room.getPushUrl());
        vo.setPullUrl(room.getPullUrl());
        return Result.success(vo);
    }

    @Override
    public Result<String> stopLive(Long userId, Long roomId) {
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }
        if (!room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此直播间！");
        }
        if (room.getStatus() != 1) {
            return Result.error(400, "直播间当前不在直播中！");
        }

        room.setStatus(2);
        room.setEndTime(LocalDateTime.now());
        liveRoomMapper.updateById(room);
        return Result.success("直播已结束！感谢您的直播！");
    }

    @Override
    public Result<List<LiveRoomVo>> getLiveList(Integer status) {
        QueryWrapper<LiveRoom> qw = new QueryWrapper<>();
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("create_time");
        List<LiveRoom> rooms = liveRoomMapper.selectList(qw);

        List<LiveRoomVo> voList = new ArrayList<>();
        for (LiveRoom room : rooms) {
            Shop shop = shopMapper.selectById(room.getShopId());
            LiveRoomVo vo = buildLiveRoomVo(room, shop);
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result<LiveRoomVo> getLiveDetail(Long roomId) {
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }

        Shop shop = shopMapper.selectById(room.getShopId());
        LiveRoomVo vo = buildLiveRoomVo(room, shop);

        // 查询挂载商品
        QueryWrapper<LiveProduct> lpQw = new QueryWrapper<>();
        lpQw.eq("live_room_id", roomId).orderByAsc("sort_order");
        List<LiveProduct> liveProducts = liveProductMapper.selectList(lpQw);

        List<LiveRoomVo.LiveProductVo> productVoList = new ArrayList<>();
        for (LiveProduct lp : liveProducts) {
            Product product = productMapper.selectById(lp.getProductId());
            if (product != null) {
                LiveRoomVo.LiveProductVo pVo = new LiveRoomVo.LiveProductVo();
                pVo.setId(lp.getId());
                pVo.setProductId(product.getId());
                pVo.setProductName(product.getName());
                pVo.setProductImage(product.getCoverImage());
                pVo.setPrice(product.getPrice());
                pVo.setStock(product.getStock());
                pVo.setSortOrder(lp.getSortOrder());
                pVo.setIsExplaining(lp.getIsExplaining());
                productVoList.add(pVo);
            }
        }
        vo.setProducts(productVoList);
        return Result.success(vo);
    }

    @Override
    public Result<String> addProducts(Long userId, Long roomId, LiveProductDto dto) {
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }
        if (!room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此直播间！");
        }

        // 验证商品存在且上架
        int sortOrder = 0;
        for (Long productId : dto.getProductIds()) {
            Product product = productMapper.selectById(productId);
            if (product == null) {
                return Result.error(404, "商品ID[" + productId + "]不存在！");
            }
            if (product.getStatus() != 1) {
                return Result.error(400, "商品【" + product.getName() + "】未上架，无法挂载到直播间！");
            }

            // 检查是否已挂载
            QueryWrapper<LiveProduct> existQw = new QueryWrapper<>();
            existQw.eq("live_room_id", roomId).eq("product_id", productId);
            if (liveProductMapper.selectCount(existQw) > 0) {
                continue; // 跳过已挂载的商品
            }

            LiveProduct lp = new LiveProduct();
            lp.setLiveRoomId(roomId);
            lp.setProductId(productId);
            lp.setSortOrder(sortOrder++);
            lp.setIsExplaining(false);
            liveProductMapper.insert(lp);
        }
        return Result.success("商品挂载成功！");
    }

    @Override
    public Result<String> setExplainingProduct(Long userId, Long roomId, Long productId) {
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }
        if (!room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此直播间！");
        }

        // 先清空当前讲解
        UpdateWrapper<LiveProduct> clearUw = new UpdateWrapper<>();
        clearUw.eq("live_room_id", roomId).set("is_explaining", false);
        liveProductMapper.update(null, clearUw);

        // 设置新讲解商品
        UpdateWrapper<LiveProduct> setUw = new UpdateWrapper<>();
        setUw.eq("live_room_id", roomId).eq("product_id", productId).set("is_explaining", true);
        int updated = liveProductMapper.update(null, setUw);
        if (updated == 0) {
            return Result.error(404, "该商品未挂载到此直播间！");
        }
        return Result.success("已切换讲解商品！");
    }

    @Override
    public Result<String> toggleAiAssistant(Long userId, Long roomId, Boolean enabled) {
        LiveRoom room = liveRoomMapper.selectById(roomId);
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }
        if (!room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此直播间！");
        }

        room.setAiAssistantEnabled(enabled);
        liveRoomMapper.updateById(room);
        return Result.success(enabled ? "AI助理已开启！" : "AI助理已关闭！");
    }

    // ========== 私有辅助方法 ==========

    private LiveRoomVo buildLiveRoomVo(LiveRoom room, Shop shop) {
        LiveRoomVo vo = new LiveRoomVo();
        vo.setId(room.getId());
        vo.setShopId(room.getShopId());
        vo.setShopName(shop != null ? shop.getName() : null);
        vo.setUserId(room.getUserId());
        vo.setTitle(room.getTitle());
        vo.setCoverImage(room.getCoverImage());
        vo.setStatus(room.getStatus());
        vo.setPullUrl(room.getPullUrl());
        vo.setViewerCount(liveSessionManager.getViewerCount(room.getId()));
        vo.setAiAssistantEnabled(room.getAiAssistantEnabled());
        vo.setStartTime(room.getStartTime());
        vo.setEndTime(room.getEndTime());
        return vo;
    }

}
