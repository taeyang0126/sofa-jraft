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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ConfigRequest.
 *
 * @author lei
 */
public class ConfigRequestTest {

    @Test
    public void testDefaultConstructor() {
        ConfigRequest request = new ConfigRequest();
        assertNotNull(request.getProperties());
        assertTrue(request.getProperties().isEmpty());
    }

    @Test
    public void testConstructorWithTypeNamespaceKey() {
        ConfigRequest request = new ConfigRequest(ConfigRequest.Type.GET_CONFIG, "test-namespace", "test-key");
        assertEquals(ConfigRequest.Type.GET_CONFIG, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
        assertNotNull(request.getProperties());
        assertTrue(request.getProperties().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullType() {
        new ConfigRequest(null, "test-namespace", "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullNamespace() {
        new ConfigRequest(ConfigRequest.Type.GET_CONFIG, null, "test-key");
    }

    @Test
    public void testCreatePutConfig() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");

        ConfigRequest request = ConfigRequest.createPutConfig("test-namespace", "test-key", properties);
        assertEquals(ConfigRequest.Type.PUT_CONFIG, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
        assertEquals(2, request.getProperties().size());
        assertEquals("value1", request.getProperties().get("prop1"));
        assertEquals("value2", request.getProperties().get("prop2"));
    }

    @Test
    public void testCreatePutConfigWithSingleProperty() {
        ConfigRequest request = ConfigRequest.createPutConfig("test-namespace", "test-key", "prop1", "value1");
        assertEquals(ConfigRequest.Type.PUT_CONFIG, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
        assertEquals(1, request.getProperties().size());
        assertEquals("value1", request.getProperties().get("prop1"));
    }

    @Test(expected = NullPointerException.class)
    public void testCreatePutConfigWithNullKey() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        ConfigRequest.createPutConfig("test-namespace", null, properties);
    }

    @Test(expected = NullPointerException.class)
    public void testCreatePutConfigWithNullProperties() {
        ConfigRequest.createPutConfig("test-namespace", "test-key", (Map<String, String>) null);
    }

    @Test
    public void testCreateGetConfig() {
        ConfigRequest request = ConfigRequest.createGetConfig("test-namespace", "test-key");
        assertEquals(ConfigRequest.Type.GET_CONFIG, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
    }

    @Test
    public void testCreateDeleteConfig() {
        ConfigRequest request = ConfigRequest.createDeleteConfig("test-namespace", "test-key");
        assertEquals(ConfigRequest.Type.DELETE_CONFIG, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
    }

    @Test
    public void testCreateListConfigs() {
        ConfigRequest request = ConfigRequest.createListConfigs("test-namespace");
        assertEquals(ConfigRequest.Type.LIST_CONFIGS, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertNull(request.getKey());
    }

    @Test
    public void testCreateGetConfigHistory() {
        ConfigRequest request = ConfigRequest.createGetConfigHistory("test-namespace", "test-key");
        assertEquals(ConfigRequest.Type.GET_CONFIG_HISTORY, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
    }

    @Test
    public void testCreateGetConfigByVersion() {
        ConfigRequest request = ConfigRequest.createGetConfigByVersion("test-namespace", "test-key", 5L);
        assertEquals(ConfigRequest.Type.GET_CONFIG_BY_VERSION, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
        assertEquals(5L, request.getVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateGetConfigByVersionWithInvalidVersion() {
        ConfigRequest.createGetConfigByVersion("test-namespace", "test-key", 0L);
    }

    @Test
    public void testCreateRollbackConfig() {
        ConfigRequest request = ConfigRequest.createRollbackConfig("test-namespace", "test-key", 3L);
        assertEquals(ConfigRequest.Type.ROLLBACK_CONFIG, request.getType());
        assertEquals("test-namespace", request.getNamespace());
        assertEquals("test-key", request.getKey());
        assertEquals(3L, request.getVersion());
    }

    @Test
    public void testValidatePutConfig() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        ConfigRequest request = ConfigRequest.createPutConfig("test-namespace", "test-key", properties);
        request.validate(); // Should not throw exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidatePutConfigWithEmptyProperties() {
        ConfigRequest request = new ConfigRequest(ConfigRequest.Type.PUT_CONFIG, "test-namespace", "test-key");
        request.validate();
    }

    @Test
    public void testValidateGetConfig() {
        ConfigRequest request = ConfigRequest.createGetConfig("test-namespace", "test-key");
        request.validate(); // Should not throw exception
    }

    @Test(expected = NullPointerException.class)
    public void testValidateGetConfigWithNullKey() {
        ConfigRequest request = new ConfigRequest(ConfigRequest.Type.GET_CONFIG, "test-namespace", null);
        request.validate();
    }

    @Test
    public void testValidateListConfigs() {
        ConfigRequest request = ConfigRequest.createListConfigs("test-namespace");
        request.validate(); // Should not throw exception
    }

    @Test
    public void testValidateGetConfigByVersion() {
        ConfigRequest request = ConfigRequest.createGetConfigByVersion("test-namespace", "test-key", 5L);
        request.validate(); // Should not throw exception
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateGetConfigByVersionWithInvalidVersion() {
        ConfigRequest request = new ConfigRequest(ConfigRequest.Type.GET_CONFIG_BY_VERSION, "test-namespace",
            "test-key");
        request.setVersion(0L);
        request.validate();
    }

    @Test
    public void testEqualsAndHashCode() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");

        ConfigRequest request1 = ConfigRequest.createPutConfig("test-namespace", "test-key", properties);
        ConfigRequest request2 = ConfigRequest.createPutConfig("test-namespace", "test-key", properties);

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());

        // Test inequality
        request2.setVersion(5L);
        assertNotEquals(request1, request2);
    }

    @Test
    public void testToString() {
        ConfigRequest request = ConfigRequest.createPutConfig("test-namespace", "test-key", "prop1", "value1");
        String str = request.toString();
        assertTrue(str.contains("PUT_CONFIG"));
        assertTrue(str.contains("test-namespace"));
        assertTrue(str.contains("test-key"));
        assertTrue(str.contains("prop1"));
        assertTrue(str.contains("value1"));
    }
}