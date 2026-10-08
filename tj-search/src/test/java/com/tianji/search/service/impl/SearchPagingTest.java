package com.tianji.search.service.impl;

import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.support.SearchCursor;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页/排序 DSL 的单元测试：只验证构建出来的查询结构，不需要真实的 ES 集群。
 */
class SearchPagingTest {

    private final SearchServiceImpl service = new SearchServiceImpl();

    private static CoursePageQuery query(int pageNo, int pageSize) {
        CoursePageQuery query = new CoursePageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    @Test
    void normalizePaging_fallsBackToDefaultsWhenValuesAreIllegal() {
        CoursePageQuery query = query(0, -5);

        service.normalizePaging(query);

        assertEquals(PageQuery.DEFAULT_PAGE_NUM, query.getPageNo());
        assertEquals(PageQuery.DEFAULT_PAGE_SIZE, query.getPageSize());
    }

    @Test
    void normalizePaging_truncatesOversizedPageSize() {
        CoursePageQuery query = query(1, 100_000);

        service.normalizePaging(query);

        assertEquals(100, query.getPageSize().intValue(), "单页条数必须被截断，避免一个请求拉走整个索引");
    }

    @Test
    void buildPaging_shallowPageStillUsesFromAndSize() {
        CoursePageQuery query = query(3, 20);
        service.normalizePaging(query);

        SearchSourceBuilder source = new SearchSourceBuilder();
        service.buildPaging(source, query);

        assertEquals(40, source.from());
        assertEquals(20, source.size());
    }

    @Test
    void buildPaging_rejectsPageBeyondResultWindowWithBadRequest() {
        // pageSize 会先被截断到 100，所以第 101 页的 from = 10000 就已经触及结果窗口上限
        CoursePageQuery query = query(101, 100);
        service.normalizePaging(query);
        SearchSourceBuilder source = new SearchSourceBuilder();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.buildPaging(source, query));

        assertEquals(400, ex.getStatus(), "应返回业务 400，而不是让 ES 抛异常变成 500");
    }

    @Test
    void buildPaging_cursorSwitchesToSearchAfter() {
        CoursePageQuery query = query(1, 20);
        service.normalizePaging(query);
        query.setCursor(SearchCursor.encode(new Object[]{1.0f, 1_204_101L}));

        SearchSourceBuilder source = new SearchSourceBuilder();
        service.buildPaging(source, query);

        // search_after 与 from 互斥：ES 要求此时不设置 from
        // （SearchSourceBuilder 未显式设置 from 时取值为 -1，等价于 0）
        assertEquals(-1, source.from(), "带游标时必须走 search_after，不能再带 from");
        assertTrue(source.toString().contains("search_after"),
                "带游标时必须走 search_after，而不是继续用 from/size");
    }

    @Test
    void buildSort_alwaysAppendsUniqueTieBreaker() {
        SearchSourceBuilder source = new SearchSourceBuilder();

        service.buildSort(source, query(1, 20));

        assertEquals(2, source.sorts().size(), "除业务排序外还必须有一个决胜字段");
        assertInstanceOf(ScoreSortBuilder.class, source.sorts().get(0), "未指定排序时按相关性");
        FieldSortBuilder tieBreaker = assertInstanceOf(FieldSortBuilder.class, source.sorts().get(1));
        assertEquals("id", tieBreaker.getFieldName());
        assertEquals(SortOrder.ASC, tieBreaker.order());
    }

    @Test
    void buildSort_honoursClientSortFieldAndDirection() {
        CoursePageQuery query = query(1, 20);
        query.setSortBy("sold");
        query.setIsAsc(false);
        SearchSourceBuilder source = new SearchSourceBuilder();

        service.buildSort(source, query);

        FieldSortBuilder primary = assertInstanceOf(FieldSortBuilder.class, source.sorts().get(0));
        assertEquals("sold", primary.getFieldName());
        assertEquals(SortOrder.DESC, primary.order());
        assertEquals("id", ((FieldSortBuilder) source.sorts().get(1)).getFieldName());
    }
}
