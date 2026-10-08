package com.tianji.search.service.impl;

import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.ErrorInfo;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.utils.*;
import com.tianji.search.config.InterestsProperties;
import com.tianji.search.constants.SearchErrorInfo;
import com.tianji.search.domain.po.Course;
import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.search.domain.vo.SearchPageVO;
import com.tianji.search.repository.CourseRepository;
import com.tianji.search.service.IInterestsService;
import com.tianji.search.service.ISearchService;
import com.tianji.search.support.SearchCursor;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.search.repository.CourseRepository.PUBLISH_TIME;

@Service
public class SearchServiceImpl implements ISearchService {

    /**
     * ES 的结果窗口上限，对应索引设置 index.max_result_window 的默认值 10000。
     * from + size 超过它 ES 会直接抛 "Result window is too large" 异常。
     */
    private static final int MAX_RESULT_WINDOW = 10_000;

    /**
     * 单页最大条数。ES 要为每条命中取回 _source 并做高亮，页越大内存与网络开销越高，
     * 同时也会让 from/size 更快触达结果窗口上限，因此这里做一次截断保护。
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 决胜排序字段。课程 id 唯一，把它追加到排序末尾可以保证排序是「全序」：
     * 既避免同分/同值文档在翻页时出现重复或遗漏，也是 search_after 正确工作的前提。
     */
    private static final String SORT_TIE_BREAKER = "id";

    @Autowired
    private RestHighLevelClient restClient;

    @Autowired
    private IInterestsService interestsService;

    @Autowired
    private UserClient userClient;

    @Autowired
    private CategoryCache categoryCache;

    @Autowired
    private InterestsProperties interestsProperties;

    @Override
    public List<CourseVO> queryCourseByCateId(Long cateLv2Id) {
        return queryTopNByCategoryIdLv2sAndFree(
                CollUtils.singletonList(cateLv2Id), null, PUBLISH_TIME, false, 10);
    }

    @Override
    public List<CourseVO> queryBestTopN() {
        // 1.获取当前用户
        return queryTopNCourseOnMarketByFree(false, CourseRepository.SOLD);
    }

    @Override
    public List<CourseVO> queryNewTopN() {
        return queryTopNCourseOnMarketByFree(false, PUBLISH_TIME);
    }

    @Override
    public List<CourseVO> queryFreeTopN() {
        return queryTopNCourseOnMarketByFree(true, CourseRepository.SOLD);
    }

    private List<CourseVO> queryTopNCourseOnMarketByFree(boolean isFree, String sortBy) {
        // 1.获取当前用户
        Long id = UserContext.getUser();
        // 2.查询课程
        List<CourseVO> courses = null;
        if (id == null) {
            // 3.未登录，直接查询报名人数最多的
            courses = queryTopNByCategoryIdLv2sAndFree(
                    null, isFree, sortBy, false, interestsProperties.getTopNumber());
        } else {
            // 4.已登录，根据兴趣爱好查询
            List<Long> categoryIds = interestsService.queryMyInterestsIds();
            if (CollUtils.isEmpty(categoryIds)) {
                // 4.1.没有兴趣爱好，直接查询报名人数最多的
                courses = queryTopNByCategoryIdLv2sAndFree(
                        null, isFree, sortBy, false, interestsProperties.getTopNumber());
            } else {
                // 4.2.有爱好.查询爱好课程中报名人数最多的
                courses = queryTopNByCategoryIdLv2sAndFree(
                        categoryIds, isFree, sortBy, false, interestsProperties.getTopNumber());
            }
        }
        return courses;
    }

