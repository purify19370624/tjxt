package com.tianji.learning.mapper;

import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.lettuce.core.dynamic.annotation.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-04-23
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {
    List<IdAndNumDTO> countLearnedSections(
            @Param("userId") Long userId,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end
            );
}
