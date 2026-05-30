package io.github.manormachine2207.hrsuite.shared.web;

import io.github.manormachine2207.hrsuite.antrag.AntragExceptions;
import io.github.manormachine2207.hrsuite.antragstyp.AntragsTypExceptions;
import io.github.manormachine2207.hrsuite.shared.tenant.MissingTenantContextException;
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

    @ExceptionHandler(AntragExceptions.NotFound.class)
    ProblemDetail handleAntragNotFound(AntragExceptions.NotFound ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AntragExceptions.IllegalState.class)
    ProblemDetail handleAntragIllegalState(AntragExceptions.IllegalState ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MissingTenantContextException.class)
    ProblemDetail handleMissingTenant(MissingTenantContextException ex) {
        // Authenticated, tenant-scoped role, but no tenant_id in context (ADR-008):
        // a token/client problem, not a server fault -> 403, not 500.
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
