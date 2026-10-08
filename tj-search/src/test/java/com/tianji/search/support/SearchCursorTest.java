package com.tianji.search.support;

import com.tianji.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchCursorTest {

    @Test
    void encode_returnsNullWhenThereIsNoSortValue() {
        assertNull(SearchCursor.encode(null), "没有排序值时应表示没有下一页");
        assertNull(SearchCursor.encode(new Object[0]));
    }

    @Test
    void encode_thenDecode_preservesValuesAndOrder() {
        Object[] sortValues = {1.0f, 1_204_101L};

        String cursor = SearchCursor.encode(sortValues);

        assertNotNull(cursor);
        // 必须是 URL 安全且无填充的，才能直接作为 query 参数传递
        assertFalse(cursor.contains("+"));
        assertFalse(cursor.contains("/"));
        assertFalse(cursor.contains("="));

        Object[] decoded = SearchCursor.decode(cursor);
        assertEquals(2, decoded.length);
        // JSON 往返后数值类型可能窄化（float->double、long->int），但数值本身必须一致，
        // 最终由 ES 按字段映射做类型转换，不影响 search_after 的定位
        assertEquals(1.0d, ((Number) decoded[0]).doubleValue(), 1e-6);
        assertEquals(1_204_101L, ((Number) decoded[1]).longValue());
    }

    @Test
    void encode_thenDecode_keepsStringSortValues() {
        String cursor = SearchCursor.encode(new Object[]{"Java实战课", 9L});
        Object[] decoded = SearchCursor.decode(cursor);
        assertEquals("Java实战课", decoded[0]);
        assertEquals(9L, ((Number) decoded[1]).longValue());
    }

    @Test
    void decode_throwsBadRequestWhenCursorIsNotBase64() {
        assertThrows(BadRequestException.class, () -> SearchCursor.decode("!!!not-a-cursor!!!"));
    }

    @Test
    void decode_throwsBadRequestWhenPayloadIsNotAJsonArray() {
        String bogus = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        assertThrows(BadRequestException.class, () -> SearchCursor.decode(bogus));
    }
}
