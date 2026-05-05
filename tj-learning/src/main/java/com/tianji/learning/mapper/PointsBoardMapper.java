package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学霸天梯榜 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-04
 */
public interface PointsBoardMapper extends BaseMapper<PointsBoard> {
    void createPointsBoardTable(@Param("tableName") String tableName);
}
