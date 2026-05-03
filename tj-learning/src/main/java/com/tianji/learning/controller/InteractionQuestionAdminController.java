package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/questions")
@Api(tags = "互动回答相关接口（管理端）")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {
    private final IInteractionQuestionService questionService;
    @GetMapping("/page")
    @ApiOperation("管理端查询互动回答")
    public PageDTO<QuestionAdminVO>queryQuestionsPageAdmin(QuestionAdminPageQuery query){
        return questionService.queryQuestionsPageAdmin(query);
    }
    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("显示或隐藏问题")
    public void updateQuestionHidden(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden){
        questionService.updateQuestionHidden(id, hidden);
    }
    @GetMapping("/{id}")
    @ApiOperation("根据id查询问题")
    public QuestionAdminVO queryQuestionById(@PathVariable("id") Long id){
        return questionService.queryQuestionByIdAdmin(id);
    }
}
