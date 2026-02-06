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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for MemoryConfigStorage.
 * Tests boundary conditions, error handling, and version management.
 *
 * @author lei
 */
public class MemoryConfigStorageTest {

    private MemoryConfigStorage storage;

    @Before
    public void setUp() {
        storage = new MemoryConfigStorage();
    }

    // ========== Null Parameter Tests ==========

    @Test(expected = NullPointerException.class)
    public void testPutConfigWithNullNamespace() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        storage.putConfig(null, "test-key", entry);
    }

    @Test(expected = NullPointerException.class)
    public void testPutConfigWithNullKey() {
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        storage.putConfig("test-namespace", null, entry);
    }

    @Test(expected = NullPointerException.class)
    public void testPutConfigWithNullEntry() {
        storage.putConfig("test-namespace", "test-key", null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigWithNullNamespace() {
        storage.getConfig(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigWithNullKey() {
        storage.getConfig("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteConfigWithNullNamespace() {
        storage.deleteConfig(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteConfigWithNullKey() {
        storage.deleteConfig("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testListConfigsWithNullNamespace() {
        storage.listConfigs(null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigHistoryWithNullNamespace() {
        storage.getConfigHistory(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigHistoryWithNullKey() {
        storage.getConfigHistory("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigByVersionWithNullNamespace() {
        storage.getConfigByVersion(null, "test-key", 1L);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigByVersionWithNullKey() {
        storage.getConfigByVersion("test-namespace", null, 1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConfigByVersionWithZeroVersion() {
        storage.getConfigByVersion("test-namespace", "test-key", 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConfigByVersionWithNegativeVersion() {
        storage.getConfigByVersion("test-namespace", "test-key", -1L);
    }

    @Test(expected = NullPointerException.class)
    public void testSaveSnapshotWithNullOutput() throws IOException {
        storage.saveSnapshot(null);
    }

    @Test(expected = NullPointerException.class)
    public void testLoadSnapshotWithNullInput() throws IOException {
        storage.loadSnapshot(null);
    }

    // ========== Empty/Non-existent Data Tests ==========

    @Test
    public void testGetConfigFromEmptyStorage() {
        ConfigEntry entry = storage.getConfig("test-namespace", "test-key");
        assertNull("Getting config from empty storage should return null", entry);
    }

    @Test
    public void testGetConfigFromNonExistentNamespace() {
        // Add config to one namespace
        ConfigEntry entry = new ConfigEntry("namespace1", "key1");
        storage.putConfig("namespace1", "key1", entry);

        // Try to get from different namespace
        ConfigEntry result = storage.getConfig("namespace2", "key1");
        assertNull("Getting config from non-existent namespace should return null", result);
    }

    @Test
    public void testGetConfigWithNonExistentKey() {
        // Add config with one key
        ConfigEntry entry = new ConfigEntry("test-namespace", "key1");
        storage.putConfig("test-namespace", "key1", entry);

        // Try to get different key
        ConfigEntry result = storage.getConfig("test-namespace", "key2");
        assertNull("Getting non-existent key should return null", result);
    }

    @Test
    public void testDeleteConfigFromEmptyStorage() {
        boolean deleted = storage.deleteConfig("test-namespace", "test-key");
        assertFalse("Deleting from empty storage should return false", deleted);
    }

    @Test
    public void testDeleteConfigFromNonExistentNamespace() {
        // Add config to one namespace
        ConfigEntry entry = new ConfigEntry("namespace1", "key1");
        storage.putConfig("namespace1", "key1", entry);

        // Try to delete from different namespace
        boolean deleted = storage.deleteConfig("namespace2", "key1");
        assertFalse("Deleting from non-existent namespace should return false", deleted);
    }

    @Test
    public void testDeleteConfigWithNonExistentKey() {
        // Add config with one key
        ConfigEntry entry = new ConfigEntry("test-namespace", "key1");
        storage.putConfig("test-namespace", "key1", entry);

        // Try to delete different key
        boolean deleted = storage.deleteConfig("test-namespace", "key2");
        assertFalse("Deleting non-existent key should return false", deleted);
    }

    @Test
    public void testListConfigsFromEmptyStorage() {
        List<ConfigEntry> configs = storage.listConfigs("test-namespace");
        assertNotNull("List should not return null", configs);
        assertTrue("List from empty storage should be empty", configs.isEmpty());
    }

    @Test
    public void testListConfigsFromNonExistentNamespace() {
        // Add config to one namespace
        ConfigEntry entry = new ConfigEntry("namespace1", "key1");
        storage.putConfig("namespace1", "key1", entry);

        // List from different namespace
        List<ConfigEntry> configs = storage.listConfigs("namespace2");
        assertNotNull("List should not return null", configs);
        assertTrue("List from non-existent namespace should be empty", configs.isEmpty());
    }

    @Test
    public void testGetConfigHistoryFromEmptyStorage() {
        List<ConfigEntry> history = storage.getConfigHistory("test-namespace", "test-key");
        assertNotNull("History should not return null", history);
        assertTrue("History from empty storage should be empty", history.isEmpty());
    }

    @Test
    public void testGetConfigByVersionFromEmptyStorage() {
        ConfigEntry entry = storage.getConfigByVersion("test-namespace", "test-key", 1L);
        assertNull("Getting version from empty storage should return null", entry);
    }

    @Test
    public void testGetConfigByVersionWithNonExistentVersion() {
        // Create config with version 1
        ConfigEntry entry = new ConfigEntry("test-namespace", "test-key");
        storage.putConfig("test-namespace", "test-key", entry);

        // Try to get version 2
        ConfigEntry result = storage.getConfigByVersion("test-namespace", "test-key", 2L);
        assertNull("Getting non-existent version should return null", result);
    }

    // ========== Version Management Tests ==========

    @Test
    public void testVersionIncrementOnUpdate() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create initial config
        ConfigEntry entry1 = new ConfigEntry(namespace, key);
        entry1.setProperty("prop", "value1");
        storage.putConfig(namespace, key, entry1);

        ConfigEntry retrieved1 = storage.getConfig(namespace, key);
        assertEquals("Initial version should be 1", 1L, retrieved1.getVersion());

        // Update config
        ConfigEntry entry2 = new ConfigEntry(namespace, key);
        entry2.setProperty("prop", "value2");
        storage.putConfig(namespace, key, entry2);

        ConfigEntry retrieved2 = storage.getConfig(namespace, key);
        assertEquals("Version should increment to 2", 2L, retrieved2.getVersion());

        // Update again
        ConfigEntry entry3 = new ConfigEntry(namespace, key);
        entry3.setProperty("prop", "value3");
        storage.putConfig(namespace, key, entry3);

        ConfigEntry retrieved3 = storage.getConfig(namespace, key);
        assertEquals("Version should increment to 3", 3L, retrieved3.getVersion());
    }

    @Test
    public void testVersionIncrementOnDelete() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create config
        ConfigEntry entry = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry);

        ConfigEntry retrieved = storage.getConfig(namespace, key);
        long versionBeforeDelete = retrieved.getVersion();

        // Delete config
        storage.deleteConfig(namespace, key);

        // Check history - deleted version should have incremented version
        List<ConfigEntry> history = storage.getConfigHistory(namespace, key);
        ConfigEntry deletedEntry = history.get(history.size() - 1);
        assertEquals("Deleted entry should have incremented version", versionBeforeDelete + 1,
            deletedEntry.getVersion());
        assertTrue("Deleted entry should be marked as deleted", deletedEntry.isDeleted());
    }

    @Test
    public void testCreateTimePreservedAcrossVersions() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create initial config
        ConfigEntry entry1 = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry1);
        ConfigEntry retrieved1 = storage.getConfig(namespace, key);
        long createTime = retrieved1.getCreateTime();

        // Update config
        ConfigEntry entry2 = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry2);
        ConfigEntry retrieved2 = storage.getConfig(namespace, key);

        assertEquals("Create time should be preserved", createTime, retrieved2.getCreateTime());
    }

    @Test
    public void testUpdateTimeChangesOnUpdate() throws InterruptedException {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create initial config
        ConfigEntry entry1 = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry1);
        ConfigEntry retrieved1 = storage.getConfig(namespace, key);
        long updateTime1 = retrieved1.getUpdateTime();

        // Wait a bit to ensure time difference
        Thread.sleep(10);

        // Update config
        ConfigEntry entry2 = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry2);
        ConfigEntry retrieved2 = storage.getConfig(namespace, key);
        long updateTime2 = retrieved2.getUpdateTime();

        assertTrue("Update time should increase", updateTime2 >= updateTime1);
    }

    @Test
    public void testHistoryContainsAllVersions() {
        String namespace = "test-namespace";
        String key = "test-key";
        int numVersions = 5;

        // Create multiple versions
        for (int i = 0; i < numVersions; i++) {
            ConfigEntry entry = new ConfigEntry(namespace, key);
            entry.setProperty("version", String.valueOf(i + 1));
            storage.putConfig(namespace, key, entry);
        }

        // Get history
        List<ConfigEntry> history = storage.getConfigHistory(namespace, key);
        assertEquals("History should contain all versions", numVersions, history.size());

        // Verify versions are sequential
        for (int i = 0; i < numVersions; i++) {
            assertEquals("Version should be sequential", (long) (i + 1), history.get(i).getVersion());
            assertEquals("Property should match version", String.valueOf(i + 1), history.get(i).getProperty("version"));
        }
    }

    @Test
    public void testHistoryIncludesDeletedVersion() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create config
        ConfigEntry entry = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry);

        // Delete config
        storage.deleteConfig(namespace, key);

        // Get history
        List<ConfigEntry> history = storage.getConfigHistory(namespace, key);
        assertEquals("History should contain 2 versions (created + deleted)", 2, history.size());
        assertFalse("First version should not be deleted", history.get(0).isDeleted());
        assertTrue("Second version should be deleted", history.get(1).isDeleted());
    }

    @Test
    public void testGetConfigByVersionReturnsCorrectVersion() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create multiple versions
        for (int i = 0; i < 3; i++) {
            ConfigEntry entry = new ConfigEntry(namespace, key);
            entry.setProperty("version", String.valueOf(i + 1));
            storage.putConfig(namespace, key, entry);
        }

        // Get specific versions
        ConfigEntry v1 = storage.getConfigByVersion(namespace, key, 1L);
        assertNotNull("Version 1 should exist", v1);
        assertEquals("Version 1 property should match", "1", v1.getProperty("version"));

        ConfigEntry v2 = storage.getConfigByVersion(namespace, key, 2L);
        assertNotNull("Version 2 should exist", v2);
        assertEquals("Version 2 property should match", "2", v2.getProperty("version"));

        ConfigEntry v3 = storage.getConfigByVersion(namespace, key, 3L);
        assertNotNull("Version 3 should exist", v3);
        assertEquals("Version 3 property should match", "3", v3.getProperty("version"));
    }

    @Test
    public void testGetConfigByVersionReturnsDeletedVersion() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create and delete config
        ConfigEntry entry = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry);
        storage.deleteConfig(namespace, key);

        // Get deleted version
        ConfigEntry deletedVersion = storage.getConfigByVersion(namespace, key, 2L);
        assertNotNull("Deleted version should be retrievable", deletedVersion);
        assertTrue("Retrieved version should be marked as deleted", deletedVersion.isDeleted());
    }

    // ========== Boundary Condition Tests ==========

    @Test
    public void testEmptyProperties() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create config with empty properties
        ConfigEntry entry = new ConfigEntry(namespace, key, new HashMap<>());
        storage.putConfig(namespace, key, entry);

        ConfigEntry retrieved = storage.getConfig(namespace, key);
        assertNotNull("Config with empty properties should be retrievable", retrieved);
        assertTrue("Properties should be empty", retrieved.getProperties().isEmpty());
    }

    @Test
    public void testLargeNumberOfProperties() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create config with many properties
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            properties.put("key-" + i, "value-" + i);
        }

        ConfigEntry entry = new ConfigEntry(namespace, key, properties);
        storage.putConfig(namespace, key, entry);

        ConfigEntry retrieved = storage.getConfig(namespace, key);
        assertNotNull("Config with many properties should be retrievable", retrieved);
        assertEquals("All properties should be stored", 100, retrieved.getProperties().size());
    }

    @Test
    public void testLongNamespaceAndKey() {
        // Create very long namespace and key
        StringBuilder namespaceBuilder = new StringBuilder();
        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            namespaceBuilder.append("a");
            keyBuilder.append("b");
        }
        String namespace = namespaceBuilder.toString();
        String key = keyBuilder.toString();

        ConfigEntry entry = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry);

        ConfigEntry retrieved = storage.getConfig(namespace, key);
        assertNotNull("Config with long namespace and key should be retrievable", retrieved);
        assertEquals("Namespace should match", namespace, retrieved.getNamespace());
        assertEquals("Key should match", key, retrieved.getKey());
    }

    @Test
    public void testSpecialCharactersInNamespaceAndKey() {
        String namespace = "test-namespace!@#$%^&*()";
        String key = "test-key<>?:\"{}|";

        ConfigEntry entry = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry);

        ConfigEntry retrieved = storage.getConfig(namespace, key);
        assertNotNull("Config with special characters should be retrievable", retrieved);
        assertEquals("Namespace should match", namespace, retrieved.getNamespace());
        assertEquals("Key should match", key, retrieved.getKey());
    }

    @Test
    public void testMultipleNamespaces() {
        // Create configs in different namespaces
        for (int i = 0; i < 5; i++) {
            String namespace = "namespace-" + i;
            ConfigEntry entry = new ConfigEntry(namespace, "key");
            entry.setProperty("namespace-id", String.valueOf(i));
            storage.putConfig(namespace, "key", entry);
        }

        // Verify each namespace has correct config
        for (int i = 0; i < 5; i++) {
            String namespace = "namespace-" + i;
            ConfigEntry retrieved = storage.getConfig(namespace, "key");
            assertNotNull("Config in namespace " + i + " should exist", retrieved);
            assertEquals("Namespace ID should match", String.valueOf(i), retrieved.getProperty("namespace-id"));
        }
    }

    @Test
    public void testMultipleKeysInSameNamespace() {
        String namespace = "test-namespace";

        // Create multiple configs in same namespace
        for (int i = 0; i < 5; i++) {
            String key = "key-" + i;
            ConfigEntry entry = new ConfigEntry(namespace, key);
            entry.setProperty("key-id", String.valueOf(i));
            storage.putConfig(namespace, key, entry);
        }

        // Verify all configs exist
        List<ConfigEntry> configs = storage.listConfigs(namespace);
        assertEquals("Should have 5 configs", 5, configs.size());

        // Verify each config
        for (int i = 0; i < 5; i++) {
            String key = "key-" + i;
            ConfigEntry retrieved = storage.getConfig(namespace, key);
            assertNotNull("Config with key " + i + " should exist", retrieved);
            assertEquals("Key ID should match", String.valueOf(i), retrieved.getProperty("key-id"));
        }
    }

    @Test
    public void testDeleteAlreadyDeletedConfig() {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create and delete config
        ConfigEntry entry = new ConfigEntry(namespace, key);
        storage.putConfig(namespace, key, entry);
        boolean deleted1 = storage.deleteConfig(namespace, key);
        assertTrue("First delete should succeed", deleted1);

        // Try to delete again
        boolean deleted2 = storage.deleteConfig(namespace, key);
        assertFalse("Second delete should fail", deleted2);

        // Verify config is still not retrievable
        ConfigEntry retrieved = storage.getConfig(namespace, key);
        assertNull("Deleted config should not be retrievable", retrieved);
    }

    @Test
    public void testListConfigsExcludesDeletedEntries() {
        String namespace = "test-namespace";

        // Create multiple configs
        for (int i = 0; i < 5; i++) {
            ConfigEntry entry = new ConfigEntry(namespace, "key-" + i);
            storage.putConfig(namespace, "key-" + i, entry);
        }

        // Delete some configs
        storage.deleteConfig(namespace, "key-1");
        storage.deleteConfig(namespace, "key-3");

        // List configs
        List<ConfigEntry> configs = storage.listConfigs(namespace);
        assertEquals("Should have 3 non-deleted configs", 3, configs.size());

        // Verify deleted configs are not in list
        for (ConfigEntry config : configs) {
            assertFalse("Listed config should not be deleted", config.isDeleted());
            assertNotEquals("key-1 should not be in list", "key-1", config.getKey());
            assertNotEquals("key-3 should not be in list", "key-3", config.getKey());
        }
    }

    // ========== Snapshot Tests ==========

    @Test
    public void testSaveAndLoadSnapshot() throws IOException {
        String namespace = "test-namespace";

        // Create some configs
        for (int i = 0; i < 3; i++) {
            ConfigEntry entry = new ConfigEntry(namespace, "key-" + i);
            entry.setProperty("index", String.valueOf(i));
            storage.putConfig(namespace, "key-" + i, entry);
        }

        // Save snapshot
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        storage.saveSnapshot(output);

        // Create new storage and load snapshot
        MemoryConfigStorage newStorage = new MemoryConfigStorage();
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        newStorage.loadSnapshot(input);

        // Verify all configs are restored
        for (int i = 0; i < 3; i++) {
            ConfigEntry retrieved = newStorage.getConfig(namespace, "key-" + i);
            assertNotNull("Config " + i + " should be restored", retrieved);
            assertEquals("Property should match", String.valueOf(i), retrieved.getProperty("index"));
        }
    }

    @Test
    public void testSnapshotPreservesVersionHistory() throws IOException {
        String namespace = "test-namespace";
        String key = "test-key";

        // Create multiple versions
        for (int i = 0; i < 3; i++) {
            ConfigEntry entry = new ConfigEntry(namespace, key);
            entry.setProperty("version", String.valueOf(i + 1));
            storage.putConfig(namespace, key, entry);
        }

        // Save snapshot
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        storage.saveSnapshot(output);

        // Load into new storage
        MemoryConfigStorage newStorage = new MemoryConfigStorage();
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        newStorage.loadSnapshot(input);

        // Verify history is preserved
        List<ConfigEntry> history = newStorage.getConfigHistory(namespace, key);
        assertEquals("History should be preserved", 3, history.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("Version should match", (long) (i + 1), history.get(i).getVersion());
        }
    }

    @Test
    public void testLoadSnapshotClearsExistingData() throws IOException {
        String namespace1 = "namespace1";
        String namespace2 = "namespace2";

        // Add data to first storage
        ConfigEntry entry1 = new ConfigEntry(namespace1, "key1");
        storage.putConfig(namespace1, "key1", entry1);

        // Save snapshot
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        storage.saveSnapshot(output);

        // Add more data to storage
        ConfigEntry entry2 = new ConfigEntry(namespace2, "key2");
        storage.putConfig(namespace2, "key2", entry2);

        // Load snapshot (should clear namespace2 data)
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        storage.loadSnapshot(input);

        // Verify only namespace1 data exists
        ConfigEntry retrieved1 = storage.getConfig(namespace1, "key1");
        assertNotNull("Namespace1 data should exist", retrieved1);

        ConfigEntry retrieved2 = storage.getConfig(namespace2, "key2");
        assertNull("Namespace2 data should be cleared", retrieved2);
    }

    @Test
    public void testEmptySnapshotSaveAndLoad() throws IOException {
        // Save empty snapshot
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        storage.saveSnapshot(output);

        // Load into new storage
        MemoryConfigStorage newStorage = new MemoryConfigStorage();
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        newStorage.loadSnapshot(input);

        // Verify storage is empty
        List<ConfigEntry> configs = newStorage.listConfigs("any-namespace");
        assertTrue("Storage should be empty", configs.isEmpty());
    }
}
