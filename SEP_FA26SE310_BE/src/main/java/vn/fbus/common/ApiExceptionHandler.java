package vn.fbus.common;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> domain(ApiException error) { return response(error.status, error.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error) {
        var field = error.getBindingResult().getFieldErrors().getFirst();
        return response(400, field.getField() + ": " + field.getDefaultMessage());
    }
    @ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> invalid(Exception error) { return response(400, "Dữ liệu yêu cầu không hợp lệ."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> conflict(Exception error) { return response(409, "Mã đã tồn tại hoặc dữ liệu bị trùng. Vui lòng tải lại."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> largeFile(Exception error) { return response(413,"Tệp Excel tối đa 2 MB."); }
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiResponse<Void>> missingFile(Exception error) { return response(400,"Chọn tệp Excel để nhập."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error) {
        LoggerFactory.getLogger(getClass()).error("Request failed", error);
        return response(500, "Không thể xử lý yêu cầu. Vui lòng thử lại.");
    }
    private ResponseEntity<ApiResponse<Void>> response(int status, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(status, message, null));
    }
}
