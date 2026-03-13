package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Product;
import com.bohao.globalshop.mapper.ProductMapper;
import com.bohao.globalshop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class ProductServiceImpl implements ProductService {
    @Autowired
    private ProductMapper productMapper;

    @Override
    public Result<List<Product>> getProductList() {
        //创建查询条件：只查出 status = 1 (上架中) 的商品
        QueryWrapper<Product> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1);
        // selectList 会把符合条件的商品全部查出来，放进一个大集合(List)里
        List<Product> list = productMapper.selectList(queryWrapper);
        return Result.success(list);
    }
}
