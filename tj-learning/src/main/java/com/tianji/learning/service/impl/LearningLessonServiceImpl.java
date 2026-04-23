package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-21
 */
@SuppressWarnings("ALL")
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 1.查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        // 2.循环遍历，处理LearningLesson数据
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1.获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            // 2.2.填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 3.批量新增
        saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 1.获取当前登录用户
        Long userId = UserContext.getUser();
        // 2.分页查询
        // select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0, 5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId) // where user_id = #{userId}
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        // 3.查询课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);

        // 4.封装VO返回
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        // 4.1.循环遍历，把LearningLesson转为VO
        for (LearningLesson r : records) {
            // 4.2.拷贝基础属性到vo
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            // 4.3.获取课程信息，填充到vo
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        // 1.获取当前登录的用户
        Long userId = UserContext.getUser();
        // 2.查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null) {
            return null;
        }
        // 3.拷贝PO基础属性到VO
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        // 4.查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(cInfo.getName());
        vo.setCourseCoverUrl(cInfo.getCoverUrl());
        vo.setSections(cInfo.getSectionNum());
        // 5.统计课表中的课程数量 select count(1) from xxx where user_id = #{userId}
        Integer courseAmount = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();
        vo.setCourseAmount(courseAmount);
        // 6.查询小节信息
        List<CataSimpleInfoDTO> cataInfos =
                catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if (!CollUtils.isEmpty(cataInfos)) {
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionName(cataInfo.getName());
            vo.setLatestSectionIndex(cataInfo.getCIndex());
        }
        return vo;
    }

    @Override
    public Long isLessonValid(Long courseId) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId)
                .one();
        if(lesson==null){
            return null;
        }
        LocalDateTime expiretime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();
        if(expiretime!=null&&now.isAfter(expiretime)){
            return null;
        }
        return lesson.getId();
    }

    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id",userId);
        wrapper.eq("course_id",courseId);
        this.remove(wrapper);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        Integer count = this.lambdaQuery()
                .eq(LearningLesson::getCourseId,courseId)
                .count();
        return count;
    }

    @Override
    public LearningLesson queryByUserAndCourseId(Long userId, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userId,courseId));
    }

    @Override
    public void createLearningPlans(Long courseId, Integer freq) {
        Long userId = UserContext.getUser();
        LearningLesson lesson = queryByUserAndCourseId(userId,courseId);
        AssertUtils.isNotNull(lesson,"课程信息不存在！");
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus()== PlanStatus.NO_PLAN.getValue()){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING.getValue());
        }
        updateById(l);
    }

    @Override
    public LearningPlanPageVO queryMyplans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);

        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId,userId)
                .eq(LearningRecord::getFinished,true)
                .gt(LearningRecord::getFinished,begin)
                .lt(LearningRecord::getFinished,end)

        );
        result.setWeekFinished(weekFinished);
        Integer weekTotalPlan = getBaseMapper().selectTotalWeekFreqByUserId(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        //TODO 3.3本周学习积分
        Page<LearningLesson>p = lambdaQuery()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getPlanStatus,PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus,LessonStatus.NOT_BEGIN,LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time",false));
        List<LearningLesson>records = p.getRecords();
        if(CollUtils.isEmpty(records)){
            return result.emptyPage(p);
        }
        Map<Long,CourseSimpleInfoDTO>cMap = queryCourseSimpleInfoList(records);
        List<IdAndNumDTO> list = recordMapper.countLearnedSections(userId,begin,end);
        Map<Long,Integer>countMap = IdAndNumDTO.toMap(list);
        List<LearningPlanVO>voList = new ArrayList<>(records.size());
        for(LearningLesson r:records){
            LearningPlanVO vo = BeanUtils.copyBean(r,LearningPlanVO.class);
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if(cInfo!=null){
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(),0));
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(),p.getPages(),voList);

    }



    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId,Long courseId){
        LambdaQueryWrapper<LearningLesson> queryWrapper = new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId,userId)
                .eq(LearningLesson::getCourseId,courseId);
        return queryWrapper;
    }
    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        // 3.1.获取课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        // 3.2.查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)) {
            // 课程不存在，无法添加
            throw new BadRequestException("课程信息不存在！");
        }
        // 3.3.把课程集合处理成Map，key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> cMap = cInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }
}
