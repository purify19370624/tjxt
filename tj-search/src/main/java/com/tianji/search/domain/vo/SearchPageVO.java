package com.tianji.search.domain.vo;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.CollUtils;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 课程搜索结果分页对象。
 *
 * <p>在通用 {@link PageDTO} 基础上增加了 {@code nextCursor}：
 * 客户端把它作为下一页请求的 {@code cursor} 参数，即可用 {@code search_after}
 * 方式无限向下翻页，绕开 ES {@code from + size} 的结果窗口上限。
 *
 * <p>继承而非改造 {@link PageDTO}，是为了不污染其他分页接口的返回结构；
 * 序列化后仍然包含 {@code total/pages/list}，对既有前端完全兼容。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ApiModel(description = "课程搜索结果分页")
public class SearchPageVO extends PageDTO<CourseVO> {

    @ApiModelProperty(value = "下一页游标，为空表示已到最后一页", example = "WzEuMCwxMjA0MTAxXQ")
    private String nextCursor;

    public SearchPageVO(Long total, Long pages, List<CourseVO> list, String nextCursor) {
        super(total, pages, list);
        this.nextCursor = nextCursor;
    }

    public static SearchPageVO empty(Long total, Long pages) {
        return new SearchPageVO(total, pages, CollUtils.emptyList(), null);
    }
}
