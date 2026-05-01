package com.bohao.globalshop.service;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Category;

import java.util.List;
import java.util.Map;

public interface CategoryService {
    Result<List<Map<String, Object>>> getCategoryTree();
}
