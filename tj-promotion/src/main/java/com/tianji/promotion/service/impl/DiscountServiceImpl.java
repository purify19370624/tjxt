package com.tianji.promotion.service.impl;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCouponDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {
    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService scopeService;
    private final Executor discountSolutionExecutor;

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourses) {

        List<Coupon> coupons = userCouponMapper.queryMyCoupons(UserContext.getUser());
        if(CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        int totalAmount = orderCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
        List<Coupon>availableCoupons  = coupons.stream().filter(c-> DiscountStrategy.getDiscount(c.getDiscountType()).canUse(totalAmount,c))
                .collect(Collectors.toList());
        if(CollUtils.isEmpty(availableCoupons)){
            return CollUtils.emptyList();
        }
        Map<Coupon,List<OrderCourseDTO>>availableCouponMap = findAvailableCoupon(availableCoupons,orderCourses);
        if(CollUtils.isEmpty(availableCouponMap)){
            return CollUtils.emptyList();
        }
        availableCoupons = new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        for (Coupon c : availableCoupons) {
            solutions.add(List.of(c));
        }
        List<CouponDiscountDTO>list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        CountDownLatch latch = new CountDownLatch(solutions.size());

        for(List<Coupon>solution:solutions){
            CompletableFuture
                    .supplyAsync(
                            ()->calculateSolutionDiscount(availableCouponMap,orderCourses,solution),
                            discountSolutionExecutor
                    ).thenAccept(dto->{
                        list.add(dto);
                        latch.countDown();
                    });
        }
        try {
            latch.await(1, TimeUnit.SECONDS);
        }catch (InterruptedException e){
            log.error("优惠方案计算被中断，{}", e.getMessage());
        }
        return findBestSolution(list);
    }

    @Override
    public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
        List<Long>userCouponIds = orderCouponDTO.getUserCouponIds();
        List<Coupon>coupons = userCouponMapper.queryCouponByUserCouponIds(userCouponIds, UserCouponStatus.UNUSED);
        if(CollUtils.isEmpty(coupons)){
            return null;
        }
        Map<Coupon,List<OrderCourseDTO>>availableCouponMap = findAvailableCoupon(coupons,orderCouponDTO.getCourseList());
        if(CollUtils.isEmpty(availableCouponMap)){
            return null;
        }
        return calculateSolutionDiscount(availableCouponMap,orderCouponDTO.getCourseList(),coupons);
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        Map<String,CouponDiscountDTO>moreDiscountMap = new HashMap<>();
        Map<Integer,CouponDiscountDTO>lessCountMap = new HashMap<>();
        for(CouponDiscountDTO solution:list){
            String ids = solution.getIds().stream().sorted(Long::compare).map(String::valueOf).collect(Collectors.joining(","));
            CouponDiscountDTO best = moreDiscountMap.get(ids);
            if(best!=null&&best.getDiscountAmount()>=solution.getDiscountAmount()){
                continue;
            }
            best = lessCountMap.get(solution.getDiscountAmount());
            int size = solution.getIds().size();
            if(size>1&&best!=null&&best.getIds().size()<=size){
                continue;
            }
            moreDiscountMap.put(ids,solution);
            lessCountMap.put(solution.getDiscountAmount(),solution);
        }
        Collection<CouponDiscountDTO>bestSolutions = CollUtils.intersection(moreDiscountMap.values(),lessCountMap.values());
        return bestSolutions.stream().sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    private CouponDiscountDTO calculateSolutionDiscount(
            Map<Coupon, List<OrderCourseDTO>> couponMap, List<OrderCourseDTO> courses, List<Coupon> solution) {
        // 1.初始化DTO
        CouponDiscountDTO dto = new CouponDiscountDTO();
        Map<Long,Integer>detialMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId,oc->0));
        dto.setDiscountDetail(detialMap);
        for(Coupon coupon:solution){
            List<OrderCourseDTO>availableCourses = couponMap.get(coupon);
            int totalAmount = availableCourses.stream().mapToInt(oc-> oc.getPrice()-detialMap.get(oc.getId())).sum();
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(!discount.canUse(totalAmount,coupon)){
                continue;
            }
            int discountAmount = discount.calculateDiscount(totalAmount,coupon);
            calculateDiscountDetails(detialMap,availableCourses,totalAmount,discountAmount);
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount+dto.getDiscountAmount());
        }
        return dto;
    }

    private void calculateDiscountDetails(Map<Long, Integer> detialMap, List<OrderCourseDTO> availableCourses, int totalAmount, int discountAmount) {
        int times = 0;
        int remainDiscount = discountAmount;
        for(OrderCourseDTO course:availableCourses){
            times++;
            int discount  = 0;
            if(times==availableCourses.size()){
                discount = remainDiscount;
            }else {
                discount = discountAmount*course.getPrice()/totalAmount;
                remainDiscount-=discount;
            }
            detialMap.put(course.getId(),discount+detialMap.get(course.getId()));
        }
   
    }

    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(List<Coupon> coupons, List<OrderCourseDTO> courses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());
        for(Coupon coupon:coupons){
            List<OrderCourseDTO>availableCourses = courses;
            if(coupon.getSpecific()){
                List<CouponScope>scopes = scopeService.lambdaQuery().eq(CouponScope::getCouponId,coupon.getId()).list();
                Set<Long>scopeIds = scopes.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                availableCourses = courses.stream()
                        .filter(c->scopeIds.contains(c.getCateId())).collect(Collectors.toList());
            }
            if(CollUtils.isEmpty(availableCourses)){
                continue;
            }
            int totalAmount = availableCourses.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if(discount.canUse(totalAmount,coupon)){
                map.put(coupon,availableCourses);
            }
        }
        return map;
    }
}
