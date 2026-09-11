package com.bohao.globalshop.enums;

import lombok.Getter;

/**
 * 用户消费层级枚举
 * <p>
 * 用于 AI 客服个性化推荐策略的分流判定
 */
@Getter
public enum UserTier {

    /**
     * 新用户：无任何消费记录
     */
    NEW("新用户", 0),

    /**
     * 普通用户：低消费/价格敏感型
     */
    BUDGET("普通用户", 1),

    /**
     * 中端用户：中等消费水平
     */
    MID("中端用户", 2),

    /**
     * 高端用户：高消费/VIP 客户
     */
    PREMIUM("高端用户", 3);

    private final String label;
    private final int level;

    UserTier(String label, int level) {
        this.label = label;
        this.level = level;
    }

    /**
     * 根据消费指标判定用户层级
     *
     * @param totalSpent      累计消费金额
     * @param avgOrderValue   平均客单价
     * @param recent90dSpent  近90天消费金额
     * @return 用户层级
     */
    public static UserTier classify(java.math.BigDecimal totalSpent,
                                     java.math.BigDecimal avgOrderValue,
                                     java.math.BigDecimal recent90dSpent) {
        if (totalSpent == null && avgOrderValue == null && recent90dSpent == null) {
            return NEW;
        }

        // 高端判定：近90天消费 >= $3000 或 平均客单价 >= $500 或 累计消费 >= $10000
        if (isGte(recent90dSpent, 3000) || isGte(avgOrderValue, 500) || isGte(totalSpent, 10000)) {
            return PREMIUM;
        }

        // 中端判定：近90天消费 >= $500 或 平均客单价 >= $100 或 累计消费 >= $2000
        if (isGte(recent90dSpent, 500) || isGte(avgOrderValue, 100) || isGte(totalSpent, 2000)) {
            return MID;
        }

        // 有消费记录但不满足中端条件 → 普通用户
        if (totalSpent != null && totalSpent.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return BUDGET;
        }

        return NEW;
    }

    private static boolean isGte(java.math.BigDecimal value, double threshold) {
        return value != null && value.compareTo(java.math.BigDecimal.valueOf(threshold)) >= 0;
    }
}
