package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import net.bytebuddy.asm.Advice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final StringRedisTemplate redisTemplate;
    private final IPointsBoardService pointsBoardService;
    private final IPointsBoardSeasonService pointsBoardSeasonService;
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        if(season==null){
            return;
        }
        pointsBoardService.createPointsBoardTableBySeason(season);
    }
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        Integer season = pointsBoardSeasonService.querySeasonByTime(time);
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX+season);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX+time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index+1;

        int pageSize = 10;
        while (true){
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key,pageNo,pageSize);
            if (CollUtils.isEmpty(boardList)){
                break;
            }
            boardList.forEach(b->{
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            pointsBoardService.saveBatch(boardList);
            pageNo+=total;
        }
        TableInfoContext.remove();
    }
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX+time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.unlink(key);
    }
}
