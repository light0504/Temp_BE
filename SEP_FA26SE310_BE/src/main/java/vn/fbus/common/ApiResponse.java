package vn.fbus.common;

public record ApiResponse<T>(int statusCode, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(200, "Success", data); }
}
