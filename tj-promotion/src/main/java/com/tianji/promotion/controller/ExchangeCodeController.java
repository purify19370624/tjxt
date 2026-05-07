package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.service.IExchangeCodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
@Api(tags = "兑换码相关接口")
@RestController
@RequestMapping("/codes")
@RequiredArgsConstructor
public class ExchangeCodeController {
    private final IExchangeCodeService exchangeCodeService;

    @GetMapping("/page")
    @ApiOperation("分页查询兑换码")
    public PageDTO<ExchangeCodeVO> queryExchangeCodeByPage(@Valid CodeQuery query) {
        return exchangeCodeService.queryExchangeCodeByPage(query);
    }
}
