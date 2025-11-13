package com.jjenus.qliina_management.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, WebRequest request) {
        log.error("Business exception: {}", ex.getMessage(), ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Business Rule Violation");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/business-rule"));
        problemDetail.setProperty("errorCode", ex.getErrorCode());
        problemDetail.setProperty("field", ex.getField());
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
            .collect(Collectors.toMap(
                error -> ((FieldError) error).getField(),
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                (existing, replacement) -> existing
            ));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/validation"));
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> violation.getMessage(),
                (existing, replacement) -> existing
            ));

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Constraint violation");
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/constraint"));
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = String.format("Parameter '%s' should be of type %s", 
            ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, message);
        problemDetail.setTitle("Invalid Parameter");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/invalid-parameter"));
        problemDetail.setProperty("errorCode", "INVALID_PARAMETER");
        problemDetail.setProperty("parameter", ex.getName());
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Invalid username or password");
        problemDetail.setTitle("Authentication Failed");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/invalid-credentials"));
        problemDetail.setProperty("errorCode", "INVALID_CREDENTIALS");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "Access denied");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/access-denied"));
        problemDetail.setProperty("errorCode", "ACCESS_DENIED");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.laundry.com/errors/internal-server-error"));
        problemDetail.setProperty("errorCode", "INTERNAL_ERROR");
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", extractPath(request));
        
        return problemDetail;
    }

    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        return description != null && description.startsWith("uri=") 
            ? description.substring(4) 
            : description;
    }
}
