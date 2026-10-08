package com.tianji.search.constants;

public interface SearchErrorInfo {
    String UPDATE_COURSE_STATUS_ERROR = "更新课程状态异常";
    String SAVE_COURSE_ERROR = "新增课程索引异常";
    String QUERY_COURSE_ERROR = "查询课程异常";
    String TEACHER_NOT_EXISTS = "教师信息不存在";
    String STAFF_NOT_EXISTS = "员工信息不存在";
    /** from + size 超过 ES 结果窗口上限时的提示 */
    String SEARCH_WINDOW_EXCEEDED = "搜索结果过深，无法继续翻页，请使用游标（cursor）继续查询";
    /** 游标解析失败（被篡改或格式非法）时的提示 */
    String INVALID_SEARCH_CURSOR = "分页游标无效，请从第一页重新查询";
}
