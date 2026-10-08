package com.tianji.search.service;

import com.tianji.search.domain.query.CoursePageQuery;
import com.tianji.search.domain.vo.CourseVO;
import com.tianji.search.domain.vo.SearchPageVO;

import java.util.List;

public interface ISearchService {

    List<CourseVO> queryCourseByCateId(Long cateLv2Id);

    List<CourseVO> queryBestTopN();

    List<CourseVO> queryNewTopN();

    List<CourseVO> queryFreeTopN();

    /**
     * 用户端课程搜索（分页）。
     * 不传 cursor 时按 pageNo/pageSize 浅分页；传 cursor 时按 search_after 深分页。
     */
    SearchPageVO queryCoursesForPortal(CoursePageQuery query);

    List<Long> queryCoursesIdByName(String keyword);
}
