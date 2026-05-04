package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-04
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/likes")
@Api(tags = "点赞业务相关接口")
public class LikedRecordController {

    private final ILikedRecordService iLikedRecordService;
    @PostMapping
    @ApiOperation("点赞或者取消点赞")
    public void addLikeRecord(@Valid @RequestBody LikeRecordFormDTO likeRecordFormDTO){
        iLikedRecordService.addLikeRecord(likeRecordFormDTO);
    }
    @GetMapping("list")
    @ApiOperation("查询指定业务id的点赞状态")
    public Set<Long> isBizLiked(@RequestParam("bizIds") List<Long> bizIds){
        return iLikedRecordService.isBizLiked(bizIds);
    }
}
