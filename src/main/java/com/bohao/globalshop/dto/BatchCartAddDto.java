package com.bohao.globalshop.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量加购（Phase 4 - F10：AI 购物顾问"一键全部加购"）
 */
@Data
public class BatchCartAddDto {
    private List<CartAddDto> items;
}