    private List<CourseVO> queryTopNByCategoryIdLv2sAndFree(
            List<Long> categoryIds, Boolean isFree, String sortBy, boolean isASC, int n) {
        // 1.准备Request
        SearchRequest request = new SearchRequest(CourseRepository.INDEX_NAME);
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        // 1.1.是否免费
        if(isFree != null) {
            queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.FREE, isFree));
        }
        // 1.2.分类id
        if (categoryIds != null) {
            if (categoryIds.size() == 1) {
                queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.CATEGORY_ID_LV2, categoryIds.get(0)));
            } else {
                queryBuilder.filter(QueryBuilders.termsQuery(CourseRepository.CATEGORY_ID_LV2, categoryIds));
            }
        }
        if(isFree != null || categoryIds != null) {
            request.source().query(queryBuilder);
        }
        // 1.3.TopN
        request.source().size(n).sort(sortBy, isASC ? SortOrder.ASC : SortOrder.DESC);
        // 2.发送请求
        SearchResponse response = null;
        try {
            response = restClient.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR, e);
        }
        // 3.解析
        SearchHits searchHits = response.getHits();
        SearchHit[] hits = searchHits.getHits();
        if (hits == null || hits.length == 0) {
            return CollUtils.emptyList();
        }
        List<CourseVO> courses = new ArrayList<>(hits.length);
        Set<Long> teacherIds = new HashSet<>(hits.length);
        for (SearchHit hit : hits) {
            // 3.1.数据转换
            CourseVO vo = JsonUtils.toBean(hit.getSourceAsString(), CourseVO.class);
            // 3.2.获取分类id
            teacherIds.add(Long.valueOf(vo.getTeacher()));
            // 3.3.保存
            courses.add(vo);
        }
        teacherIds.remove(0L);
        if (teacherIds.size() == 0) {
            return courses;
        }
        // 4.查询教师
        List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
        AssertUtils.isNotEmpty(teachers, SearchErrorInfo.TEACHER_NOT_EXISTS);
        Map<String, String> tMap = teachers.stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), UserDTO::getName));
        for (CourseVO c : courses) {
            c.setTeacher(tMap.getOrDefault(c.getTeacher(), "匿名"));
        }
        return courses;
    }

    @Override
    public SearchPageVO queryCoursesForPortal(CoursePageQuery query) {
        // 1.规整分页参数：非法值回落默认、超大页大小截断，保证后续 total/pages 与实际查询一致
        normalizePaging(query);
        // 2.搜索数据
        SearchResponse response = searchForResponse(query, CourseVO.EXCLUDE_FIELDS);
        // 3.解析响应
        PageDTO<Course> result = handleSearchResponse(response, query.getPageSize());
        // 4.计算下一页游标：本页取满才说明后面可能还有数据
        String nextCursor = buildNextCursor(response, query.getPageSize());
        // 5.处理VO
        List<Course> list = result.getList();
        if (CollUtils.isEmpty(list)) {
            return SearchPageVO.empty(result.getTotal(), result.getPages());
        }
        // 5.1.查询教师信息
        List<Long> teacherIds = list.stream().map(Course::getTeacher).collect(Collectors.toList());
        List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
        AssertUtils.isNotEmpty(teachers, SearchErrorInfo.TEACHER_NOT_EXISTS);
        Map<Long, String> teacherMap = teachers.stream()
                .collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        // 5.2.转换VO
        List<CourseVO> vos = new ArrayList<>(list.size());
        for (Course c : list) {
            CourseVO vo = BeanUtils.toBean(c, CourseVO.class);
            vo.setTeacher(teacherMap.getOrDefault(c.getTeacher(), "未知"));
            vos.add(vo);
        }
        return new SearchPageVO(result.getTotal(), result.getPages(), vos, nextCursor);
    }

    @Override
    public List<Long> queryCoursesIdByName(String keyword) {
        // 1.创建Request
        SearchRequest request = new SearchRequest(CourseRepository.INDEX_NAME);
        // 2.构建DSL
        request.source()
                .query(QueryBuilders.matchPhraseQuery(CourseRepository.DEFAULT_QUERY_NAME, keyword))
                .fetchSource(new String[]{"id"}, null);
        // 3.查询
        SearchResponse response;
        try {
            response = restClient.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR, e);
        }
        // 4.解析
        SearchHits searchHits = response.getHits();
        // 4.1.获取hits
        SearchHit[] hits = searchHits.getHits();
        if (hits.length == 0) {
            return CollUtils.emptyList();
        }
        // 4.2.获取id
        return Arrays.stream(hits)
                .map(SearchHit::getId)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }


    private SearchResponse searchForResponse(CoursePageQuery query, String[] excludeFields) {
        // 1.创建Request
        SearchRequest request = new SearchRequest(CourseRepository.INDEX_NAME);
        // 2.构建DSL
        // 2.1.构建query
        buildBasicQuery(request, query);
        // 2.2.排序：客户端指定 > 相关性，末尾统一追加唯一决胜字段
        buildSort(request.source(), query);
        // 2.3.分页：优先游标（search_after），否则 from/size 并做结果窗口保护
        buildPaging(request.source(), query);
        // 2.4.精确统计总数：ES 默认只精确到 10000 条，会让 total/pages 在超大结果集上失真
        request.source().trackTotalHits(true);
        // 2.5.高亮
        request.source().highlighter(new HighlightBuilder().field(CourseRepository.DEFAULT_QUERY_NAME));
        // 2.6.source处理
        request.source().fetchSource(null, excludeFields);
        // 3.发送请求
        try {
            return restClient.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new CommonException(ErrorInfo.Msg.SERVER_INTER_ERROR, e);
        }
    }

    /**
     * 规整分页参数：页码、页大小非法时回落默认值，超过上限则截断。
     *
     * <p>目的是把「单个请求最多取回多少文档」变成常量，避免一个请求就把 ES 和网关的内存打满。
     * 截断而不是直接报错，是为了兼容那些习惯性传大 pageSize 的调用方。
     */
    void normalizePaging(CoursePageQuery query) {
        Integer pageNo = query.getPageNo();
        if (pageNo == null || pageNo < 1) {
            query.setPageNo(PageQuery.DEFAULT_PAGE_NUM);
        }
        Integer pageSize = query.getPageSize();
        if (pageSize == null || pageSize < 1) {
            query.setPageSize(PageQuery.DEFAULT_PAGE_SIZE);
        } else if (pageSize > MAX_PAGE_SIZE) {
            query.setPageSize(MAX_PAGE_SIZE);
        }
    }

    /**
     * 构建排序条件。无论客户端是否指定排序，末尾都追加唯一字段作为决胜条件。
     *
     * <p>没有决胜字段时，ES 对「同分或排序字段取值相同」的文档的返回顺序是<b>不保证稳定</b>的，
     * 于是翻页时会出现同一条课程重复出现、另一条课程永远看不到的情况；
     * 而 {@code search_after} 本身也要求排序是全序，否则游标无法唯一定位。
     */
    void buildSort(SearchSourceBuilder source, CoursePageQuery query) {
        String sortBy = query.getSortBy();
        if (StringUtils.isNotBlank(sortBy)) {
            SortOrder order = Boolean.TRUE.equals(query.getIsAsc()) ? SortOrder.ASC : SortOrder.DESC;
            source.sort(SortBuilders.fieldSort(sortBy).order(order));
        } else {
            // 未指定排序时优先按相关性；match_all 场景下 _score 恒为 1.0，此时实际由决胜字段决定顺序
            source.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        }
        source.sort(SortBuilders.fieldSort(SORT_TIE_BREAKER).order(SortOrder.ASC));
    }

    /**
     * 构建分页条件。
     *
     * <p>带游标时使用 {@code search_after}：由上一页最后一条的排序值直接定位，
     * 代价与页深无关，因此可以无限翻页；
     * 不带游标时退化为 {@code from/size}，并在触及 ES 结果窗口上限之前抛出明确的业务异常（400），
     * 而不是把 ES 的 "Result window is too large" 包装成 500 抛给前端。
     */
    void buildPaging(SearchSourceBuilder source, CoursePageQuery query) {
        source.size(query.getPageSize());
        String cursor = query.getCursor();
        if (StringUtils.isNotBlank(cursor)) {
            // search_after 与 from 互斥：ES 要求此时 from 为 0，故这里不再设置 from
            source.searchAfter(SearchCursor.decode(cursor));
            return;
        }
        int from = query.from();
        if (from + query.getPageSize() > MAX_RESULT_WINDOW) {
            throw new BadRequestException(SearchErrorInfo.SEARCH_WINDOW_EXCEEDED);
        }
        source.from(from);
    }

    /**
     * 生成下一页游标：取本页最后一条命中的排序值。
     * 该数组与 {@link #buildSort} 声明的排序字段一一对应，原样回传给 search_after 即可定位下一页。
     * 本页没取满，说明后面已经没有数据，返回 null 表示到达最后一页。
     */
    String buildNextCursor(SearchResponse response, int pageSize) {
        SearchHit[] hits = response.getHits().getHits();
        if (hits == null || hits.length < pageSize) {
            return null;
        }
        return SearchCursor.encode(hits[hits.length - 1].getSortValues());
    }

    private void buildBasicQuery(SearchRequest request, CoursePageQuery query) {
        // 1.准备bool查询
        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        // 2.关键字搜索
        String keyword = query.getKeyword();
        if (StringUtils.isBlank(keyword)) {
            queryBuilder.must(QueryBuilders.matchAllQuery());
        } else {
            queryBuilder.must(QueryBuilders.matchPhraseQuery(CourseRepository.DEFAULT_QUERY_NAME, keyword));
        }
        // 3.其它条件
        if (query.getCategoryIdLv1() != null) {
            queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.CATEGORY_ID_LV1, query.getCategoryIdLv1()));
        }
        if (query.getCategoryIdLv2() != null) {
            queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.CATEGORY_ID_LV2, query.getCategoryIdLv2()));
        }
        if (query.getCategoryIdLv3() != null) {
            queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.CATEGORY_ID_LV3, query.getCategoryIdLv3()));
        }
        if (query.getFree() != null) {
            queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.FREE, query.getFree()));
        }
        if (query.getType() != null) {
            queryBuilder.filter(QueryBuilders.termQuery(CourseRepository.TYPE, query.getType()));
        }
        LocalDateTime beginTime = query.getBeginTime();
        LocalDateTime endTime = query.getEndTime();
        if(beginTime != null || endTime != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery(CourseRepository.UPDATE_TIME);
            if (beginTime != null) {
                rangeQuery.gte(beginTime);
            }
            if (endTime != null) {
                rangeQuery.lte(endTime);
            }
            queryBuilder.filter(rangeQuery);
        }
        // 4.写入request
        request.source().query(queryBuilder);
    }

    private PageDTO<Course> handleSearchResponse(SearchResponse response, int pageSize) {
        SearchHits searchHits = response.getHits();
        // 1.总条数
        long total = searchHits.getTotalHits().value;
        // 2.总页数
        long totalPages = (total + pageSize - 1) / pageSize;
        // 3.获取命中的数据
        SearchHit[] hits = searchHits.getHits();
        if (hits.length <= 0) {
            return new PageDTO<>(total, totalPages, CollUtils.emptyList());
        }
        // 4.遍历
        List<Course> list = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            // 5.获取某一条source
            String jsonSource = hit.getSourceAsString();
            // 6.反序列化
            Course course = JsonUtils.toBean(jsonSource, Course.class);
            // 7.处理高亮
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            if (CollUtils.isNotEmpty(highlightFields)) {
                // 7.1.获取高亮结果
                HighlightField field = highlightFields.get(CourseRepository.DEFAULT_QUERY_NAME);
                Object[] fragments = field.getFragments();
                String value = StringUtils.join(fragments);
                // 7.2.覆盖非高亮结果
                course.setName(value);
            }
            list.add(course);
        }
        return new PageDTO<>(total, totalPages, list);
    }
}
