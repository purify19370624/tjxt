package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-28
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(QuestionFormDTO questionFormDTO);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    void updateQuestion(Long id, QuestionFormDTO dto);

    QuestionVO queryQuestionById(Long id);
    void deleteQuestionById(Long id);

    PageDTO<QuestionAdminVO> queryQuestionsPageAdmin(QuestionAdminPageQuery query);

    void updateQuestionHidden(Long id, Boolean hidden);

    QuestionAdminVO queryQuestionByIdAdmin(Long id);
}
