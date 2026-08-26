package com.tourlk.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consistent JSON error shape returned by {@link GlobalExceptionHandler}.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    /** Field -> message, populated only for validation errors. */
    private Map<String, String> fieldErrors;

}
