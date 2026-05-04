package com.tianji.remark.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikedTimesDTO {
    /**
     * 点赞的业务id
     */
    private Long bizId;
    /**
     * 总的点赞次数
     */
    private Integer likedTimes;
    public static LikedTimesDTO of(Long bizId, Integer likedTimes) {
        if (bizId == null) {
            throw new IllegalArgumentException("bizId不能为空");
        }
        return new LikedTimesDTO(bizId, likedTimes == null ? 0 : likedTimes);
    }

}
