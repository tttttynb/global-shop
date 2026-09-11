package com.bohao.globalshop.service;

import com.bohao.globalshop.entity.EsProduct;
import com.bohao.globalshop.entity.UserProfile;

import java.util.List;

/**
 * 个性化搜索服务
 * <p>
 * 根据用户消费画像对搜索结果进行价格带过滤和重排序
 */
public interface PersonalizationService {

    /**
     * 对 ES 搜索结果进行个性化重排
     *
     * @param originalResults 原始搜索结果列表
     * @param userId          用户ID（null 表示未登录，用默认策略）
     * @return 重排后的结果列表
     */
    List<EsProduct> personalizeSearchResults(List<EsProduct> originalResults, Long userId);

    /**
     * 根据用户层级确定排序策略
     *
     * @param profile 用户画像
     * @return 排序策略标识: "RATING_DESC" | "VALUE_SCORE" | "PRICE_ASC" | "DEFAULT"
     */
    String getSortStrategy(UserProfile profile);

    /**
     * 根据用户消费画像构建个性化 System Prompt
     * <p>
     * 不同层级返回不同的角色定位、推荐策略和语气风格，
     * 通过 {@code @V("tierPrompt")} 注入到 {@code PersonalizedCustomerServiceAgent} 中。
     * </p>
     *
     * @param profile 用户画像
     * @return 层级对应的角色提示词
     */
    String buildTierPrompt(UserProfile profile);
}
