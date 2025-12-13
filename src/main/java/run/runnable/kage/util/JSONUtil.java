package run.runnable.kage.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * JSON工具类，提供对JSONObject和JSONArray的操作方法
 */
public interface JSONUtil {

    /**
     * 从JSONObject中根据路径获取JSONArray，并转换为JSONObject列表
     *
     * @param jsonObject 源JSONObject对象
     * @param path JSON查询路径
     * @return JSONObject列表，如果路径不存在则返回空列表
     */
    static List<JSONObject> optJSONArray(JSONObject jsonObject, String path){
        // 使用optQuery方法安全地获取路径对应的值，并转换为JSONArray
        // 如果路径不存在或值为null，则使用空的JSONArray替代
        JSONArray jsonArray = Optional.ofNullable(((JSONArray) jsonObject.optQuery(path)))
                .orElse(new JSONArray());
        // 将JSONArray转换为Stream，再映射为JSONObject列表
        return StreamSupport.stream(jsonArray.spliterator(), false)
                .map(it -> ((JSONObject) it))
                .collect(Collectors.toList());
    }

    /**
     * 从JSONObject中根据路径获取JSONArray，并转换为JSONObject流
     *
     * @param jsonObject 源JSONObject对象
     * @param path JSON查询路径
     * @return JSONObject流，如果路径不存在可能抛出异常
     */
    static Stream<JSONObject> optJSONArrayStream(JSONObject jsonObject, String path){
        // 使用optQuery方法获取路径对应的JSONArray
        // 将JSONArray转换为Stream，并映射为JSONObject流
        return StreamSupport.stream(((JSONArray) jsonObject.optQuery(path))
                        .spliterator(), false)
                .map(it -> ((JSONObject) it));
    }

    /**
     * 递归移除JSONObject及其嵌套结构中的指定key
     *
     * @param jsonObject 要处理的JSONObject
     * @param keyToRemove 需要移除的key名称
     */
    static void removeKey(JSONObject jsonObject, String keyToRemove) {
        // 遍历JSON对象的所有key，使用removeIf方法判断是否需要移除
        jsonObject.keySet().removeIf(key -> {
            // 如果当前key是指定要移除的key，直接返回true进行移除
            if (key.equals(keyToRemove)) {
                return true;
            }

            // 获取当前key对应的值
            Object value = jsonObject.get(key);
            // 如果值是JSONObject类型，递归调用removeKey方法处理嵌套对象
            if (value instanceof JSONObject) {
                removeKey((JSONObject) value, keyToRemove);
            }
            // 如果值是JSONArray类型，遍历数组中的每个元素
            else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.get(i);
                    // 如果数组元素是JSONObject类型，递归调用removeKey方法处理
                    if (item instanceof JSONObject) {
                        removeKey((JSONObject) item, keyToRemove);
                    }
                }
            }
            // 对于其他情况，不移除当前key
            return false;
        });
    }
}
