package com.tianji.promotion.mapper;

import com.tianji.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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
    @Update({
            "<script>",
            "UPDATE coupon ",
            "SET used_num = used_num + #{num} ",
            "WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int incrUsedNum(@Param("ids") List<Long> ids,
                    @Param("num") Integer num);
}
