package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {
    private final StringRedisTemplate redisTemplate;
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private static volatile boolean begin = true;

    @PostConstruct
    public void init(){
        CompletableFuture.runAsync(this::handleDelayTask);
    }



    @PreDestroy
    public void destroy(){
        begin = false;
        log.debug("延迟任务停止执行!");
    }
    private void handleDelayTask() {
        while (begin){
            try {
                DelayTask<RecordTaskData>task = queue.take();
                RecordTaskData data = task.getData();

                LearningRecord record = readRecordCache(data.getLessonId(),data.getSectionId());
                if(record==null){
                    continue;
                }
                if(!Objects.equals(data.getMoment(),record.getMoment())){
                    continue;
                }
                record.setFinished(null);
                recordMapper.updateById(record);
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try{
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key,sectionId.toString());
            if(cacheData==null){
                return null;
            }
            return JsonUtils.toBean(cacheData.toString(),LearningRecord.class);
        }catch (Exception e){
            log.error("缓存读取失败",e);
            return null;
        }
    }

    public void cleanRecordCache(Long lessonId,Long sectionId){
        String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
        redisTemplate.opsForHash().delete(key,sectionId.toString());
    }

    public void addLearningRecordTask(LearningRecord record){
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try{
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,record.getLessonId());
            redisTemplate.opsForHash().put(key,record.getSectionId().toString(),json);
            redisTemplate.expire(key,Duration.ofMinutes(1));
        }catch (Exception e){
            log.error("更新学习记录缓存异常",e);
        }
    }


    @Data
    @NoArgsConstructor
    private static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
    @Data
    @NoArgsConstructor
    private static class RecordCacheData{
        private Long id;
        private boolean finished;
        private Integer moment;

        public RecordCacheData(LearningRecord record) {
            this.finished = record.getFinished();
            this.id = record.getId();
            this.moment = record.getMoment();
        }
    }
}
