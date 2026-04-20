package com.bohao.globalshop.controller;

import com.bohao.globalshop.common.Result;
import com.bohao.globalshop.dto.AiProductAnalysisDto;
import com.bohao.globalshop.service.impl.AiMerchantServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/ai")
@RequiredArgsConstructor
public class AiMerchantController {
    private final AiMerchantServiceImpl aiMerchantService;

    @GetMapping("/analyze")
    public Result<AiProductAnalysisDto> analyze(
            @RequestParam String imageUrl,
            @RequestParam String keyword) {
        AiProductAnalysisDto dto = aiMerchantService.analyzeImage(imageUrl, keyword);
        return Result.success(dto);
    }
}
