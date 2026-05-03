package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-28
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;
    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;
    private final RabbitMqHelper rabbitMqHelper;
    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        Long userId = UserContext.getUser();
        question.setUserId(userId);
        boolean saved = save(question);
        if (!saved) {
            throw new DbException("发布问题失败");
        }
        rabbitMqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.COMMENT_EVALUTE,
                userId
        );
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {

        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();

        // ✅ 修复1：不能同时为空（而不是必须都有）
        if (courseId == null && sectionId == null) {
            throw new BadRequestException("课程ID和小节ID不能同时为空");
        }

        Long userId = UserContext.getUser();

        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class, info -> !info.getProperty().equals("description"))

                // ✅ 修复2：动态条件（核心）
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)

                // ✅ 修复3：onlyMine 安全判断
                .eq(Boolean.TRUE.equals(query.getOnlyMine()) && userId != null,
                        InteractionQuestion::getUserId, userId)

                // ⚠️ 可选：如果你现在查不到数据，可以先注释这一行测试
                .eq(InteractionQuestion::getHidden, false)

                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // ================== 以下你的代码不动 ==================

        Set<Long> userIds = records.stream()
                .filter(q -> q != null && !q.getAnonymity())
                .map(InteractionQuestion::getUserId)
                .collect(Collectors.toSet());

        Set<Long> answerIds = records.stream()
                .map(InteractionQuestion::getLatestAnswerId)
                .filter(Objects::nonNull) // ✅ 小优化：避免 null
                .collect(Collectors.toSet());

        Map<Long, InteractionReply> replyMap = new HashMap<>(answerIds.size());

        if (CollUtils.isNotEmpty(answerIds)) {
            List<InteractionReply> replies = replyMapper.selectList(
                    new LambdaQueryWrapper<>(InteractionReply.class)
                            .eq(InteractionReply::getHidden, false)
                            .in(InteractionReply::getId, answerIds)
            );

            replyMap = replies.stream()
                    .collect(Collectors.toMap(InteractionReply::getId, reply -> reply));

            Set<Long> users = replies.stream()
                    .filter(reply -> !reply.getAnonymity())
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());

            userIds.addAll(users);
        }

        userIds.remove(null);

        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());

        if (CollUtils.isNotEmpty(userIds)) { // ✅ 防止空集合查接口
            List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
            if (CollUtils.isNotEmpty(userDTOS)) {
                userMap = userDTOS.stream()
                        .collect(Collectors.toMap(UserDTO::getId, user -> user));
            }
        }

        List<QuestionVO> results = new ArrayList<>(records.size());

        for (InteractionQuestion record : records) {
            QuestionVO questionVO = BeanUtils.copyBean(record, QuestionVO.class);

            if (record != null && !record.getAnonymity()) {
                UserDTO userDTO = userMap.get(record.getUserId());
                if (userDTO != null) { // ✅ 防止空指针
                    questionVO.setUserId(userDTO.getId());
                    questionVO.setUserName(userDTO.getName());
                    questionVO.setUserIcon(userDTO.getIcon());
                }
            }

            InteractionReply r = replyMap.get(record.getLatestAnswerId());

            if (r != null) {
                questionVO.setLatestReplyContent(r.getContent());

                if (!r.getAnonymity()) {
                    UserDTO userDTO = userMap.get(r.getUserId());
                    if (userDTO != null) {
                        questionVO.setLatestReplyUser(userDTO.getName());
                    }
                }
            }

            results.add(questionVO);
        }

        return PageDTO.of(page, results);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionDTO) {
        boolean updated = lambdaUpdate()
                .set(StringUtils.isNotBlank(questionDTO.getTitle()), InteractionQuestion::getTitle, questionDTO.getTitle())
                .set(StringUtils.isNotBlank(questionDTO.getDescription()), InteractionQuestion::getDescription, questionDTO.getDescription())
                .set(questionDTO.getAnonymity() != null, InteractionQuestion::getAnonymity, questionDTO.getAnonymity())
                .eq(InteractionQuestion::getId, id)
                .update();
        if (!updated) {
            throw new DbException("修改问题失败");
        }
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        InteractionQuestion question = this.getById(id);
        if (question == null || question.getHidden()) {
            return null;
        }
        QuestionVO questionVO = BeanUtils.copyBean(question, QuestionVO.class);
        if (!question.getAnonymity()) {
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                questionVO.setUserName(userDTO.getName());
                questionVO.setUserIcon(userDTO.getIcon());
            }
        }
        return questionVO;
    }

    @Override
    @Transactional
    public void deleteQuestionById(Long id) {
        InteractionQuestion question = getById(id);
        Long userId = UserContext.getUser();
        if (!Objects.equals(question.getUserId(), userId)) {
            throw new BadRequestException("只能删除自己发布的问题");
        }
        boolean removed = removeById(id);
        if (!removed) {
            throw new DbException("删除问题失败");
        }
        LambdaQueryWrapper<InteractionReply> wrapper = new LambdaQueryWrapper<>(InteractionReply.class).eq(InteractionReply::getQuestionId, id);
        replyMapper.delete(wrapper);
    }


    @Override
    public PageDTO<QuestionAdminVO> queryQuestionsPageAdmin(QuestionAdminPageQuery query) {
        //根据课程名字获取课程id集合
        List<Long>courseIdList = null;
        String courseName = query.getCourseName();
        if(StringUtils.isNotBlank(courseName)){
            courseIdList = searchClient.queryCoursesIdByName(courseName);
            if(CollUtils.isEmpty(courseIdList)){
                courseIdList = null;
            }
        }
        //分页条件查询问题列表
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIdList != null, InteractionQuestion::getCourseId, courseIdList)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .ge(begin != null, InteractionQuestion::getCreateTime, begin)
                .le(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion>records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }


        //从问题列表中解析出：课程id集合、用户id集合、章节id集合
        Set<Long>courseIds = new HashSet<>(records.size());
        Set<Long>userIds = new HashSet<>(records.size());
        Set<Long>chseIds = new HashSet<>(records.size());
        for(InteractionQuestion record:records){
            courseIds.add(record.getCourseId());
            userIds.add(record.getUserId());
            chseIds.add(record.getSectionId());
        }
        //查询课程信息map
        List<CourseSimpleInfoDTO>courseInfos = courseClient.getSimpleInfoList(courseIds);
        Map<Long,CourseSimpleInfoDTO> courseMap = new HashMap<>(courseInfos.size());
        if(CollUtils.isNotEmpty(courseInfos)){
            courseMap = courseInfos.stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, course -> course));
        }
        //查询用户信息map
        List<UserDTO>userDTOS = userClient.queryUserByIds(userIds);
        Map<Long,UserDTO>userMap = new HashMap<>(userDTOS.size());
        if(CollUtils.isNotEmpty(userDTOS)){
            userMap = userDTOS.stream()
                    .collect(Collectors.toMap(UserDTO::getId,user->user));
        }
        //查询章节信息map
        List<CataSimpleInfoDTO>cataInfoDTOs= catalogueClient.batchQueryCatalogue(chseIds);
        Map<Long,String>cataMap = new HashMap<>(cataInfoDTOs.size());
        if(CollUtils.isNotEmpty(cataInfoDTOs)){
            cataMap = cataInfoDTOs.stream()
                    .collect(Collectors.toMap(CataSimpleInfoDTO::getId,CataSimpleInfoDTO::getName));

        }
        //封装VO列表
        List<QuestionAdminVO>results = new ArrayList<>(records.size());
        for(InteractionQuestion record:records){
            QuestionAdminVO questionVO = BeanUtils.copyBean(record,QuestionAdminVO.class);
            results.add(questionVO);
            questionVO.setChapterName(cataMap.getOrDefault(record.getChapterId(),""));
            questionVO.setSectionName(cataMap.getOrDefault(record.getSectionId(),""));
            CourseSimpleInfoDTO courseDTO = courseMap.get(record.getCourseId());
            if(courseDTO!=null){
                questionVO.setCourseName(courseDTO.getName());
                String catagoryName = categoryCache.getCategoryNames(courseDTO.getCategoryIds());
                questionVO.setCategoryName(StringUtils.isNotBlank(catagoryName)?catagoryName:"");
            }
            UserDTO userDTO = userMap.get(record.getUserId());
            if(userDTO!=null){
                questionVO.setUserName(userDTO.getName());
            }
        }
        //返回
        return PageDTO.of(page,results);
    }

    @Override
    public void updateQuestionHidden(Long id, Boolean hidden) {
        if(id==null||hidden==null){
            throw new BadRequestException("请合理请求");
        }
        boolean updated = lambdaUpdate()
                .set(InteractionQuestion::getHidden,hidden)
                .eq(InteractionQuestion::getId,id)
                .update();
        if (!updated) {
            throw new DbException(hidden ? "隐藏问题失败" : "显示问题失败");
        }
    }

    @Override
    @Transactional
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        //查询问题信息
        InteractionQuestion question = getById(id);
        if (question == null) {
            return null;
        }
        QuestionAdminVO questionVO = BeanUtils.copyBean(question, QuestionAdminVO.class);
        //查询课程名称和分类名称
        CourseFullInfoDTO courseInfoDTO = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        List<Long> teacherIds = new ArrayList<>();
        if (courseInfoDTO != null) {
            questionVO.setCourseName(courseInfoDTO.getName());
            questionVO.setCategoryName(categoryCache.getCategoryNames(courseInfoDTO.getCategoryIds()));
            teacherIds = courseInfoDTO.getTeacherIds();
        }
        //查询章节信息
        List<CataSimpleInfoDTO> cataDTOs = catalogueClient.batchQueryCatalogue(Set.of(question.getChapterId(), question.getSectionId()));
        Map<Long, String> cataMap = new HashMap<>(2);
        if (CollUtils.isNotEmpty(cataDTOs)) {
            cataMap = cataDTOs.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));
        }
        questionVO.setCategoryName(cataMap.getOrDefault(question.getChapterId(), ""));
        questionVO.setSectionName(cataMap.getOrDefault(question.getSectionId(), ""));
        //查询用户昵称和老师昵称
        Set<Long> userIds = new HashSet<>(teacherIds.size() + 1);
        userIds.addAll(teacherIds);
        userIds.add(question.getUserId());
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = new HashMap<>(userDTOS.size());
        if (CollUtils.isNotEmpty(userDTOS)) {
            userMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, user -> user));
        }
        UserDTO userDTO = userMap.get(question.getUserId());
        if (userDTO != null) {
            questionVO.setUserName(userDTO.getName());
            questionVO.setUserIcon(userDTO.getIcon());
        }

        userMap.remove(question.getUserId());
        if (CollUtils.isNotEmpty(userMap)) {
            String teacherNickName = userMap.values().stream().map(UserDTO::getName).collect(Collectors.joining("、"));
            questionVO.setTeacherName(teacherNickName);
        }
        //更新回答状态为已查看
        lambdaUpdate()
                .set(InteractionQuestion::getStatus, QuestionStatus.CHECKED)
                .eq(InteractionQuestion::getId, id)
                .update();
        //返回
        return questionVO;
    }
}
