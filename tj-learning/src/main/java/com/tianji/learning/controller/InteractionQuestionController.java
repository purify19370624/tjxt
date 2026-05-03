package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-28
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "互动问答的相关接口")
@RequiredArgsConstructor
@Slf4j
public class InteractionQuestionController {
    private final IInteractionQuestionService questionService;
    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionFormDTO){
       questionService.saveQuestion(questionFormDTO);
    }
    @ApiOperation("分页查询互动内容")
    @GetMapping("/page")
    public PageDTO<QuestionVO>queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }
    @ApiOperation("修改互动问题")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable("id") Long id,@RequestBody QuestionFormDTO dto){
        log.debug("修改问题：{}", dto);
        questionService.updateQuestion(id,dto);
    }
    @ApiOperation("根据id查询问题详细-用户端")
    @GetMapping("/{id}")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return questionService.queryQuestionById(id);
    }
    @ApiOperation("根据id删除问题")
    @DeleteMapping("/{id}")
    public void deleteQuestion(@PathVariable("id") Long id){
        questionService.deleteQuestionById(id);
    }
}
