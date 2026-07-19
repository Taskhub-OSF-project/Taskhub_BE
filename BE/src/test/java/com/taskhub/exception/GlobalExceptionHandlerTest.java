package com.taskhub.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    @Test
    void malformedJsonReturnsBadRequestWithoutLeakingParserDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleMalformedJson(mock(HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed JSON request");
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_REQUEST");
    }
}
