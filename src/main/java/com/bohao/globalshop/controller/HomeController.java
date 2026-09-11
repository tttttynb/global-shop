package com.bohao.globalshop.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.common.UserContextHolder;
import com.bohao.globalshop.dto.HomeFeedDto;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.Shop;
import com.bohao.globalshop.entity.UserProfile;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ShopMapper;
import com.bohao.globalshop.service.RecommendationService;
import com.bohao.globalshop.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页个性化 Feed 接口（Tier 1.2）
 * <p>
 * 根据用户层级返回不同的首页分区和商品推荐
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final UserProfileService userProfileService;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final RecommendationService recommendationService;

    /** 每分区商品数 */
    private static final int SECTION_SIZE = 8;

    @GetMapping("/feed")
    public Result<HomeFeedDto> getFeed() {
        Long userId = UserContextHolder.getCurrentUserId();
        String tier = "NEW";

        if (userId != null) {
            UserProfile profile = userProfileService.getProfile(userId);
            tier = profile != null && profile.getUserTier() != null
                    ? profile.getUserTier() : "NEW";
        }

        HomeFeedDto feed = new HomeFeedDto();
        feed.setUserTier(tier);
        feed.setSections(buildSections(tier, userId));

        log.debug("首页 Feed: tier={}, sections={}", tier, feed.getSections().size());
        return Result.success(feed);
    }

    private List<HomeFeedDto.HomeSection> buildSections(String tier, Long userId) {
        List<HomeFeedDto.HomeSection> sections;
        switch (tier) {
            case "PREMIUM":
                sections = buildPremiumSections();
                break;
            case "MID":
                sections = buildMidSections();
                break;
            case "BUDGET":
                sections = buildBudgetSections();
                break;
            default:
                sections = buildNewUserSections();
                break;
        }

        // 🚀 Tier 2.1c: 为所有层级追加"猜你喜欢"分区
        List<HomeFeedDto.HomeSection.ProductItem> youMayLike =
                recommendationService.getYouMayLike(userId, SECTION_SIZE);
        if (!youMayLike.isEmpty()) {
            sections.add(newSection("you_may_like", "🎯 猜你喜欢",
                    "根据你的购物偏好为你推荐", "/products?sort=sales", youMayLike));
        }

        return sections;
    }

    // ==================== PREMIUM ====================

    private List<HomeFeedDto.HomeSection> buildPremiumSections() {
        List<HomeFeedDto.HomeSection> sections = new ArrayList<>();

        // 1. 新品首发 — 最新上架贵价商品
        sections.add(newSection("new_luxury", "✨ 新品首发 · 限量臻选",
                "为您精选最新上架的高端好物", "/products?sort=latest",
                queryProducts("price", false, "latest", SECTION_SIZE)));

        // 2. 高端热卖 — 高评分高价格
        sections.add(newSection("top_rated", "🏅 品质之选 · 高端热卖",
                "口碑爆棚的品质好物", "/products?sort=rating",
                queryProducts("price", false, "sales", SECTION_SIZE)));

        return sections;
    }

    // ==================== MID ====================

    private List<HomeFeedDto.HomeSection> buildMidSections() {
        List<HomeFeedDto.HomeSection> sections = new ArrayList<>();

        // 1. 品质甄选 — 中端价位热卖
        sections.add(newSection("quality_picks", "🥈 品质甄选 · 本周热门",
                "性价比与品质的完美平衡", "/products?sort=popular",
                queryProducts("mid", false, "sales", SECTION_SIZE)));

        // 2. 新品速递 — 最新上架
        sections.add(newSection("new_arrivals", "📦 新品速递",
                "刚刚上架，快人一步", "/products?sort=latest",
                queryProducts("all", false, "latest", SECTION_SIZE)));

        return sections;
    }

    // ==================== BUDGET ====================

    private List<HomeFeedDto.HomeSection> buildBudgetSections() {
        List<HomeFeedDto.HomeSection> sections = new ArrayList<>();

        // 1. 超值特惠 — 低价好物
        sections.add(newSection("budget_deals", "💰 超值特惠 · 每日精选",
                "实惠好物，省钱不省品质", "/products?sort=price_asc",
                queryProducts("budget", false, "sales", SECTION_SIZE)));

        // 2. 限时秒杀风格 — 折扣推荐
        sections.add(newSection("hot_deals", "🔥 大家都在买",
                "平台热销实惠好物", "/products?sort=sales",
                queryProducts("budget", false, "price_asc", SECTION_SIZE)));

        return sections;
    }

    // ==================== NEW ====================

    private List<HomeFeedDto.HomeSection> buildNewUserSections() {
        List<HomeFeedDto.HomeSection> sections = new ArrayList<>();

        // 1. 平台爆款 — 销量最高
        sections.add(newSection("trending", "🌟 平台爆款 · 大家都在买",
                "销量 TOP 好物，闭眼入不出错", "/products?sort=sales",
                queryProducts("all", false, "sales", SECTION_SIZE)));

        // 2. 新人必逛 — 各品类代表作
        sections.add(newSection("starter", "🎁 新人必逛 · 入门精选",
                "各品类代表作，快速了解平台", "/products?sort=latest",
                queryProducts("all", true, "latest", SECTION_SIZE)));

        return sections;
    }

    // ==================== 查询工具 ====================

    private List<HomeFeedDto.HomeSection.ProductItem> queryProducts(
            String priceLevel, boolean diverseCategories, String sort, int limit) {

        QueryWrapper<Product> qw = new QueryWrapper<>();
        qw.eq("status", 1);

        // 价格分层
        switch (priceLevel) {
            case "high":
                qw.ge("price", 300);
                break;
            case "mid":
                qw.between("price", 50, 800);
                break;
            case "budget":
                qw.le("price", 200);
                break;
            // "all" → 不加价格过滤
        }

        // 排序
        switch (sort) {
            case "price_asc":
                qw.orderByAsc("price");
                break;
            case "price_desc":
                qw.orderByDesc("price");
                break;
            case "sales":
                qw.orderByDesc("sales_count");
                break;
            case "latest":
            default:
                qw.orderByDesc("create_time");
                break;
        }

        qw.last("LIMIT " + limit);
        List<Product> products = productMapper.selectList(qw);

        // 多样化品类（从不同 categoryId 各取一个）
        if (diverseCategories && products.size() > 4) {
            Set<Long> seenCategories = new LinkedHashSet<>();
            List<Product> diverse = new ArrayList<>();
            for (Product p : products) {
                if (seenCategories.add(p.getCategoryId() != null ? p.getCategoryId() : 0L)) {
                    diverse.add(p);
                }
                if (diverse.size() >= limit) break;
            }
            // 不够的从剩余补充
            if (diverse.size() < limit) {
                for (Product p : products) {
                    if (!diverse.contains(p)) {
                        diverse.add(p);
                        if (diverse.size() >= limit) break;
                    }
                }
            }
            products = diverse;
        }

        // 批量查店铺名
        Set<Long> shopIds = products.stream()
                .map(Product::getShopId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> shopNameMap = shopIds.isEmpty() ? Map.of()
                : shopMapper.selectBatchIds(shopIds).stream()
                        .collect(Collectors.toMap(Shop::getId, Shop::getName, (a, b) -> a));

        return products.stream().map(p -> {
            HomeFeedDto.HomeSection.ProductItem item = new HomeFeedDto.HomeSection.ProductItem();
            item.setId(p.getId());
            item.setName(p.getName());
            item.setPrice(p.getPrice());
            item.setCoverImage(p.getCoverImage());
            item.setShopName(shopNameMap.getOrDefault(p.getShopId(), "平台自营"));
            item.setSalesCount(p.getSalesCount() != null ? p.getSalesCount() : 0);
            item.setTag(computeTag(p));
            return item;
        }).collect(Collectors.toList());
    }

    private String computeTag(Product p) {
        if (p.getSalesCount() != null && p.getSalesCount() > 100) return "爆款";
        if (p.getCreateTime() != null &&
                p.getCreateTime().isAfter(java.time.LocalDateTime.now().minusDays(7)))
            return "新品";
        if (p.getPrice() != null && p.getPrice().compareTo(BigDecimal.valueOf(100)) < 0) return "超值";
        return null;
    }

    private HomeFeedDto.HomeSection newSection(String key, String title, String subtitle,
                                               String link, List<HomeFeedDto.HomeSection.ProductItem> products) {
        HomeFeedDto.HomeSection section = new HomeFeedDto.HomeSection();
        section.setKey(key);
        section.setTitle(title);
        section.setSubtitle(subtitle);
        section.setLink(link);
        section.setProducts(products);
        return section;
    }
}
