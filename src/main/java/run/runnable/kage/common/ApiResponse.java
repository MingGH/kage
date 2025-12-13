package run.runnable.kage.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

// 使用 @JsonInclude(JsonInclude.Include.NON_NULL) 可以去除返回中为 null 的字段
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    // 响应状态码，例如 200、400、500 等
    private int status;

    // 提示信息，例如 "成功"、"请求参数错误" 等
    private String message;

    // 返回数据，可以是任何类型
    private T data;

    // 响应时间戳
    private LocalDateTime timestamp;

    // 构造函数
    public ApiResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // 工厂方法 - 构建成功响应
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    // 工厂方法 - 构建成功响应，可自定义消息
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    // 工厂方法 - 构建错误响应
    public static <T> ApiResponse<T> error(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }

    // Getters 和 Setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
