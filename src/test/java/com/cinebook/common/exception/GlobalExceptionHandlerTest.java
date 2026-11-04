package com.cinebook.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    @Test
    void databaseErrorsAreNotReportedAsExpiredAuthentication() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleDataAccessFailure(
                new BadSqlGrammarException("feed", "select invalid", null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertEquals("The request could not be completed.", response.getDetail());
    }

    @Test
    void googleEmailConflictReturnsSafeAccountLinkingInstructions() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleIllegalState(new IllegalStateException(
                "An account already uses this email. Sign in with that account before linking Google."));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals("Sign in with your existing CineBook password, then connect Google from Profile. Your CineBook data stays with that account.", response.getDetail());
    }
}
