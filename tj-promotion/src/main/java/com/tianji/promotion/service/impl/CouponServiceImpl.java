package com.tianji.promotion.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    private final ICouponScopeService scopeService;
    private final IExchangeCodeService codeService;
    private final CategoryCache categoryCache;
    private final CourseClient courseClient;
    private final IUserCouponService userCouponService;
//    private final Executor clearCouponCacheExecutor;
    @Override
    public void saveCoupon(CouponFormDTO couponFormDTO) {

        Coupon coupon = BeanUtils.copyBean(couponFormDTO,Coupon.class);
        save(coupon);
        if(!coupon.getSpecific()){
            return;
        }
        Long couponId = coupon.getId();
        List<Long> scopes = couponFormDTO.getScopes();
        if(CollUtils.isEmpty(scopes)){
            throw new BadRequestException("限定范围不能为空");
        }
        List<CouponScope>list = scopes.stream()
                .map(bizId->new CouponScope().setBizId(bizId).setCouponId(couponId))
                .collect(Collectors.toList());
        scopeService.saveBatch(list);

    }


    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Integer type = query.getType();
        Integer status = query.getStatus();
        String name = query.getName();
        Page<Coupon> page = lambdaQuery().eq(type != null, Coupon::getType, type)
                .eq(status != null, Coupon::getStatus, status)
                .eq(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<Coupon>records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        List<CouponPageVO>list = BeanUtils.copyList(records,CouponPageVO.class);


        return PageDTO.of(page,list);
    }
    @Transactional
    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        Coupon coupon = getById(dto.getId());
        if(coupon==null){
            throw new BadRequestException("优惠券不存在！");
        }
        if(coupon.getStatus()!= CouponStatus.DRAFT&&coupon.getStatus()!= PAUSE){
            throw new BizIllegalException("优惠券状态错误！");
        }
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        Boolean isBegin = issueBeginTime==null||!issueBeginTime.isAfter(now);
        Coupon c = BeanUtils.copyBean(dto,Coupon.class);
        if(isBegin){
            c.setStatus(ISSUING);
            c.setIssueBeginTime(now);
        }else{
            c.setStatus(UN_ISSUE);
        }
        updateById(c);

        // 判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放
        if(coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT){
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }
    }

    @Override
    public void updateCouponById(CouponFormDTO dto, Long id) {
        Long dtoId = dto.getId();
        if ((dtoId != null && id != null && !dtoId.equals(id)) || (dtoId == null && id == null)) {
            throw new BadRequestException("参数错误");
        }
        Coupon coupon = BeanUtils.copyBean(dto,Coupon.class);
        boolean update = lambdaUpdate().eq(Coupon::getStatus, 1).update(coupon);
        if(!update){
            return;
        }
        List<Long> scopeIds = dto.getScopes();
        List<Long> ids = scopeService.lambdaQuery()
                .select(CouponScope::getId).eq(CouponScope::getCouponId, dto.getId()).list()
                .stream().map(CouponScope::getId).collect(Collectors.toList());
        scopeService.removeByIds(ids);
        // 删除成功后，并且有范围再插入
        if (CollUtils.isNotEmpty(scopeIds)) {
            List<CouponScope> lis = scopeIds.stream()
                    .map(i -> new CouponScope().setCouponId(dto.getId()).setType(1).setBizId(i))
                    .collect(Collectors.toList());
            scopeService.saveBatch(lis);
        }

    }

    @Override
    public CouponDetailVO queryCouponById(Long id) {
        // 1. 查询主表
        Coupon coupon = getById(id);
        if (coupon == null) {
            return null;
        }
        // 2. 拷贝VO
        CouponDetailVO vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        // 3. 无作用范围，直接返回空集合
        if (Boolean.FALSE.equals(coupon.getSpecific())) {
            vo.setScopes(Collections.emptyList());
            return vo;
        }
        // 4. 批量查询作用范围
        List<CouponScope> scopes = scopeService.lambdaQuery()
                .eq(CouponScope::getCouponId, coupon.getId())
                .list();
        if (CollUtil.isEmpty(scopes)) {
            vo.setScopes(Collections.emptyList());
            return vo;
        }
        // 5. 一次性分组
        Map<Integer, List<CouponScope>> typeMap = scopes.stream()
                .collect(Collectors.groupingBy(CouponScope::getType));
        // 6. 处理分类作用域
        List<CouponScopeVO> categoryScopes = Optional.ofNullable(typeMap.get(1))
                .orElse(Collections.emptyList())
                .stream()
                .map(scope -> new CouponScopeVO(scope.getBizId(), categoryCache.getNameByLv3Id(scope.getBizId())))
                .collect(Collectors.toList());
        // 7. 处理课程作用域
        List<CouponScopeVO> courseScopes = new ArrayList<>();
        List<CouponScope> courseScopesList = typeMap.get(2);
        if (CollUtil.isNotEmpty(courseScopesList)) {
            Set<Long> courseIds = courseScopesList.stream()
                    .map(CouponScope::getBizId)
                    .collect(Collectors.toSet());
            List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(courseIds);
            if (CollUtil.isNotEmpty(simpleInfoList)) {
                courseScopes = simpleInfoList.stream()
                        .map(c -> new CouponScopeVO(c.getId(), c.getName()))
                        .collect(Collectors.toList());
            }
        }
        // 8. 合并结果
        List<CouponScopeVO> finalScopes = new ArrayList<>(categoryScopes.size() + courseScopes.size());
        finalScopes.addAll(categoryScopes);
        finalScopes.addAll(courseScopes);
        vo.setScopes(finalScopes);
        return vo;
    }

    @Override
    public void deleteById(Long id) {
        if(id==null){
            throw new BadRequestException("请选择要删除的优惠券！");
        }
        Coupon coupon = getById(id);
        if(coupon==null)return;
        if (!coupon.getStatus().equals(CouponStatus.DRAFT) && !coupon.getStatus().equals(CouponStatus.PAUSE)) {
            throw new BadRequestException("只能删除未发放的优惠券");
        }
        boolean removed = removeById(id);
        if (!removed) throw new DbException("删除优惠券失败");
    }

    @Override
    public void pauseIssue(long id) {
        boolean update = this.lambdaUpdate()
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE, ISSUING)
                .set(Coupon::getStatus, PAUSE)
                .update();
//        if (update){
//            clearCouponCache(id);
//        }
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if(CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        List<Long>couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());
        List<UserCoupon>userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(UserCoupon::getCouponId,couponIds)
                .list();
        Map<Long,Long>issueMap =userCoupons.stream().collect(Collectors.groupingBy(UserCoupon::getCouponId,Collectors.counting()));
        Map<Long,Long>unuseMap =userCoupons.stream().filter(uc->uc.getStatus()== UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId,Collectors.counting()));
        List<CouponVO>list = new ArrayList<>(coupons.size());
        for(Coupon c:coupons){
            CouponVO vo = BeanUtils.copyBean(c,CouponVO.class);
            list.add(vo);
            vo.setAvailable(c.getIssueNum() < c.getTotalNum()
                    && issueMap.getOrDefault(c.getId(), 0L) < c.getUserLimit());
            vo.setReceived(unuseMap.getOrDefault(c.getId(),  0L) > 0);
        }
        return list;
    }
//    @Override
//    public void clearCouponCache(Long id) {
//        clearCouponCacheExecutor.execute(new ClearCouponCacheTask(id, stringRedisTemplate));
//    }
}
