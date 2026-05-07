package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-07
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService codeService;

    @Override
    public void receiveCoupon(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime beginTime = coupon.getIssueBeginTime();
        LocalDateTime endTime = coupon.getIssueEndTime();
        if (beginTime == null || endTime == null) {
            throw new BadRequestException("优惠券有效期未配置");
        }
        if (now.isBefore(beginTime) || now.isAfter(endTime)) {
            throw new BadRequestException("优惠券发放尚未开始或者已经结束！");
        }

        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足！");
        }

        Long userId = UserContext.getUser();

        // 校验用户领取数量（原子化）
        synchronized (userId.toString().intern()) {
            Integer count = lambdaQuery()
                    .eq(UserCoupon::getUserId, userId)
                    .eq(UserCoupon::getCouponId, couponId)
                    .count();
            if (count != null && count >= coupon.getUserLimit()) {
                throw new BadRequestException("超出领取数量！");
            }
            // 扣减库存并保存用户券
            couponMapper.incrIssueNum(coupon.getId());
            saveUserCoupon(coupon, userId);
        }
    }

    @Transactional
    @Override
    public void exchangeCoupon(String code) {
        long serialNum = CodeUtil.parseCode(code);
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);
        if(exchanged){
            throw new BizIllegalException("兑换码已经兑换过了！");

        }
        try{
            ExchangeCode exchangeCode = codeService.getById(serialNum);
            if(exchangeCode==null){
                throw new BizIllegalException("兑换码不存在！");
            }
            LocalDateTime now = LocalDateTime.now();
            if(now.isAfter(exchangeCode.getExpiredTime())){
                throw new BizIllegalException("兑换码已过期！");
            }
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            Long userId = UserContext.getUser();
            checkAndCreateUserCoupon(coupon, userId, serialNum);
        }catch (Exception e){
            codeService.updateExchangeMark(serialNum,false);
            throw e;
        }
    }




    @Transactional
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Long serialNum) {
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId,userId)
                .eq(UserCoupon::getCouponId,coupon.getId())
                .count();
        if(count != null && count >= coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量");
        }
        couponMapper.incrIssueNum(coupon.getId());
        saveUserCoupon(coupon,userId);
        if (serialNum != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    @Override
    public PageDTO<CouponVO> queryMyCouponPage(UserCouponQuery query) {
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.分页查询用户券
        Page<UserCoupon> page = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getStatus, query.getStatus())
                .page(query.toMpPage(new OrderItem("term_end_time", true)));
        List<UserCoupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 3.获取优惠券详细信息
        // 3.1.获取用户券关联的优惠券id
        Set<Long> couponIds = records.stream().map(UserCoupon::getCouponId).collect(Collectors.toSet());
        // 3.2.查询
        List<Coupon> coupons = couponMapper.selectBatchIds(couponIds);

        // 4.封装VO
        return PageDTO.of(page, BeanUtils.copyList(coupons, CouponVO.class));
    }

    private void saveUserCoupon(Coupon coupon, Long userId){
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if(termBeginTime==null){
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        save(uc);
    }
}
