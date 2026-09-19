package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.entity.ProductTranslation;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.mapper.ProductTranslationMapper;
import com.bohao.globalshop.service.ProductTranslationService;
import com.bohao.globalshop.service.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductTranslationServiceImpl implements ProductTranslationService {

    private final ProductTranslationMapper productTranslationMapper;
    private final ProductMapper productMapper;
    private final TranslationService translationService;

    /** 自动翻译开关（沿用 personalization.enabled 同款配置模式，控制 LLM 调用成本） */
    @Value("${app.i18n.auto-translate:true}")
    private boolean autoTranslate;

    /** 翻译目标语言（直播翻译已验证 en/ja/ko/th 链路） */
    @Value("${app.i18n.target-languages:en,ja,ko,th}")
    private String targetLanguagesConfig;

    /** 描述截断长度：控制 LLM token 成本，超长描述只翻译前段 */
    private static final int MAX_DESC_LENGTH = 1500;

    @Override
    @Async
    public void translateProductAsync(Long productId) {
        if (!autoTranslate || productId == null) {
            return;
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            log.warn("商品翻译跳过：商品 {} 不存在", productId);
            return;
        }

        for (String lang : getTargetLanguages()) {
            try {
                String title = translationService.translate(product.getName(), lang);
                String description = null;
                if (product.getDescription() != null && !product.getDescription().isBlank()) {
                    String desc = product.getDescription();
                    if (desc.length() > MAX_DESC_LENGTH) {
                        desc = desc.substring(0, MAX_DESC_LENGTH);
                    }
                    description = translationService.translate(desc, lang);
                }
                if (title == null || title.isBlank()) {
                    log.warn("商品 {} [{}] 翻译结果为空，保留旧译文", productId, lang);
                    continue;
                }
                upsert(productId, lang, title, description);
                log.info("🌍 商品 {} 翻译完成 [{}]: {}", productId, lang, title);
            } catch (Exception e) {
                // 单语言失败不影响其他语言；译文缺失时前台自动回退中文
                log.error("❌ 商品 {} 翻译失败 [{}]: {}", productId, lang, e.getMessage());
            }
        }
    }

    @Override
    public ProductTranslation getTranslation(Long productId, String lang) {
        if (productId == null || lang == null || lang.isBlank() || lang.toLowerCase().startsWith("zh")) {
            return null;
        }
        return productTranslationMapper.selectOne(new QueryWrapper<ProductTranslation>()
                .eq("product_id", productId)
                .eq("lang", normalizeLang(lang))
                .last("LIMIT 1"));
    }

    @Override
    public List<ProductTranslation> listTranslations(Long productId) {
        return productTranslationMapper.selectList(new QueryWrapper<ProductTranslation>()
                .eq("product_id", productId));
    }

    @Override
    public void removeByProductId(Long productId) {
        productTranslationMapper.delete(new QueryWrapper<ProductTranslation>()
                .eq("product_id", productId));
    }

    @Override
    public List<String> getTargetLanguages() {
        return Arrays.stream(targetLanguagesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** "en-US" / "EN" → "en" */
    private String normalizeLang(String lang) {
        String l = lang.trim().toLowerCase();
        int idx = l.indexOf('-');
        return idx > 0 ? l.substring(0, idx) : l;
    }

    /** 按 (product_id, lang) 唯一键 upsert 译文 */
    private void upsert(Long productId, String lang, String title, String description) {
        ProductTranslation existing = productTranslationMapper.selectOne(new QueryWrapper<ProductTranslation>()
                .eq("product_id", productId).eq("lang", lang).last("LIMIT 1"));
        if (existing == null) {
            ProductTranslation t = new ProductTranslation();
            t.setProductId(productId);
            t.setLang(lang);
            t.setTitle(title);
            t.setDescription(description);
            productTranslationMapper.insert(t);
        } else {
            existing.setTitle(title);
            existing.setDescription(description);
            productTranslationMapper.updateById(existing);
        }
    }
}
