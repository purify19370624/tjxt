package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
@Api(tags = "优惠券相关接口")
public class CouponController {
    private final ICouponService couponService;
    @ApiOperation("新增优惠券接口")
    @PostMapping
    public void saveCoupon(@RequestBody @Valid CouponFormDTO couponFormDTO){
        couponService.saveCoupon(couponFormDTO);
    }
    @ApiOperation("分页查询优惠券")
    @GetMapping("/page")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
        return couponService.queryCouponByPage(query);
    }
    @ApiOperation("发放优惠券接口")
    @PutMapping("/{id}/issue")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO dto) {
        couponService.beginIssue(dto);
    }
    @ApiOperation("修改优惠券")
    @PutMapping("/{id}")
    public void updateById(@RequestBody @Valid CouponFormDTO dto, @PathVariable("id") Long id){
        couponService.updateCouponById(dto, id);
    }
    @ApiOperation("根据id查询优惠券接口")
    @GetMapping("/{id}")
    public CouponDetailVO queryCouponById(@ApiParam("优惠券id") @PathVariable("id") Long id){
        return couponService.queryCouponById(id);
    }
    @ApiOperation("删除优惠券")
    @DeleteMapping("/{id}")
    public void deleteById(@ApiParam("优惠券id") @PathVariable("id") Long id){
        couponService.deleteById(id);
    }
    @ApiOperation("停发优惠券")
    @PutMapping("/{id}/pause")
    public void pauseIssue(@ApiParam("优惠券id") @PathVariable("id") long id) {
        couponService.pauseIssue(id);
    }


}
