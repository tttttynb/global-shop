package com.bohao.globalshop.agent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.entity.LiveProduct;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.LiveProductMapper;
import com.bohao.globalshop.mapper.ProductMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LiveTools {

    private final LiveProductMapper liveProductMapper;
    private final ProductMapper productMapper;

    @Tool("查询直播间当前正在讲解的商品的详细信息，包括名称、价格、描述、库存")
    public String getCurrentProduct(Long roomId) {
        QueryWrapper<LiveProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("live_room_id", roomId).eq("is_explaining", true);
        LiveProduct liveProduct = liveProductMapper.selectOne(wrapper);
        if (liveProduct == null) {
            return "当前没有正在讲解的商品";
        }
        Product product = productMapper.selectById(liveProduct.getProductId());
        if (product == null) {
            return "当前没有正在讲解的商品";
        }
        return String.format("【正在讲解】%s | 价格：%.2f元 | 库存：%d件 | 简介：%s",
                product.getName(), product.getPrice(), product.getStock(), product.getDescription());
    }

    @Tool("根据关键词在直播间挂载的商品中搜索商品信息")
    public String searchProductInRoom(Long roomId, String keyword) {
        QueryWrapper<LiveProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("live_room_id", roomId);
        List<LiveProduct> liveProducts = liveProductMapper.selectList(wrapper);
        if (liveProducts.isEmpty()) {
            return "直播间暂无挂载商品";
        }
        List<Long> productIds = liveProducts.stream()
                .map(LiveProduct::getProductId)
                .collect(Collectors.toList());
        QueryWrapper<Product> productWrapper = new QueryWrapper<>();
        productWrapper.in("id", productIds)
                .and(w -> w.like("name", keyword).or().like("description", keyword));
        List<Product> products = productMapper.selectList(productWrapper);
        if (products.isEmpty()) {
            return "直播间内未找到与'" + keyword + "'相关的商品";
        }
        return products.stream()
                .map(p -> String.format("%s | 价格：%.2f元 | 库存：%d件", p.getName(), p.getPrice(), p.getStock()))
                .collect(Collectors.joining("\n"));
    }

    @Tool("查询直播间所有挂载商品的列表，包括名称和价格")
    public String getProductList(Long roomId) {
        QueryWrapper<LiveProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("live_room_id", roomId).orderByAsc("sort_order");
        List<LiveProduct> liveProducts = liveProductMapper.selectList(wrapper);
        if (liveProducts.isEmpty()) {
            return "直播间暂无挂载商品";
        }
        List<Long> productIds = liveProducts.stream()
                .map(LiveProduct::getProductId)
                .collect(Collectors.toList());
        List<Product> products = productMapper.selectBatchIds(productIds);
        if (products.isEmpty()) {
            return "直播间暂无挂载商品";
        }
        StringBuilder sb = new StringBuilder("直播间商品列表：\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(String.format("%d. %s | 价格：%.2f元", i + 1, p.getName(), p.getPrice()));
            if (i < products.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
