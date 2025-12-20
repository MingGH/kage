package run.runnable.kage.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class JSONUtilTest {

    @Test
    @DisplayName("optJSONArray 测试")
    void optJSONArray_shouldReturnList() {
        String jsonStr = "{\"data\": [{\"id\": 1}, {\"id\": 2}]}";
        JSONObject jsonObject = new JSONObject(jsonStr);
        
        List<JSONObject> result = JSONUtil.optJSONArray(jsonObject, "/data");
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getInt("id"));
        assertEquals(2, result.get(1).getInt("id"));
        
        List<JSONObject> empty = JSONUtil.optJSONArray(jsonObject, "/notexist");
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("optJSONArrayStream 测试")
    void optJSONArrayStream_shouldReturnStream() {
        String jsonStr = "{\"data\": [{\"id\": 1}, {\"id\": 2}]}";
        JSONObject jsonObject = new JSONObject(jsonStr);
        
        Stream<JSONObject> stream = JSONUtil.optJSONArrayStream(jsonObject, "/data");
        List<JSONObject> result = stream.collect(Collectors.toList());
        
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getInt("id"));
    }

    @Test
    @DisplayName("removeKey 测试 - 简单移除")
    void removeKey_shouldRemoveSimpleKey() {
        String jsonStr = "{\"keep\": 1, \"remove\": 2}";
        JSONObject jsonObject = new JSONObject(jsonStr);
        
        JSONUtil.removeKey(jsonObject, "remove");
        
        assertTrue(jsonObject.has("keep"));
        assertFalse(jsonObject.has("remove"));
    }

    @Test
    @DisplayName("removeKey 测试 - 递归移除")
    void removeKey_shouldRemoveRecursively() {
        String jsonStr = "{\"a\": {\"remove\": 1, \"keep\": 2}, \"b\": [{\"remove\": 3, \"keep\": 4}]}";
        JSONObject jsonObject = new JSONObject(jsonStr);
        
        JSONUtil.removeKey(jsonObject, "remove");
        
        // Check nested object
        JSONObject a = jsonObject.getJSONObject("a");
        assertFalse(a.has("remove"));
        assertTrue(a.has("keep"));
        
        // Check array
        JSONArray b = jsonObject.getJSONArray("b");
        JSONObject item = b.getJSONObject(0);
        assertFalse(item.has("remove"));
        assertTrue(item.has("keep"));
    }
}
