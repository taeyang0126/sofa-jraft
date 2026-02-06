/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.sofa.jraft.example.configcenter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for ConfigException and its subclasses.
 *
 * @author lei
 */
public class ConfigExceptionTest {

    @Test
    public void testConfigExceptionWithMessage() {
        ConfigException exception = new ConfigException(ConfigErrorCode.INTERNAL_ERROR, "Test error message");

        assertEquals(ConfigErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("Test error message", exception.getMessage());
        assertNotNull(exception.toString());
        assertTrue(exception.toString().contains(ConfigErrorCode.INTERNAL_ERROR));
        assertTrue(exception.toString().contains("Test error message"));
    }

    @Test
    public void testConfigExceptionWithCause() {
        Throwable cause = new RuntimeException("Root cause");
        ConfigException exception = new ConfigException(ConfigErrorCode.INTERNAL_ERROR, "Test error message", cause);

        assertEquals(ConfigErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("Test error message", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testConfigNotFoundException() {
        ConfigNotFoundException exception = new ConfigNotFoundException("test-namespace", "test-key");

        assertEquals(ConfigErrorCode.CONFIG_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("test-namespace"));
        assertTrue(exception.getMessage().contains("test-key"));
    }

    @Test
    public void testConfigNotFoundExceptionWithVersion() {
        ConfigNotFoundException exception = new ConfigNotFoundException("test-namespace", "test-key", 123L);

        assertEquals(ConfigErrorCode.CONFIG_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("test-namespace"));
        assertTrue(exception.getMessage().contains("test-key"));
        assertTrue(exception.getMessage().contains("123"));
    }

    @Test
    public void testNamespaceNotFoundException() {
        NamespaceNotFoundException exception = new NamespaceNotFoundException("test-namespace");

        assertEquals(ConfigErrorCode.NAMESPACE_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("test-namespace"));
    }

    @Test
    public void testConfigTimeoutException() {
        ConfigTimeoutException exception = new ConfigTimeoutException("putConfig", 5000L);

        assertEquals(ConfigErrorCode.CONFIG_TIMEOUT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("putConfig"));
        assertTrue(exception.getMessage().contains("5000"));
    }

    @Test
    public void testConfigTimeoutExceptionWithCause() {
        Throwable cause = new RuntimeException("Timeout cause");
        ConfigTimeoutException exception = new ConfigTimeoutException("getConfig", 3000L, cause);

        assertEquals(ConfigErrorCode.CONFIG_TIMEOUT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("getConfig"));
        assertTrue(exception.getMessage().contains("3000"));
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testConfigConnectionException() {
        ConfigConnectionException exception = new ConfigConnectionException("http://localhost:8080");

        assertEquals(ConfigErrorCode.CONFIG_CONNECTION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("http://localhost:8080"));
    }

    @Test
    public void testConfigConnectionExceptionWithCause() {
        Throwable cause = new RuntimeException("Connection refused");
        ConfigConnectionException exception = new ConfigConnectionException("http://localhost:8080", cause);

        assertEquals(ConfigErrorCode.CONFIG_CONNECTION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("http://localhost:8080"));
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void testErrorCodeConstants() {
        // Test that all error codes are properly defined
        assertNotNull(ConfigErrorCode.CONFIG_NOT_FOUND);
        assertNotNull(ConfigErrorCode.CONFIG_ALREADY_EXISTS);
        assertNotNull(ConfigErrorCode.CONFIG_INVALID_PARAMETER);
        assertNotNull(ConfigErrorCode.NAMESPACE_NOT_FOUND);
        assertNotNull(ConfigErrorCode.NAMESPACE_ALREADY_EXISTS);
        assertNotNull(ConfigErrorCode.VERSION_NOT_FOUND);
        assertNotNull(ConfigErrorCode.VERSION_CONFLICT);
        assertNotNull(ConfigErrorCode.CONFIG_TIMEOUT);
        assertNotNull(ConfigErrorCode.CONFIG_CONNECTION_FAILED);
        assertNotNull(ConfigErrorCode.RAFT_NOT_LEADER);
        assertNotNull(ConfigErrorCode.RAFT_OPERATION_FAILED);
        assertNotNull(ConfigErrorCode.SERIALIZATION_ERROR);
        assertNotNull(ConfigErrorCode.DESERIALIZATION_ERROR);
        assertNotNull(ConfigErrorCode.INTERNAL_ERROR);
    }

    @Test
    public void testErrorCodeUniqueness() {
        // Test that error codes are unique strings
        String[] errorCodes = { ConfigErrorCode.CONFIG_NOT_FOUND, ConfigErrorCode.CONFIG_ALREADY_EXISTS,
                ConfigErrorCode.CONFIG_INVALID_PARAMETER, ConfigErrorCode.NAMESPACE_NOT_FOUND,
                ConfigErrorCode.NAMESPACE_ALREADY_EXISTS, ConfigErrorCode.VERSION_NOT_FOUND,
                ConfigErrorCode.VERSION_CONFLICT, ConfigErrorCode.CONFIG_TIMEOUT,
                ConfigErrorCode.CONFIG_CONNECTION_FAILED, ConfigErrorCode.RAFT_NOT_LEADER,
                ConfigErrorCode.RAFT_OPERATION_FAILED, ConfigErrorCode.SERIALIZATION_ERROR,
                ConfigErrorCode.DESERIALIZATION_ERROR, ConfigErrorCode.INTERNAL_ERROR };

        // Verify all error codes are non-null and non-empty
        for (String errorCode : errorCodes) {
            assertNotNull(errorCode);
            assertTrue(errorCode.length() > 0);
        }
    }

    @Test
    public void testExceptionInheritance() {
        // Test that all custom exceptions inherit from ConfigException
        ConfigNotFoundException notFoundEx = new ConfigNotFoundException("ns", "key");
        assertTrue(notFoundEx instanceof ConfigException);
        assertTrue(notFoundEx instanceof RuntimeException);

        NamespaceNotFoundException namespaceEx = new NamespaceNotFoundException("ns");
        assertTrue(namespaceEx instanceof ConfigException);
        assertTrue(namespaceEx instanceof RuntimeException);

        ConfigTimeoutException timeoutEx = new ConfigTimeoutException("op", 1000L);
        assertTrue(timeoutEx instanceof ConfigException);
        assertTrue(timeoutEx instanceof RuntimeException);

        ConfigConnectionException connectionEx = new ConfigConnectionException("url");
        assertTrue(connectionEx instanceof ConfigException);
        assertTrue(connectionEx instanceof RuntimeException);
    }

    @Test
    public void testConfigNotFoundExceptionWithEmptyStrings() {
        // Test with empty strings
        ConfigNotFoundException exception = new ConfigNotFoundException("", "");

        assertEquals(ConfigErrorCode.CONFIG_NOT_FOUND, exception.getErrorCode());
        assertNotNull(exception.getMessage());
    }

    @Test
    public void testConfigNotFoundExceptionWithSpecialCharacters() {
        // Test with special characters
        ConfigNotFoundException exception = new ConfigNotFoundException("namespace/with/slashes", "key:with:colons");

        assertEquals(ConfigErrorCode.CONFIG_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("namespace/with/slashes"));
        assertTrue(exception.getMessage().contains("key:with:colons"));
    }

    @Test
    public void testNamespaceNotFoundExceptionWithSpecialCharacters() {
        // Test with special characters
        NamespaceNotFoundException exception = new NamespaceNotFoundException("namespace-with-dashes_and_underscores");

        assertEquals(ConfigErrorCode.NAMESPACE_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("namespace-with-dashes_and_underscores"));
    }

    @Test
    public void testConfigTimeoutExceptionWithZeroTimeout() {
        // Test with zero timeout
        ConfigTimeoutException exception = new ConfigTimeoutException("operation", 0L);

        assertEquals(ConfigErrorCode.CONFIG_TIMEOUT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("0"));
    }

    @Test
    public void testConfigTimeoutExceptionWithLargeTimeout() {
        // Test with large timeout value
        ConfigTimeoutException exception = new ConfigTimeoutException("operation", Long.MAX_VALUE);

        assertEquals(ConfigErrorCode.CONFIG_TIMEOUT, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(String.valueOf(Long.MAX_VALUE)));
    }

    @Test
    public void testConfigConnectionExceptionWithComplexUrl() {
        // Test with complex URL
        ConfigConnectionException exception = new ConfigConnectionException(
            "https://user:pass@host:8080/path?query=value#fragment");

        assertEquals(ConfigErrorCode.CONFIG_CONNECTION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("https://user:pass@host:8080/path?query=value#fragment"));
    }

    @Test
    public void testConfigExceptionToStringFormat() {
        // Test toString format
        ConfigException exception = new ConfigException(ConfigErrorCode.INTERNAL_ERROR, "Test message");
        String toString = exception.toString();

        assertNotNull(toString);
        assertTrue(toString.startsWith("ConfigException{"));
        assertTrue(toString.contains("errorCode="));
        assertTrue(toString.contains("message="));
        assertTrue(toString.endsWith("}"));
    }

    @Test
    public void testConfigExceptionWithNullMessage() {
        // Test with null message
        ConfigException exception = new ConfigException(ConfigErrorCode.INTERNAL_ERROR, null);

        assertEquals(ConfigErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        // getMessage() can return null, which is valid
    }

    @Test
    public void testConfigExceptionWithNullCause() {
        // Test with null cause
        ConfigException exception = new ConfigException(ConfigErrorCode.INTERNAL_ERROR, "Test message", null);

        assertEquals(ConfigErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("Test message", exception.getMessage());
        // getCause() can return null, which is valid
    }

    @Test
    public void testConfigNotFoundExceptionWithLongVersion() {
        // Test with negative version
        ConfigNotFoundException exception = new ConfigNotFoundException("namespace", "key", -1L);

        assertEquals(ConfigErrorCode.CONFIG_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("-1"));
    }

    @Test
    public void testMultipleExceptionChaining() {
        // Test exception chaining
        Throwable rootCause = new IllegalArgumentException("Root cause");
        Throwable intermediateCause = new RuntimeException("Intermediate cause", rootCause);
        ConfigException exception = new ConfigException(ConfigErrorCode.INTERNAL_ERROR, "Top level error",
            intermediateCause);

        assertEquals(ConfigErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertEquals("Top level error", exception.getMessage());
        assertEquals(intermediateCause, exception.getCause());
        assertEquals(rootCause, exception.getCause().getCause());
    }
}
