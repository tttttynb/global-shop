package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.config.RabbitMqConfig;
import com.bohao.globalshop.dto.FlashSaleCreateDto;
import com.bohao.globalshop.entity.*;
import com.bohao.globalshop.mapper.*;
import com.bohao.globalshop.service.LiveFlashSaleService;
import com.bohao.globalshop.service.SkuService;
import com.bohao.globalshop.vo.FlashSaleVo;
import com.bohao.globalshop.vo.LiveMessageVo;
import com.bohao.globalshop.websocket.LiveSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveFlashSaleServiceImpl implements LiveFlashSaleService {

    /** 秒杀活动独立库存池 Key：flash:stock:{saleId} */
    private static final String FLASH_STOCK_KEY_PREFIX = "flash:stock:";
    /** 每人每场限购标记 Key：flash:bought:{saleId}:{userId} */
    private static final String FLASH_BOUGHT_KEY_PREFIX = "flash:bought:";

    private final LiveFlashSaleMapper flashSaleMapper;
    private final LiveRoomMapper liveRoomMapper;
    private final ProductMapper productMapper;
    private final TraderOrderMapper traderOrderMapper;
    private final TradeOrderItemMapper tradeOrderItemMapper;
    private final SkuService skuService;
    private final StringRedisTemplate stringRedisTemplate;
    private final LiveSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final org.springframework.data.redis.core.script.DefaultRedisScript<Long> seckillScript;

    // ==================== 主播：发起秒杀 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<FlashSaleVo> start(Long userId, FlashSaleCreateDto dto) {
        // 1. 直播间归属 + 直播中校验
        LiveRoom room = liveRoomMapper.selectById(dto.getRoomId());
        if (room == null) {
            return Result.error(404, "直播间不存在！");
        }
        if (!room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此直播间！");
        }
        if (room.getStatus() != 1) {
            return Result.error(400, "只有直播中才能发起秒杀！");
        }
        // 2. 参数校验
        if (dto.getFlashPrice() == null || dto.getFlashPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "秒杀价必须大于 0！");
        }
        int totalQty = dto.getTotalQty() != null ? dto.getTotalQty() : 0;
        if (totalQty < 1 || totalQty > 500) {
            return Result.error(400, "秒杀数量须在 1~500 之间！");
        }
        int duration = dto.getDurationMinutes() != null ? dto.getDurationMinutes() : 3;
        if (duration < 1 || duration > 60) {
            return Result.error(400, "秒杀时长须在 1~60 分钟之间！");
        }
        // 3. 一个直播间同时只允许一场进行中的秒杀
        QueryWrapper<LiveFlashSale> activeQw = new QueryWrapper<>();
        activeQw.eq("live_room_id", room.getId()).eq("status", 0).gt("end_time", LocalDateTime.now());
        if (flashSaleMapper.selectCount(activeQw) > 0) {
            return Result.error(400, "当前直播间已有进行中的秒杀，请先结束它！");
        }
        // 4. 商品 + SKU 校验
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null || product.getStatus() != 1) {
            return Result.error(400, "商品不存在或已下架！");
        }
        ProductSku sku = skuService.resolveSku(dto.getProductId(), dto.getSkuId());
        if (sku == null) {
            return Result.error(400, "商品规格不存在或已停售！");
        }
        if (dto.getFlashPrice().compareTo(sku.getPrice()) >= 0) {
            return Result.error(400, "秒杀价必须低于原价 ¥" + sku.getPrice() + "，不然观众会骂人的！");
        }
        // 5. 🔒 从 SKU 预占库存（Redis Lua + DB 双层，防超卖）
        if (!skuService.deductRedisStock(sku.getId(), totalQty)) {
            return Result.error(400, "该规格可用库存不足 " + totalQty + " 件，无法发起秒杀！");
        }
        if (!skuService.deductStock(sku.getId(), totalQty)) {
            skuService.restoreRedisStock(sku.getId(), totalQty);
            return Result.error(400, "该规格可用库存不足 " + totalQty + " 件，无法发起秒杀！");
        }
        skuService.syncProductAggregate(product.getId());
        // 6. 落库活动记录
        LiveFlashSale sale = new LiveFlashSale();
        sale.setLiveRoomId(room.getId());
        sale.setProductId(product.getId());
        sale.setSkuId(sku.getId());
        sale.setFlashPrice(dto.getFlashPrice());
        sale.setOriginalPrice(sku.getPrice());
        sale.setTotalQty(totalQty);
        sale.setRemainQty(totalQty);
        sale.setStartTime(LocalDateTime.now());
        sale.setEndTime(LocalDateTime.now().plusMinutes(duration));
        sale.setStatus(0);
        flashSaleMapper.insert(sale);
        // 7. 初始化独立库存池（活动结束后 1 小时自动清理 Key）
        String stockKey = FLASH_STOCK_KEY_PREFIX + sale.getId();
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(totalQty), Duration.ofMinutes(duration + 60L));
        // 8. WebSocket 广播：秒杀卡片弹出 + 系统弹幕造势
        FlashSaleVo vo = buildVo(sale, product, sku);
        broadcastFlashSale(room.getId(), "START", vo);
        broadcastSystemDanmu(room.getId(), "🔥 主播发起了限时秒杀：【" + product.getName() + "】秒杀价 ¥"
                + dto.getFlashPrice() + "（原价 ¥" + sku.getPrice() + "），限量 " + totalQty + " 件，" + duration + " 分钟内有效，手慢无！");
        log.info("⚡ 直播间 [{}] 发起秒杀 [{}]：商品={} SKU={} 秒杀价={} 数量={} 时长={}min",
                room.getId(), sale.getId(), product.getId(), sku.getId(), dto.getFlashPrice(), totalQty, duration);
        return Result.success(vo);
    }

    // ==================== 观众：抢购 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> buy(Long userId, Long saleId, Integer quantity) {
        int qty = quantity != null && quantity > 0 ? Math.min(quantity, 2) : 1;
        // 1. 活动有效性校验
        LiveFlashSale sale = flashSaleMapper.selectById(saleId);
        if (sale == null) {
            return Result.error(404, "秒杀活动不存在！");
        }
        LocalDateTime now = LocalDateTime.now();
        if (sale.getStatus() != 0 || now.isAfter(sale.getEndTime())) {
            return Result.error(400, "秒杀已结束，下次手速快点！");
        }
        // 2. 每人每场限购一次（Redis setIfAbsent 原子占位）
        String boughtKey = FLASH_BOUGHT_KEY_PREFIX + saleId + ":" + userId;
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(boughtKey, "1",
                Duration.between(now, sale.getEndTime()).plusHours(1));
        if (first == null || !first) {
            return Result.error(400, "每人限购一次，把机会留给其他小伙伴吧！");
        }
        // 3. 🔒 扣活动独立库存池（Redis Lua 原子预扣减，复用秒杀脚本）
        String stockKey = FLASH_STOCK_KEY_PREFIX + saleId;
        Long luaResult = stringRedisTemplate.execute(
                seckillScript,
                java.util.Collections.singletonList(stockKey),
                String.valueOf(qty));
        if (luaResult == null || luaResult == 0L) {
            stringRedisTemplate.delete(boughtKey);
            return Result.error(400, "💥 手慢了！已被抢空！");
        }
        // 4. MySQL remain_qty 原子扣减兜底
        UpdateWrapper<LiveFlashSale> uw = new UpdateWrapper<>();
        uw.eq("id", saleId).eq("status", 0).ge("remain_qty", qty)
                .setSql("remain_qty = remain_qty - " + qty);
        if (flashSaleMapper.update(null, uw) == 0) {
            stringRedisTemplate.opsForValue().increment(stockKey, qty);
            stringRedisTemplate.delete(boughtKey);
            return Result.error(400, "💥 手慢了！已被抢空！");
        }
        // 5. 创建秒杀订单（快照：秒杀价 + 规格 + 来源标记）
        Product product = productMapper.selectById(sale.getProductId());
        ProductSku sku = sale.getSkuId() != null ? skuService.resolveSku(sale.getProductId(), sale.getSkuId()) : null;
        TradeOrder order = new TradeOrder();
        order.setUserId(userId);
        order.setShopId(product != null ? product.getShopId() : null);
        order.setTotalAmount(sale.getFlashPrice().multiply(new BigDecimal(qty)));
        order.setStatus(0);
        order.setOrderSource("LIVE_FLASH");
        order.setFlashSaleId(saleId);
        traderOrderMapper.insert(order);

        TradeOrderItem item = new TradeOrderItem();
        item.setOrderId(order.getId());
        item.setProductId(sale.getProductId());
        item.setSkuId(sale.getSkuId());
        item.setSkuSpec((sku != null ? sku.getSpecText() : "默认规格") + "【直播秒杀】");
        item.setProductName(product != null ? product.getName() : "秒杀商品");
        item.setCoverImage(sku != null && sku.getImage() != null && !sku.getImage().isEmpty()
                ? sku.getImage() : (product != null ? product.getCoverImage() : null));
        item.setPrice(sale.getFlashPrice());
        item.setQuantity(qty);
        item.setTotalAmount(order.getTotalAmount());
        tradeOrderItemMapper.insert(item);

        // 6. 推入 MQ 延迟队列：超时未支付自动取消（取消时按 skuId 回补 SKU 库存，与预占闭环）
        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDER_DELAY_EXCHANGE, RabbitMqConfig.ORDER_DELAY_ROUTING_KEY, order.getId());

        // 7. 广播库存进度；抢空则提前结束活动
        LiveFlashSale latest = flashSaleMapper.selectById(saleId);
        FlashSaleVo vo = buildVo(latest, product, sku);
        broadcastFlashSale(sale.getLiveRoomId(), "STOCK_UPDATE", vo);
        if (latest.getRemainQty() <= 0) {
            latest.setStatus(1);
            flashSaleMapper.updateById(latest);
            broadcastFlashSale(sale.getLiveRoomId(), "END", vo);
            broadcastSystemDanmu(sale.getLiveRoomId(), "🎉 本场秒杀已被抢空！没抢到的宝宝关注主播，下一波福利马上来！");
        }
        log.info("⚡ 用户 [{}] 秒杀成功：活动={} 订单={} 剩余={}", userId, saleId, order.getId(), latest.getRemainQty());
        return Result.success(String.valueOf(order.getId()));
    }

    // ==================== 主播：提前终止 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancel(Long userId, Long saleId) {
        LiveFlashSale sale = flashSaleMapper.selectById(saleId);
        if (sale == null) {
            return Result.error(404, "秒杀活动不存在！");
        }
        LiveRoom room = liveRoomMapper.selectById(sale.getLiveRoomId());
        if (room == null || !room.getUserId().equals(userId)) {
            return Result.error(403, "您没有权限操作此秒杀！");
        }
        if (sale.getStatus() != 0) {
            return Result.error(400, "该秒杀已结束，无需终止！");
        }
        restoreAndFinish(sale, 2);
        FlashSaleVo vo = buildVo(sale, productMapper.selectById(sale.getProductId()), null);
        broadcastFlashSale(room.getId(), "END", vo);
        broadcastSystemDanmu(room.getId(), "📢 主播终止了本场秒杀，未售出的库存已归还商城。");
        return Result.success("秒杀已终止，剩余 " + sale.getRemainQty() + " 件库存已回补！");
    }

    // ==================== 观众：查询进行中活动 ====================

    @Override
    public Result<FlashSaleVo> getActive(Long roomId) {
        QueryWrapper<LiveFlashSale> qw = new QueryWrapper<>();
        qw.eq("live_room_id", roomId).eq("status", 0).gt("end_time", LocalDateTime.now())
                .orderByDesc("id").last("LIMIT 1");
        LiveFlashSale sale = flashSaleMapper.selectOne(qw);
        if (sale == null) {
            return Result.success(null);
        }
        Product product = productMapper.selectById(sale.getProductId());
        ProductSku sku = sale.getSkuId() != null ? skuService.resolveSku(sale.getProductId(), sale.getSkuId()) : null;
        return Result.success(buildVo(sale, product, sku));
    }

    // ==================== 定时任务：过期收尾 ====================

    @Override
    public void finishExpired() {
        QueryWrapper<LiveFlashSale> qw = new QueryWrapper<>();
        qw.eq("status", 0).le("end_time", LocalDateTime.now());
        List<LiveFlashSale> expired = flashSaleMapper.selectList(qw);
        for (LiveFlashSale sale : expired) {
            try {
                restoreAndFinish(sale, 1);
                FlashSaleVo vo = buildVo(sale, productMapper.selectById(sale.getProductId()), null);
                broadcastFlashSale(sale.getLiveRoomId(), "END", vo);
                broadcastSystemDanmu(sale.getLiveRoomId(), "⏰ 本场秒杀时间到！共售出 "
                        + (sale.getTotalQty() - sale.getRemainQty()) + " 件，感谢大家支持！");
                log.info("⏰ 秒杀活动 [{}] 已到期结束，回补库存 {} 件", sale.getId(), sale.getRemainQty());
            } catch (Exception e) {
                log.error("❌ 秒杀活动 [{}] 过期收尾失败", sale.getId(), e);
            }
        }
    }

    // ==================== 私有辅助 ====================

    /** 回补剩余库存到 SKU 并置终态（先原子置终态，成功者才有资格回补，防止并发重复回补） */
    private void restoreAndFinish(LiveFlashSale sale, int finalStatus) {
        UpdateWrapper<LiveFlashSale> uw = new UpdateWrapper<>();
        uw.eq("id", sale.getId()).eq("status", 0).set("status", finalStatus);
        boolean won = flashSaleMapper.update(null, uw) > 0;
        sale.setStatus(finalStatus);
        stringRedisTemplate.delete(FLASH_STOCK_KEY_PREFIX + sale.getId());
        if (won && sale.getRemainQty() != null && sale.getRemainQty() > 0 && sale.getSkuId() != null) {
            skuService.restoreStock(sale.getSkuId(), sale.getRemainQty());
            skuService.restoreRedisStock(sale.getSkuId(), sale.getRemainQty());
            skuService.syncProductAggregate(sale.getProductId());
        }
    }

    private FlashSaleVo buildVo(LiveFlashSale sale, Product product, ProductSku sku) {
        FlashSaleVo vo = new FlashSaleVo();
        vo.setId(sale.getId());
        vo.setLiveRoomId(sale.getLiveRoomId());
        vo.setProductId(sale.getProductId());
        vo.setSkuId(sale.getSkuId());
        vo.setProductName(product != null ? product.getName() : null);
        vo.setProductImage(sku != null && sku.getImage() != null && !sku.getImage().isEmpty()
                ? sku.getImage() : (product != null ? product.getCoverImage() : null));
        vo.setSkuSpec(sku != null ? sku.getSpecText() : null);
        vo.setFlashPrice(sale.getFlashPrice());
        vo.setOriginalPrice(sale.getOriginalPrice());
        vo.setTotalQty(sale.getTotalQty());
        vo.setRemainQty(sale.getRemainQty());
        vo.setSoldPercent(sale.getTotalQty() > 0
                ? (sale.getTotalQty() - sale.getRemainQty()) * 100 / sale.getTotalQty() : 0);
        vo.setStartTime(sale.getStartTime());
        vo.setEndTime(sale.getEndTime());
        vo.setStatus(sale.getStatus());
        return vo;
    }

    /** 广播秒杀事件：{type:"flash_sale", data:{action, sale}} */
    private void broadcastFlashSale(Long roomId, String action, FlashSaleVo sale) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("action", action);
            data.put("sale", sale);
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", "flash_sale");
            envelope.put("data", data);
            sessionManager.broadcastToRoom(roomId, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("❌ 广播秒杀事件失败: room={} action={}", roomId, action, e);
        }
    }

    /** 广播系统弹幕（type=3），复用现有弹幕信封，不落库 */
    private void broadcastSystemDanmu(Long roomId, String content) {
        try {
            LiveMessageVo msg = new LiveMessageVo();
            msg.setNickname("系统");
            msg.setContent(content);
            msg.setType(3);
            msg.setCreateTime(LocalDateTime.now());
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("type", "message");
            envelope.put("data", msg);
            sessionManager.broadcastToRoom(roomId, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("❌ 广播系统弹幕失败: room={}", roomId, e);
        }
    }
}
