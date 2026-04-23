package com.tianji.learning.service;

import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-21
 */
public interface ILearningLessonService extends IService<LearningLesson> {
    void addUserLessons(Long userId, List<Long> courseIds);
    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();
    Long isLessonValid(Long courseId);

    void deleteCourseFromLesson(Long userId, Long courseIds);
    Integer countLearningLessonByCourse(Long courseId);


    LearningLesson queryByUserAndCourseId(Long userId, Long courseId);

    void createLearningPlans(@NotNull @Min(1) Long courseId, @NotNull @Range(min = 1, max = 50) Integer freq);

    LearningPlanPageVO queryMyplans(PageQuery query);
}
