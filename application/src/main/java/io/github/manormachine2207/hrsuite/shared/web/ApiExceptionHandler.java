package io.github.manormachine2207.hrsuite.shared.web;

import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypExceptions;
import io.github.manormachine2207.hrsuite.tenant.TenantExceptions.TenantConflictException;
import io.github.manormachine2207.hrsuite.tenant.TenantExceptions.TenantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    ProblemDetail handleNotFound(TenantNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TenantConflictException.class)
    ProblemDetail handleConflict(TenantConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AntragsTypExceptions.NotFound.class)
    ProblemDetail handleAntragsTypNotFound(AntragsTypExceptions.NotFound ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AntragsTypExceptions.Conflict.class)
    ProblemDetail handleAntragsTypConflict(AntragsTypExceptions.Conflict ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AntragsTypExceptions.IllegalState.class)
    ProblemDetail handleAntragsTypIllegalState(AntragsTypExceptions.IllegalState ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AntragsTypExceptions.BreakingChange.class)
    ProblemDetail handleAntragsTypBreakingChange(AntragsTypExceptions.BreakingChange ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
