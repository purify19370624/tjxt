package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-04
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {
    Integer querySeasonByTime(LocalDateTime time);
}
