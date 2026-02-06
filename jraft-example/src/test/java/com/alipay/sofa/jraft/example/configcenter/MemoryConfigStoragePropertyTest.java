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
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.quicktheories.core.Gen;

import static org.junit.Assert.*;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.*;

/**
 * Property-based tests for MemoryConfigStorage.
 * Tests universal properties that should hold across all valid inputs.
 *
 * @author lei
 */
public class MemoryConfigStoragePropertyTest {

    /**
     * Feature: distributed-config-center, Property 1: 配置 CRUD 操作一致性
     * 
     * For any namespace, key and properties, creating a config entry and then querying it
     * should return the same properties, updating a config entry and then querying it
     * should return the new properties, and deleting a config entry and then querying it
     * should return null.
     * 
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
     */
    @Test
    public void testConfigCrudConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            generateProperties()                              // properties
        ).checkAssert((namespace, key, properties) -> {
            MemoryConfigStorage storage = new MemoryConfigStorage();
            
            // Test CREATE and READ (Requirement 1.1, 1.2)
            ConfigEntry entry = new ConfigEntry(namespace, key, properties);
            storage.putConfig(namespace, key, entry);
            
            ConfigEntry retrieved = storage.getConfig(namespace, key);
            assertNotNull("Created config should be retrievable", retrieved);
            assertEquals("Namespace should match", namespace, retrieved.getNamespace());
            assertEquals("Key should match", key, retrieved.getKey());
            assertEquals("Properties should match", properties, retrieved.getProperties());
            assertEquals("Version should be 1 for new entry", 1L, retrieved.getVersion());
            assertFalse("Entry should not be deleted", retrieved.isDeleted());
            
            // Test UPDATE and READ (Requirement 1.3)
            Map<String, String> updatedProperties = new HashMap<>(properties);
            updatedProperties.put("updated-key", "updated-value");
            ConfigEntry updatedEntry = new ConfigEntry(namespace, key, updatedProperties);
            storage.putConfig(namespace, key, updatedEntry);
            
            ConfigEntry retrievedAfterUpdate = storage.getConfig(namespace, key);
            assertNotNull("Updated config should be retrievable", retrievedAfterUpdate);
            assertEquals("Updated properties should match", updatedProperties, retrievedAfterUpdate.getProperties());
            assertEquals("Version should be incremented", 2L, retrievedAfterUpdate.getVersion());
            assertEquals("Create time should be preserved", retrieved.getCreateTime(), retrievedAfterUpdate.getCreateTime());
            assertTrue("Update time should be greater or equal", retrievedAfterUpdate.getUpdateTime() >= retrieved.getUpdateTime());
            
            // Test DELETE and READ (Requirement 1.4, 1.5)
            boolean deleted = storage.deleteConfig(namespace, key);
            assertTrue("Delete should return true for existing entry", deleted);
            
            ConfigEntry retrievedAfterDelete = storage.getConfig(namespace, key);
            assertNull("Deleted config should return null", retrievedAfterDelete);
            
            // Test querying non-existent config (Requirement 1.5)
            ConfigEntry nonExistent = storage.getConfig(namespace, "non-existent-key");
            assertNull("Non-existent config should return null", nonExistent);
        });
    }

    /**
     * Feature: distributed-config-center, Property 1: 配置 CRUD 操作一致性
     * 
     * For any namespace and key, multiple updates should increment version monotonically
     * and preserve history.
     * 
     * Validates: Requirements 1.3, 3.1, 3.2
     */
    @Test
    public void testVersionMonotonicity() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            integers().between(1, 10)                         // number of updates
        ).checkAssert((namespace, key, numUpdates) -> {
            MemoryConfigStorage storage = new MemoryConfigStorage();
            
            // Create initial entry
            Map<String, String> properties = new HashMap<>();
            properties.put("initial", "value");
            ConfigEntry entry = new ConfigEntry(namespace, key, properties);
            storage.putConfig(namespace, key, entry);
            
            // Perform multiple updates
            for (int i = 1; i <= numUpdates; i++) {
                Map<String, String> updatedProps = new HashMap<>();
                updatedProps.put("update", String.valueOf(i));
                ConfigEntry updateEntry = new ConfigEntry(namespace, key, updatedProps);
                storage.putConfig(namespace, key, updateEntry);
                
                ConfigEntry retrieved = storage.getConfig(namespace, key);
                assertNotNull("Config should exist after update " + i, retrieved);
                assertEquals("Version should be " + (i + 1), i + 1, retrieved.getVersion());
            }
            
            // Verify history
            List<ConfigEntry> history = storage.getConfigHistory(namespace, key);
            assertEquals("History should contain all versions", (long)(numUpdates + 1), (long)history.size());
            
            // Verify version monotonicity in history
            for (int i = 0; i < history.size(); i++) {
                assertEquals("Version should be sequential", (long)(i + 1), history.get(i).getVersion());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 1: 配置 CRUD 操作一致性
     * 
     * For any namespace, listing configs should only return non-deleted entries
     * and should include all created entries.
     * 
     * Validates: Requirements 1.1, 1.4, 2.1
     */
    @Test
    public void testListConfigsConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            integers().between(1, 10)                         // number of configs
        ).checkAssert((namespace, numConfigs) -> {
            MemoryConfigStorage storage = new MemoryConfigStorage();
            
            // Create multiple configs
            for (int i = 0; i < numConfigs; i++) {
                String key = "key-" + i;
                Map<String, String> properties = new HashMap<>();
                properties.put("index", String.valueOf(i));
                ConfigEntry entry = new ConfigEntry(namespace, key, properties);
                storage.putConfig(namespace, key, entry);
            }
            
            // List should return all configs
            List<ConfigEntry> configs = storage.listConfigs(namespace);
            assertEquals("List should return all created configs", (long)numConfigs, (long)configs.size());
            
            // Delete half of the configs
            int numToDelete = numConfigs / 2;
            for (int i = 0; i < numToDelete; i++) {
                String key = "key-" + i;
                storage.deleteConfig(namespace, key);
            }
            
            // List should only return non-deleted configs
            List<ConfigEntry> remainingConfigs = storage.listConfigs(namespace);
            assertEquals("List should only return non-deleted configs", 
                (long)(numConfigs - numToDelete), (long)remainingConfigs.size());
            
            // Verify all remaining configs are not deleted
            for (ConfigEntry config : remainingConfigs) {
                assertFalse("Listed config should not be deleted", config.isDeleted());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 1: 配置 CRUD 操作一致性
     * 
     * For any namespace and key, deleting a non-existent config should return false,
     * and deleting an already deleted config should return false.
     * 
     * Validates: Requirements 1.4, 1.5
     */
    @Test
    public void testDeleteIdempotency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20)   // key
        ).checkAssert((namespace, key) -> {
            MemoryConfigStorage storage = new MemoryConfigStorage();
            
            // Delete non-existent config should return false
            boolean deletedNonExistent = storage.deleteConfig(namespace, key);
            assertFalse("Deleting non-existent config should return false", deletedNonExistent);
            
            // Create and delete config
            Map<String, String> properties = new HashMap<>();
            properties.put("test", "value");
            ConfigEntry entry = new ConfigEntry(namespace, key, properties);
            storage.putConfig(namespace, key, entry);
            
            boolean deletedFirst = storage.deleteConfig(namespace, key);
            assertTrue("First delete should return true", deletedFirst);
            
            // Delete again should return false
            boolean deletedSecond = storage.deleteConfig(namespace, key);
            assertFalse("Second delete should return false", deletedSecond);
        });
    }

    /**
     * Feature: distributed-config-center, Property 1: 配置 CRUD 操作一致性
     * 
     * For any namespace, key and version, getting a config by version should return
     * the correct historical version.
     * 
     * Validates: Requirements 3.2, 3.3
     */
    @Test
    public void testGetConfigByVersionConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            integers().between(2, 10)                         // number of versions
        ).checkAssert((namespace, key, numVersions) -> {
            MemoryConfigStorage storage = new MemoryConfigStorage();
            
            // Create multiple versions
            for (int i = 0; i < numVersions; i++) {
                Map<String, String> properties = new HashMap<>();
                properties.put("version", String.valueOf(i + 1));
                ConfigEntry entry = new ConfigEntry(namespace, key, properties);
                storage.putConfig(namespace, key, entry);
            }
            
            // Verify each version can be retrieved
            for (long version = 1; version <= numVersions; version++) {
                ConfigEntry retrieved = storage.getConfigByVersion(namespace, key, version);
                assertNotNull("Version " + version + " should exist", retrieved);
                assertEquals("Version number should match", version, retrieved.getVersion());
                assertEquals("Version property should match", 
                    String.valueOf(version), retrieved.getProperty("version"));
            }
            
            // Non-existent version should return null
            ConfigEntry nonExistent = storage.getConfigByVersion(namespace, key, numVersions + 1);
            assertNull("Non-existent version should return null", nonExistent);
        });
    }

    /**
     * Generator for random property maps
     */
    private Gen<Map<String, String>> generateProperties() {
        return integers().between(0, 5).map(size -> {
            Map<String, String> properties = new HashMap<>();
            for (int i = 0; i < size; i++) {
                properties.put("key-" + i, "value-" + i);
            }
            return properties;
        });
    }
}
