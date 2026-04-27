package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-23
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {
    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1.获取登录用户

        Long userId = UserContext.getUser();
        // 2.查询课表

        LearningLesson lesson = lessonService.queryByUserAndCourseId(userId, courseId);
        // 3.查询学习记录
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId,lesson.getId()).list();
        // 4.封装结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        dto.setLatestSectionId(lesson.getLatestSectionId());
        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        if(recordDTO==null){
            throw new BizIllegalException("请求参数不能为空");
        }
        Long userId = UserContext.getUser();
        boolean finished = false;
        if(recordDTO.getSectionType().equals(SectionType.VIDEO.getValue())){
            finished = handleVideoLearningRecord(userId, recordDTO);
        }else{
            finished = handleExamRecord(userId, recordDTO);
        }
        if(!finished){
            return;
        }
        handleLearningLessonsChange(recordDTO,finished);

    }

    private void handleLearningLessonsChange(LearningRecordFormDTO recordDTO, boolean finished) {
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if(lesson==null){
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        boolean allLearned = false;
        if(finished){
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(),false,false);
            if(cInfo==null){
                throw new BizIllegalException("课程不存在，无法更新数据！");
            }
            allLearned = lesson.getLearnedSections()+1>=cInfo.getSectionNum();
        }
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections()==0,LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned,LearningLesson::getStatus,LessonStatus.FINISHED.getValue())
                .set(!finished,LearningLesson::getLatestSectionId,recordDTO.getSectionId())
                .set(!finished,LearningLesson::getLatestLearnTime,recordDTO.getCommitTime())
                .setSql(finished,"learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId,lesson.getId())
                .update();
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDTO) {
        LearningRecord record = BeanUtils.copyBean(recordDTO,LearningRecord.class);

        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());
        boolean success = save(record);
        if(!success){
            throw new DbException("新增考试记录失败");
        }
        return true;
    }

    private boolean handleVideoLearningRecord(Long userId, LearningRecordFormDTO recordDTO) {
        LearningRecord oldlearningrecord = queryOldRecord(recordDTO.getLessonId(),recordDTO.getSectionId());
        if(oldlearningrecord==null){
            LearningRecord record = BeanUtils.copyBean(recordDTO,LearningRecord.class);
            record.setUserId(userId);
            boolean success = save(record);
            if(!success){
                throw new DbException("新增学习记录失败");
            }
        }
        boolean finished = !oldlearningrecord.getFinished()&&recordDTO.getMoment()*2>=recordDTO.getDuration();
        if(!finished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordDTO.getLessonId());
            record.setSectionId(recordDTO.getSectionId());
            record.setMoment(recordDTO.getMoment());
            record.setId(oldlearningrecord.getId());
            record.setFinishTime(oldlearningrecord.getFinishTime());
            taskHandler.addLearningRecordTask(record);
            return false;

        }
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment,recordDTO.getMoment())
                .set(finished,LearningRecord::getFinished,true)
                .set(finished,LearningRecord::getFinishTime,recordDTO.getCommitTime())
                .eq(LearningRecord::getId,oldlearningrecord.getId())
                .update();
        if(!success){
            throw new DbException("更新学习记录失败");
        }
        taskHandler.cleanRecordCache(recordDTO.getLessonId(),recordDTO.getSectionId());
        return finished;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        LearningRecord record = taskHandler.readRecordCache(lessonId,sectionId);
        if(record!=null){
            return record;
        }
        record = lambdaQuery()
                .eq(LearningRecord::getLessonId,lessonId)
                .eq(LearningRecord::getSectionId,sectionId)
                .one();
        taskHandler.writeRecordCache(record);
        return record;
    }


}
