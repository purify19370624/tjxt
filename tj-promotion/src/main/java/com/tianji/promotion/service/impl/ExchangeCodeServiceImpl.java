package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.ExchangeCodeVO;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionRedisConstants.*;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-06
 */
@Service

public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {
    private final StringRedisTemplate redisTemplate;
    private final BoundValueOperations<String, String> serialOps;
    @Autowired
    public ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.serialOps = redisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);
    }
    @Override
    @Async("generateExchangeCodeExecutor")
    public void asyncGenerateCode(Coupon coupon) {
        // 发放数量
        Integer totalNum = coupon.getTotalNum();
        // 1.获取Redis自增序列号
        Long result = serialOps.increment(totalNum);
        if (result == null) {
            return;
        }
        int maxSerialNum = result.intValue();
        List<ExchangeCode> list = new ArrayList<>(totalNum);
        for (int serialNum = maxSerialNum - totalNum + 1; serialNum <= maxSerialNum; serialNum++) {
            // 2.生成兑换码
            String code = CodeUtil.generateCode(serialNum, coupon.getId());
            ExchangeCode e = new ExchangeCode();
            e.setCode(code);
            e.setId(serialNum);
            e.setExchangeTargetId(coupon.getId());
            e.setExpiredTime(coupon.getIssueEndTime());
            list.add(e);
        }
        // 3.保存数据库
        saveBatch(list);

        // 4.写入Redis缓存，member：couponId，score：兑换码的最大序列号
        redisTemplate.opsForZSet().add(COUPON_RANGE_KEY, coupon.getId().toString(), maxSerialNum);
    }

    @Override
    public PageDTO<ExchangeCodeVO> queryExchangeCodeByPage(CodeQuery query) {
        Page<ExchangeCode> page = lambdaQuery()
                .eq(ExchangeCode::getStatus, query.getStatus())
                .eq(ExchangeCode::getType, 1)
                .gt(ExchangeCode::getExpiredTime, LocalDateTime.now())
                .eq(ExchangeCode::getExchangeTargetId, query.getCouponId())
                .page(query.toMpPage());
        List<ExchangeCode> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<ExchangeCodeVO> list = BeanUtils.copyToList(records, ExchangeCodeVO.class);
        return PageDTO.of(page, list);
    }
    @Override
    public boolean updateExchangeMark(long serialNum, boolean mark) {
        Boolean boo = redisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, mark);
        return boo != null && boo;
    }

}
