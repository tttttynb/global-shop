package com.bohao.globalshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bohao.globalshop.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
