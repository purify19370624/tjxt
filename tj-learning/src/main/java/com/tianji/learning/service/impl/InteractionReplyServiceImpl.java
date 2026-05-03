package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-28
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    private final UserClient userClient;

    private final RabbitMqHelper rabbitMqHelper;

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query) {
        return queryReplyPageThroughIsAdmin(query, true);
    }

    private PageDTO<ReplyVO> queryReplyPageThroughIsAdmin(ReplyPageQuery query, boolean isStudent) {
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if (questionId == null && answerId == null) {
            throw new BadRequestException("错误的请求");
        }
        //分页查询回答或回复信息
        Page<InteractionReply> page = lambdaQuery()
                .eq(questionId != null, InteractionReply::getQuestionId, query.getQuestionId())
                .eq(answerId != null, InteractionReply::getAnswerId, query.getAnswerId())
                .eq(isStudent, InteractionReply::getHidden, false)
                .page(query.toMpPage("liked_times", false));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        Map<Long, Long> idMapTargetIds = new HashMap<>(records.size());
        //定义所有用户id列表、回复的目标回复map
        //根据回复目标回复id查询回答或回复id集合，转为map
        Set<Long> userIds = new HashSet<>(records.size() * 2);
        Set<Long> targetReplyIds = new HashSet<>(records.size());
        records.forEach(reply -> {
            if (!isStudent) {
                userIds.add(reply.getUserId());
            } else if (BooleanUtils.isFalse(reply.getAnonymity())) {
                userIds.add(reply.getUserId());
            }
            Long t = reply.getTargetReplyId();
            if (t != null && t > 0) {
                targetReplyIds.add(t);
                idMapTargetIds.put(reply.getId(), t);
            }
        });
        Map<Long, InteractionReply> targetReplyMap = new HashMap<>(targetReplyIds.size());
        if (CollUtils.isNotEmpty(targetReplyIds)) {
            List<InteractionReply> list = lambdaQuery()
                    .in(InteractionReply::getId, targetReplyIds)
                    .list();
            for (InteractionReply reply : list) {
                targetReplyMap.put(reply.getId(), reply);
                if (!isStudent) {
                    userIds.add(reply.getUserId());
                } else if (BooleanUtils.isFalse(reply.getAnonymity())) {
                    userIds.add(reply.getUserId());
                }
            }
        }
        //根据用户id列表查询用户信息，并转为map
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));

        //封装VO结果集
        List<ReplyVO> results = BeanUtils.copyList(records, ReplyVO.class);
        for (ReplyVO vo : results) {
            UserDTO user = userMap.get(vo.getUserId());
            //当前回复者昵称和头像
            if (user != null) {
                vo.setUserName(user.getName());
                vo.setUserIcon(user.getIcon());
            }
            //目标回复者昵称
            InteractionReply targetReply = targetReplyMap.get(idMapTargetIds.get(vo.getId()));
            if (targetReply != null) {
                UserDTO userDTO = userMap.get(targetReply.getUserId());
                if (userDTO != null) {
                    vo.setTargetUserName(userDTO.getName());
                }
            }

        }
        //返回
        return PageDTO.of(page, results);
    }

    @Override
    @Transactional
    public void saveReply(ReplyDTO replyDTO) {

        boolean isValid = replyDTO.getTargetReplyId() != null || replyDTO.getAnswerId() != null || replyDTO.getQuestionId() != null;
        if (!isValid) {
            throw new BadRequestException("只能评论某一个问题或回答或评论");
        }
        Long userId = UserContext.getUser();
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        boolean saved = save(reply);
        if (!saved) {
            throw new DbException("回答失败，请重新尝试");
        }
        boolean updated = questionService.lambdaUpdate()
                .set(InteractionQuestion::getLatestAnswerId, reply.getId())
                .set(!replyDTO.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.CHECKED)
                .setSql("answer_times = answer_times + 1")
                .eq(replyDTO.getQuestionId() != null, InteractionQuestion::getId, replyDTO.getQuestionId())
                .update();
        if (!updated) {
            throw new DbException("回答失败，请重新尝试");
        }
        rabbitMqHelper.sendAsync(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.WRITE_REPLY,
                userId
        );
    }

    @Override
    public PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query) {
        return queryReplyPage(query);
    }

    @Override
    public ReplyVO queryReplyByIdAdmin(Long id) {
        //1、校验
        if (id == null) {
            throw new BadRequestException("请正确操作访问服务器哦~");
        }

        //2、查询interaction_reply表，按主键查询
        InteractionReply reply = this.getById(id);
        if (reply == null) {
            throw new BadRequestException("回答或评论不存在");
        }

        //3、封装vo
        ReplyVO vo = BeanUtils.copyBean(reply, ReplyVO.class);

        //4、远程调用用户服务，获取用户信息
        UserDTO userDTO = userClient.queryUserById(reply.getUserId());
        if (userDTO != null) {
            vo.setUserName(userDTO.getName());
            vo.setUserIcon(userDTO.getIcon());
        }




        //6、返回vo
        return vo;
    }

    @Override
    public void showOrHiddenReply(Long id, Boolean hidden) {
        boolean updated = lambdaUpdate()
                .set(hidden != null, InteractionReply::getHidden, hidden)
                .eq(InteractionReply::getId, id)
                .update();
        if (!updated) {
            throw new DbException(Boolean.TRUE.equals(hidden) ? "隐藏失败" : "显示失败");
        }
    }
}
