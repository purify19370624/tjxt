package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(@Valid CouponFormDTO couponFormDTO);

    PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query);

    void beginIssue(@Valid CouponIssueFormDTO dto);

    void updateCouponById(@Valid CouponFormDTO dto, Long id);

    CouponDetailVO queryCouponById(Long id);

    void deleteById(Long id);

    void pauseIssue(long id);

//    void clearCouponCache(Long id);
}
