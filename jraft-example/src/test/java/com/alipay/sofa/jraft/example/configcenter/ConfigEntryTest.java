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
 * Unit tests for ConfigEntry.
 *
 * @author lei
 */
public class ConfigEntryTest {

    @Test
    public void testDefaultConstructor() {
        ConfigEntry entry = new ConfigEntry();
        assertNotNull(entry.getProperties());
        assertTrue(entry.getProperties().isEmpty());
        assertEquals(1L, entry.getVersion());
        assertFalse(entry.isDeleted());
        assertTrue(entry.getCreateTime() > 0);
        assertTrue(entry.getUpdateTime() > 0);
    }

    @Test
    public void testConstructorWithNamespaceAndKey() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        assertEquals("test-namespace", entry.getNamespace());
        assertEquals("test-key", entry.getKey());
        assertNotNull(entry.getProperties());
        assertTrue(entry.getProperties().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullNamespace() {
        new ConfigEntry(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullKey() {
        new ConfigEntry("test-namespace", null);
    }

    @Test
    public void testConstructorWithProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        properties.put("prop2", "value2");

        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key", properties);
        assertEquals("test-namespace", entry.getNamespace());
        assertEquals("test-key", entry.getKey());
        assertEquals(2, entry.getProperties().size());
        assertEquals("value1", entry.getProperty("prop1"));
        assertEquals("value2", entry.getProperty("prop2"));
    }

    @Test
    public void testCopyConstructor() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");
        ConfigEntry original = new ConfigEntry("test-namespace", "test-key", properties);
        original.setVersion(5L);

        ConfigEntry copy = new ConfigEntry(original);
        assertEquals(original.getNamespace(), copy.getNamespace());
        assertEquals(original.getKey(), copy.getKey());
        assertEquals(original.getProperties(), copy.getProperties());
        assertEquals(6L, copy.getVersion()); // version should be incremented
        assertEquals(original.getCreateTime(), copy.getCreateTime());
        assertTrue(copy.getUpdateTime() >= original.getUpdateTime());
        assertFalse(copy.isDeleted());
    }

    @Test
    public void testPropertyOperations() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");

        // Test setting property
        entry.setProperty("prop1", "value1");
        assertEquals("value1", entry.getProperty("prop1"));
        assertTrue(entry.hasProperties());
        assertEquals(1, entry.getPropertyKeys().size());

        // Test updating property
        entry.setProperty("prop1", "updated-value1");
        assertEquals("updated-value1", entry.getProperty("prop1"));
        assertEquals(1, entry.getPropertyKeys().size());

        // Test removing property by setting null
        entry.setProperty("prop1", null);
        assertNull(entry.getProperty("prop1"));
        assertFalse(entry.hasProperties());
        assertEquals(0, entry.getPropertyKeys().size());
    }

    @Test(expected = NullPointerException.class)
    public void testSetPropertyWithNullKey() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        entry.setProperty(null, "value");
    }

    @Test
    public void testUniqueId() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        assertEquals("test-namespace:test-key", entry.getUniqueId());
    }

    @Test
    public void testEqualsAndHashCode() {
        Map<String, String> properties = new HashMap<>();
        properties.put("prop1", "value1");

        ConfigEntry entry1 = new ConfigEntry("test-namespace", "test-key", properties);
        ConfigEntry entry2 = new ConfigEntry("test-namespace", "test-key", properties);
        entry2.setVersion(entry1.getVersion());
        entry2.setCreateTime(entry1.getCreateTime());
        entry2.setUpdateTime(entry1.getUpdateTime());

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());

        // Test inequality
        entry2.setVersion(entry1.getVersion() + 1);
        assertNotEquals(entry1, entry2);
    }

    @Test
    public void testToString() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        entry.setProperty("prop1", "value1");
        String str = entry.toString();
        assertTrue(str.contains("test-namespace"));
        assertTrue(str.contains("test-key"));
        assertTrue(str.contains("prop1"));
        assertTrue(str.contains("value1"));
    }
}