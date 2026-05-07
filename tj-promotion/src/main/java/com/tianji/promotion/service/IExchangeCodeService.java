package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;

import javax.validation.Valid;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCode(Coupon coupon);

    PageDTO<ExchangeCodeVO> queryExchangeCodeByPage(@Valid CodeQuery query);
}
