package com.bohao.globalshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.entity.Category;
import com.bohao.globalshop.mapper.CategoryMapper;
import com.bohao.globalshop.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryMapper categoryMapper;

    @Override
    public Result<List<Map<String, Object>>> getCategoryTree() {
        QueryWrapper<Category> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_order");
        List<Category> allCategories = categoryMapper.selectList(qw);

        // 构建树形结构
        Map<Long, List<Category>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));

        List<Map<String, Object>> tree = new ArrayList<>();
        List<Category> roots = parentMap.getOrDefault(0L, Collections.emptyList());
        for (Category root : roots) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", root.getId());
            node.put("name", root.getName());
            node.put("icon", root.getIcon());
            // 构建子分类
            List<Category> children = parentMap.getOrDefault(root.getId(), Collections.emptyList());
            List<Map<String, Object>> childNodes = new ArrayList<>();
            for (Category child : children) {
                Map<String, Object> childNode = new HashMap<>();
                childNode.put("id", child.getId());
                childNode.put("name", child.getName());
                childNode.put("icon", child.getIcon());
                childNodes.add(childNode);
            }
            node.put("children", childNodes);
            tree.add(node);
        }
        return Result.success(tree);
    }
}
