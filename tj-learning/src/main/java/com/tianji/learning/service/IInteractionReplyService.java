package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;

import javax.validation.Valid;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-28
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query);

    void saveReply(@Valid ReplyDTO replyDTO);

    PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query);

    ReplyVO queryReplyByIdAdmin(Long id);

    void showOrHiddenReply(Long id, Boolean hidden);
}
