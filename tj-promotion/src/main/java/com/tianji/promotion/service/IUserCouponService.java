package com.tianji.promotion.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-07
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);

    void exchangeCoupon(String code);
    void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long serialNum);

    PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query);
}
