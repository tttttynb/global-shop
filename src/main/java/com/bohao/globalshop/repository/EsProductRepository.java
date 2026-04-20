package com.bohao.globalshop.repository;

import com.bohao.globalshop.entity.EsProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface EsProductRepository extends ElasticsearchRepository<EsProduct, Long> {
    // 🚀 见证奇迹：你只需要遵循 Spring Data 的命名规范，它就能自动生成极其复杂的全文检索代码！
    // 意思是：根据 name 或者 description 进行全文搜索，并且支持分页！
    Page<EsProduct> findByNameOrDescription(String name, String description, Pageable pageable);
}
