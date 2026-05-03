package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 控制器
 * </p>
 *
 * @author 虎哥
 */
@Api(tags = "回答或回复相关接口（管理端）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/replies")
public class InteractionReplyAdminController {
    private final IInteractionReplyService replyService;

    @GetMapping("/page")
    @ApiOperation("分页查询回答或评论")
    public PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query) {
        return replyService.queryReplyPageAdmin(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询回答或回复")
    public ReplyVO queryAdminReplyById(@PathVariable("id") Long id) {
        return replyService.queryReplyByIdAdmin(id);
    }

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("隐藏或显示回答或评论")
    public void showOrHiddenReply(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        replyService.showOrHiddenReply(id, hidden);
    }
}