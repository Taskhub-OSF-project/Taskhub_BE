package com.taskhub.lambda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamLambdaHandlerSecurityTest {
    @Test
    void rejectsUnconfiguredOriginsInsteadOfReflectingThem() {
        assertFalse(StreamLambdaHandler.isOriginAllowed("https://attacker.example"));
    }

    @Test
    void acceptsConfiguredDevelopmentOriginInDefaultLocalEnvironment() {
        assertTrue(StreamLambdaHandler.isOriginAllowed("http://localhost:5173"));
    }
}
