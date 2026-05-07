package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    @Update("UPDATE coupon SET issue_num = issue_num + 1 WHERE id = #{couponId}")
    int incrIssueNum(@Param("couponId") Long couponId);
}
