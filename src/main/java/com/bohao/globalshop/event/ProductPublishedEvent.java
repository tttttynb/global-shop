package com.bohao.globalshop.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 商品发布/编辑事件（Phase 3 - F5 多语言）
 * <p>
 * 商家发布或编辑商品后发布此事件，ProductTranslationListener 异步触发 AI 翻译四语（en/ja/ko/th），
 * 复用现有 event/listener 模式（同 NotificationEvent / OrderCompletedEvent），不阻塞发布主流程。
 * </p>
 */
@Getter
public class ProductPublishedEvent extends ApplicationEvent {

    /** 商品ID */
    private final Long productId;

    /** true=新发布 false=编辑更新（编辑时全量重翻，覆盖旧译文） */
    private final boolean newlyCreated;

    public ProductPublishedEvent(Object source, Long productId, boolean newlyCreated) {
        super(source);
        this.productId = productId;
        this.newlyCreated = newlyCreated;
    }
}
