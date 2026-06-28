package org.betaiotazeta.autoshiftplanner.backend.rest;

import org.betaiotazeta.autoshiftplanner.ShiftConversionException;
import org.betaiotazeta.autoshiftplanner.backend.service.ScheduleNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain/lookup failures into RFC 7807 {@link ProblemDetail} responses. Bean-validation
 * failures on the request body are already turned into 400 ProblemDetails by Spring's default
 * handling; this advice covers the cases the controller surfaces explicitly.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** A pre-worked region could not be converted into a shift given the constraint settings. */
    @ExceptionHandler(ShiftConversionException.class)
    public ProblemDetail handleShiftConversion(ShiftConversionException ex) {
        return badRequest(ex.getMessage());
    }

    /** Out-of-range cell coordinates or inconsistent dimensions in the request. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(ex.getMessage());
    }

    /** A path variable could not be bound (e.g. a malformed job-id UUID). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid value for '" + ex.getName() + "'.");
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ProblemDetail handleNotFound(ScheduleNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    private ProblemDetail badRequest(String detail) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
