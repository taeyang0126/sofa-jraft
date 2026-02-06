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

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ConfigResponse.
 *
 * @author lei
 */
public class ConfigResponseTest {

    @Test
    public void testDefaultConstructor() {
        ConfigResponse response = new ConfigResponse();
        assertFalse(response.isSuccess());
        assertNull(response.getErrorMsg());
        assertNull(response.getConfigEntry());
        assertNotNull(response.getConfigEntries());
        assertTrue(response.getConfigEntries().isEmpty());
    }

    @Test
    public void testConstructorWithSuccess() {
        ConfigResponse response = new ConfigResponse(true);
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMsg());
    }

    @Test
    public void testConstructorWithSuccessAndErrorMsg() {
        ConfigResponse response = new ConfigResponse(false, "Test error");
        assertFalse(response.isSuccess());
        assertEquals("Test error", response.getErrorMsg());
    }

    @Test
    public void testSuccessWithSingleEntry() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        ConfigResponse response = ConfigResponse.success(entry);

        assertTrue(response.isSuccess());
        assertNull(response.getErrorMsg());
        assertEquals(entry, response.getConfigEntry());
        assertTrue(response.hasConfigEntry());
        assertEquals(1, response.getConfigEntryCount());
    }

    @Test(expected = NullPointerException.class)
    public void testSuccessWithNullEntry() {
        ConfigResponse.success((ConfigEntry) null);
    }

    @Test
    public void testSuccessWithMultipleEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("test-namespace", "test-key1"));
        entries.add(new ConfigEntry("test-namespace", "test-key2"));

        ConfigResponse response = ConfigResponse.success(entries);

        assertTrue(response.isSuccess());
        assertNull(response.getErrorMsg());
        assertEquals(2, response.getConfigEntries().size());
        assertTrue(response.hasConfigEntries());
        assertEquals(2, response.getConfigEntryCount());
    }

    @Test(expected = NullPointerException.class)
    public void testSuccessWithNullEntries() {
        ConfigResponse.success((List<ConfigEntry>) null);
    }

    @Test
    public void testSuccessWithoutData() {
        ConfigResponse response = ConfigResponse.success();
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMsg());
        assertNull(response.getConfigEntry());
        assertTrue(response.getConfigEntries().isEmpty());
        assertEquals(0, response.getConfigEntryCount());
    }

    @Test
    public void testErrorWithMessage() {
        ConfigResponse response = ConfigResponse.error("Test error message");
        assertFalse(response.isSuccess());
        assertEquals("Test error message", response.getErrorMsg());
        assertNull(response.getConfigEntry());
        assertTrue(response.getConfigEntries().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testErrorWithNullMessage() {
        ConfigResponse.error(null);
    }

    @Test
    public void testErrorWithMessageAndCause() {
        Exception cause = new RuntimeException("Cause message");
        ConfigResponse response = ConfigResponse.error("Test error", cause);
        assertFalse(response.isSuccess());
        assertEquals("Test error: Cause message", response.getErrorMsg());
    }

    @Test
    public void testErrorWithMessageAndNullCause() {
        ConfigResponse response = ConfigResponse.error("Test error", null);
        assertFalse(response.isSuccess());
        assertEquals("Test error", response.getErrorMsg());
    }

    @Test
    public void testErrorWithMessageAndCauseWithoutMessage() {
        Exception cause = new RuntimeException();
        ConfigResponse response = ConfigResponse.error("Test error", cause);
        assertFalse(response.isSuccess());
        assertEquals("Test error", response.getErrorMsg());
    }

    @Test
    public void testAddConfigEntry() {
        ConfigResponse response = new ConfigResponse();
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");

        response.addConfigEntry(entry);
        assertEquals(1, response.getConfigEntries().size());
        assertEquals(entry, response.getConfigEntries().get(0));
        assertTrue(response.hasConfigEntries());
        assertEquals(1, response.getConfigEntryCount());
    }

    @Test
    public void testAddNullConfigEntry() {
        ConfigResponse response = new ConfigResponse();
        response.addConfigEntry(null);
        assertTrue(response.getConfigEntries().isEmpty());
        assertEquals(0, response.getConfigEntryCount());
    }

    @Test
    public void testHasConfigEntry() {
        ConfigResponse response = new ConfigResponse();
        assertFalse(response.hasConfigEntry());

        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        response.setConfigEntry(entry);
        assertTrue(response.hasConfigEntry());
    }

    @Test
    public void testHasConfigEntries() {
        ConfigResponse response = new ConfigResponse();
        assertFalse(response.hasConfigEntries());

        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        response.addConfigEntry(entry);
        assertTrue(response.hasConfigEntries());
    }

    @Test
    public void testGetConfigEntryCount() {
        ConfigResponse response = new ConfigResponse();
        assertEquals(0, response.getConfigEntryCount());

        // Test with single entry
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        response.setConfigEntry(entry);
        assertEquals(1, response.getConfigEntryCount());

        // Test with multiple entries
        response.setConfigEntry(null);
        response.addConfigEntry(entry);
        response.addConfigEntry(new ConfigEntry("test-namespace", "test-key2"));
        assertEquals(2, response.getConfigEntryCount());
    }

    @Test
    public void testSetConfigEntries() {
        ConfigResponse response = new ConfigResponse();
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("test-namespace", "test-key1"));
        entries.add(new ConfigEntry("test-namespace", "test-key2"));

        response.setConfigEntries(entries);
        assertEquals(2, response.getConfigEntries().size());

        // Test with null
        response.setConfigEntries(null);
        assertNotNull(response.getConfigEntries());
        assertTrue(response.getConfigEntries().isEmpty());
    }

    @Test
    public void testEqualsAndHashCode() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        ConfigResponse response1 = ConfigResponse.success(entry);
        ConfigResponse response2 = ConfigResponse.success(entry);

        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());

        // Test inequality
        ConfigResponse response3 = ConfigResponse.error("Error message");
        assertNotEquals(response1, response3);
    }

    @Test
    public void testToString() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        ConfigResponse response = ConfigResponse.success(entry);
        String str = response.toString();
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("test-namespace"));
        assertTrue(str.contains("test-key"));
    }
}