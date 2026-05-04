package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-04
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        Long user = UserContext.getUser();
        List<LikedRecord>list = lambdaQuery()
                .in(LikedRecord::getBizId,bizIds)
                .eq(LikedRecord::getUserId,user)
                .list();
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    @Override
    public void addLikeRecord(LikeRecordFormDTO likeRecordFormDTO) {
        // 1.基于前端的参数，判断是执行点赞还是取消点赞
        boolean  success = likeRecordFormDTO.getLiked() ? like(likeRecordFormDTO) :unlike(likeRecordFormDTO);
        // 2.判断是否执行成功，如果失败，则直接结束
        if(!success){
            return;
        }
        // 3.如果执行成功，统计点赞总数
        Integer likedTimes = lambdaQuery()
                .eq(LikedRecord::getBizId,likeRecordFormDTO.getBizId())
                .count();
        // 4.发送消息到mq

        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,likeRecordFormDTO.getBizType()),
                LikedTimesDTO.of(likeRecordFormDTO.getBizId(), likedTimes));

    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {

    }

    private boolean unlike(LikeRecordFormDTO likeRecordFormDTO) {
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getUserId,UserContext.getUser())
                .eq(LikedRecord::getBizId,likeRecordFormDTO.getBizId())
        );


    }

    private boolean like(LikeRecordFormDTO likeRecordFormDTO) {
        Long userId = UserContext.getUser();
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId,userId)
                .eq(LikedRecord::getBizId,likeRecordFormDTO.getBizId())
                .count();
        if(count>0){
            return false;
        }
        LikedRecord r = new LikedRecord();
        r.setUserId(userId);
        r.setBizId(likeRecordFormDTO.getBizId());
        r.setBizType(likeRecordFormDTO.getBizType());
        save(r);
        return true;
    }

}
