package com.bohao.globalshop.listener;

import com.bohao.globalshop.event.ProductPublishedEvent;
import com.bohao.globalshop.service.ProductTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 商品翻译事件监听器（Phase 3 - F5 多语言）
 * <p>
 * 商品发布/编辑 → ProductPublishedEvent → 异步 AI 翻译四语落库，
 * 复用 NotificationEventListener 同款事件驱动模式，不阻塞商家发布主流程。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductTranslationListener {

    private final ProductTranslationService productTranslationService;

    @Async
    @EventListener
    public void handleProductPublished(ProductPublishedEvent event) {
        log.info("🌍 收到商品{}事件，触发异步翻译: productId={}",
                event.isNewlyCreated() ? "发布" : "编辑", event.getProductId());
        productTranslationService.translateProductAsync(event.getProductId());
    }
}
