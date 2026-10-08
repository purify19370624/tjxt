package com.tianji.search.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.CommonException;
import com.tianji.search.constants.SearchErrorInfo;

import java.util.Base64;

/**
 * 深分页游标（Elasticsearch {@code search_after}）编解码器。
 *
 * <p>游标本质是「上一页最后一条命中的排序值数组」，把它原样回传给
 * {@code search_after} 即可定位到下一页，因此翻页代价与页深无关，可以无限翻页。
 *
 * <p>对外暴露的是 Base64URL 编码后的不透明字符串，客户端不需要（也不应该）
 * 理解其内部结构，这样将来调整排序字段时不会破坏兼容性。
 *
 * <p>注意：排序值经 JSON 往返后数值类型可能窄化（如 {@code long} 变成 {@code int}），
 * 但数值本身不变；这些值最终以 JSON 形式发给 ES，由 ES 按字段映射做类型转换，
 * 因此不影响 {@code search_after} 的正确性。
 *
 * @author tianji
 */
public final class SearchCursor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** URL 安全且去掉填充符，便于直接作为 query 参数传递 */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private SearchCursor() {
    }

    /**
     * 把排序值数组编码为游标。
     *
     * @param sortValues 命中结果的 {@code getSortValues()}，为空时返回 {@code null}（表示没有下一页）
     */
    public static String encode(Object[] sortValues) {
        if (sortValues == null || sortValues.length == 0) {
            return null;
        }
        try {
            return ENCODER.encodeToString(MAPPER.writeValueAsBytes(sortValues));
        } catch (Exception e) {
            // 排序值来自 ES 响应，正常情况一定能序列化；走到这里说明数据异常，按服务端错误处理
            throw new CommonException(SearchErrorInfo.INVALID_SEARCH_CURSOR, e);
        }
    }

    /**
     * 把游标解码回排序值数组。
     *
     * @throws BadRequestException 游标被篡改或格式非法时抛出，由全局异常处理器转成 400
     */
    public static Object[] decode(String cursor) {
        try {
            return MAPPER.readValue(DECODER.decode(cursor), Object[].class);
        } catch (Exception e) {
            throw new BadRequestException(SearchErrorInfo.INVALID_SEARCH_CURSOR);
        }
    }
}
