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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.quicktheories.core.Gen;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.EnumOutter;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;

import static org.junit.Assert.*;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.*;

/**
 * Property-based tests for ConfigStateMachine.
 * Tests universal properties that should hold across all valid executions.
 *
 * @author lei
 */
public class ConfigStateMachinePropertyTest {

    /**
     * Feature: distributed-config-center, Property 3: 版本管理一致性
     * 
     * For any config entry, each update should increment the version number,
     * historical versions should be preserved, and rollback operations should
     * restore to the specified version's value and create a new version.
     * 
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
     */
    @Test
    public void testVersionManagementConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            integers().between(2, 10)                         // number of updates
        ).checkAssert((namespace, key, numUpdates) -> {
            ConfigStateMachine stateMachine = new ConfigStateMachine();
            AtomicLong logIndex = new AtomicLong(1);
            
            // Create initial config (Requirement 3.1)
            Map<String, String> initialProps = new HashMap<>();
            initialProps.put("version", "1");
            ConfigRequest createRequest = new ConfigRequest();
            createRequest.setType(ConfigRequest.Type.PUT_CONFIG);
            createRequest.setNamespace(namespace);
            createRequest.setKey(key);
            createRequest.setProperties(initialProps);
            
            applyRequest(stateMachine, createRequest, logIndex.getAndIncrement());
            
            ConfigEntry initialEntry = stateMachine.getStorage().getConfig(namespace, key);
            assertNotNull("Initial config should exist", initialEntry);
            assertEquals("Initial version should be 1", 1L, initialEntry.getVersion());
            
            // Perform multiple updates and verify version increments (Requirement 3.1)
            for (int i = 2; i <= numUpdates + 1; i++) {
                Map<String, String> updateProps = new HashMap<>();
                updateProps.put("version", String.valueOf(i));
                ConfigRequest updateRequest = new ConfigRequest();
                updateRequest.setType(ConfigRequest.Type.PUT_CONFIG);
                updateRequest.setNamespace(namespace);
                updateRequest.setKey(key);
                updateRequest.setProperties(updateProps);
                
                applyRequest(stateMachine, updateRequest, logIndex.getAndIncrement());
                
                ConfigEntry updatedEntry = stateMachine.getStorage().getConfig(namespace, key);
                assertNotNull("Config should exist after update " + i, updatedEntry);
                assertEquals("Version should increment to " + i, (long)i, updatedEntry.getVersion());
                assertEquals("Properties should be updated", String.valueOf(i), 
                    updatedEntry.getProperty("version"));
            }
            
            // Verify history is preserved (Requirement 3.2)
            ConfigRequest historyRequest = new ConfigRequest();
            historyRequest.setType(ConfigRequest.Type.GET_CONFIG_HISTORY);
            historyRequest.setNamespace(namespace);
            historyRequest.setKey(key);
            
            ConfigResponse historyResponse = applyRequest(stateMachine, historyRequest, 
                logIndex.getAndIncrement());
            assertTrue("History request should succeed", historyResponse.isSuccess());
            
            List<ConfigEntry> history = historyResponse.getConfigEntries();
            assertNotNull("History should not be null", history);
            assertEquals("History should contain all versions", (long)(numUpdates + 1), (long)history.size());
            
            // Verify each historical version can be retrieved (Requirement 3.3)
            for (long version = 1; version <= numUpdates + 1; version++) {
                ConfigRequest versionRequest = new ConfigRequest();
                versionRequest.setType(ConfigRequest.Type.GET_CONFIG_BY_VERSION);
                versionRequest.setNamespace(namespace);
                versionRequest.setKey(key);
                versionRequest.setVersion(version);
                
                ConfigResponse versionResponse = applyRequest(stateMachine, versionRequest, 
                    logIndex.getAndIncrement());
                assertTrue("Version " + version + " should be retrievable", versionResponse.isSuccess());
                
                ConfigEntry versionEntry = versionResponse.getConfigEntry();
                assertNotNull("Version entry should not be null", versionEntry);
                assertEquals("Version number should match", version, versionEntry.getVersion());
                assertEquals("Version property should match", String.valueOf(version), 
                    versionEntry.getProperty("version"));
            }
            
            // Test rollback to a previous version (Requirement 3.5)
            long rollbackToVersion = (numUpdates + 1) / 2;  // Rollback to middle version
            ConfigRequest rollbackRequest = new ConfigRequest();
            rollbackRequest.setType(ConfigRequest.Type.ROLLBACK_CONFIG);
            rollbackRequest.setNamespace(namespace);
            rollbackRequest.setKey(key);
            rollbackRequest.setVersion(rollbackToVersion);
            
            ConfigResponse rollbackResponse = applyRequest(stateMachine, rollbackRequest, 
                logIndex.getAndIncrement());
            assertTrue("Rollback should succeed", rollbackResponse.isSuccess());
            
            ConfigEntry rolledBackEntry = rollbackResponse.getConfigEntry();
            assertNotNull("Rolled back entry should not be null", rolledBackEntry);
            assertEquals("Rollback should create new version", (long)(numUpdates + 2), 
                rolledBackEntry.getVersion());
            assertEquals("Rollback should restore properties", String.valueOf(rollbackToVersion), 
                rolledBackEntry.getProperty("version"));
            
            // Verify current config matches rolled back version's properties
            ConfigEntry currentEntry = stateMachine.getStorage().getConfig(namespace, key);
            assertNotNull("Current config should exist after rollback", currentEntry);
            assertEquals("Current version should be new version", (long)(numUpdates + 2), 
                currentEntry.getVersion());
            assertEquals("Current properties should match rollback target", 
                String.valueOf(rollbackToVersion), currentEntry.getProperty("version"));
        });
    }

    /**
     * Feature: distributed-config-center, Property 3: 版本管理一致性
     * 
     * For any config entry, deleting it should preserve history and mark it as deleted,
     * but historical versions should still be retrievable.
     * 
     * Validates: Requirements 3.4
     */
    @Test
    public void testDeletePreservesHistory() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            integers().between(1, 5)                          // number of versions before delete
        ).checkAssert((namespace, key, numVersions) -> {
            ConfigStateMachine stateMachine = new ConfigStateMachine();
            AtomicLong logIndex = new AtomicLong(1);
            
            // Create multiple versions
            for (int i = 1; i <= numVersions; i++) {
                Map<String, String> props = new HashMap<>();
                props.put("version", String.valueOf(i));
                ConfigRequest putRequest = new ConfigRequest();
                putRequest.setType(ConfigRequest.Type.PUT_CONFIG);
                putRequest.setNamespace(namespace);
                putRequest.setKey(key);
                putRequest.setProperties(props);
                
                applyRequest(stateMachine, putRequest, logIndex.getAndIncrement());
            }
            
            // Delete the config (Requirement 3.4)
            ConfigRequest deleteRequest = new ConfigRequest();
            deleteRequest.setType(ConfigRequest.Type.DELETE_CONFIG);
            deleteRequest.setNamespace(namespace);
            deleteRequest.setKey(key);
            
            ConfigResponse deleteResponse = applyRequest(stateMachine, deleteRequest, 
                logIndex.getAndIncrement());
            assertTrue("Delete should succeed", deleteResponse.isSuccess());
            
            // Verify config is deleted
            ConfigEntry currentEntry = stateMachine.getStorage().getConfig(namespace, key);
            assertNull("Deleted config should return null", currentEntry);
            
            // Verify history is preserved (Requirement 3.4)
            ConfigRequest historyRequest = new ConfigRequest();
            historyRequest.setType(ConfigRequest.Type.GET_CONFIG_HISTORY);
            historyRequest.setNamespace(namespace);
            historyRequest.setKey(key);
            
            ConfigResponse historyResponse = applyRequest(stateMachine, historyRequest, 
                logIndex.getAndIncrement());
            assertTrue("History request should succeed", historyResponse.isSuccess());
            
            List<ConfigEntry> history = historyResponse.getConfigEntries();
            assertNotNull("History should not be null", history);
            assertEquals("History should include deleted version", (long)(numVersions + 1), 
                (long)history.size());
            
            // Verify last entry is marked as deleted
            ConfigEntry lastEntry = history.get(history.size() - 1);
            assertTrue("Last entry should be marked as deleted", lastEntry.isDeleted());
            assertEquals("Deleted entry should have incremented version", (long)(numVersions + 1), 
                lastEntry.getVersion());
            
            // Verify all previous versions are still retrievable (Requirement 3.4)
            for (long version = 1; version <= numVersions; version++) {
                ConfigRequest versionRequest = new ConfigRequest();
                versionRequest.setType(ConfigRequest.Type.GET_CONFIG_BY_VERSION);
                versionRequest.setNamespace(namespace);
                versionRequest.setKey(key);
                versionRequest.setVersion(version);
                
                ConfigResponse versionResponse = applyRequest(stateMachine, versionRequest, 
                    logIndex.getAndIncrement());
                assertTrue("Historical version " + version + " should be retrievable", 
                    versionResponse.isSuccess());
                
                ConfigEntry versionEntry = versionResponse.getConfigEntry();
                assertNotNull("Version entry should not be null", versionEntry);
                assertEquals("Version number should match", version, versionEntry.getVersion());
                assertFalse("Historical version should not be marked as deleted", 
                    versionEntry.isDeleted());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 3: 版本管理一致性
     * 
     * For any config entry, rollback to a non-existent version should fail,
     * and the current version should remain unchanged.
     * 
     * Validates: Requirements 3.5
     */
    @Test
    public void testRollbackToNonExistentVersionFails() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            integers().between(1, 5)                          // number of versions
        ).checkAssert((namespace, key, numVersions) -> {
            ConfigStateMachine stateMachine = new ConfigStateMachine();
            AtomicLong logIndex = new AtomicLong(1);
            
            // Create multiple versions
            for (int i = 1; i <= numVersions; i++) {
                Map<String, String> props = new HashMap<>();
                props.put("version", String.valueOf(i));
                ConfigRequest putRequest = new ConfigRequest();
                putRequest.setType(ConfigRequest.Type.PUT_CONFIG);
                putRequest.setNamespace(namespace);
                putRequest.setKey(key);
                putRequest.setProperties(props);
                
                applyRequest(stateMachine, putRequest, logIndex.getAndIncrement());
            }
            
            ConfigEntry beforeRollback = stateMachine.getStorage().getConfig(namespace, key);
            assertNotNull("Config should exist before rollback", beforeRollback);
            long versionBeforeRollback = beforeRollback.getVersion();
            
            // Try to rollback to non-existent version (Requirement 3.5)
            long nonExistentVersion = numVersions + 10;
            ConfigRequest rollbackRequest = new ConfigRequest();
            rollbackRequest.setType(ConfigRequest.Type.ROLLBACK_CONFIG);
            rollbackRequest.setNamespace(namespace);
            rollbackRequest.setKey(key);
            rollbackRequest.setVersion(nonExistentVersion);
            
            ConfigResponse rollbackResponse = applyRequest(stateMachine, rollbackRequest, 
                logIndex.getAndIncrement());
            assertFalse("Rollback to non-existent version should fail", rollbackResponse.isSuccess());
            assertNotNull("Error message should be provided", rollbackResponse.getErrorMsg());
            assertTrue("Error message should mention version not found", 
                rollbackResponse.getErrorMsg().contains("not found"));
            
            // Verify current version is unchanged
            ConfigEntry afterRollback = stateMachine.getStorage().getConfig(namespace, key);
            assertNotNull("Config should still exist after failed rollback", afterRollback);
            assertEquals("Version should remain unchanged", versionBeforeRollback, 
                afterRollback.getVersion());
            assertEquals("Properties should remain unchanged", String.valueOf(numVersions), 
                afterRollback.getProperty("version"));
        });
    }

    /**
     * Feature: distributed-config-center, Property 3: 版本管理一致性
     * 
     * For any sequence of operations (create, update, delete, rollback),
     * version numbers should always be monotonically increasing.
     * 
     * Validates: Requirements 3.1
     */
    @Test
    public void testVersionMonotonicityAcrossOperations() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            generateOperationSequence()                       // sequence of operations
        ).checkAssert((namespace, key, operations) -> {
            ConfigStateMachine stateMachine = new ConfigStateMachine();
            AtomicLong logIndex = new AtomicLong(1);
            long expectedVersion = 0;
            
            for (String operation : operations) {
                ConfigRequest request = new ConfigRequest();
                request.setNamespace(namespace);
                request.setKey(key);
                
                switch (operation) {
                    case "CREATE":
                    case "UPDATE":
                        expectedVersion++;
                        Map<String, String> props = new HashMap<>();
                        props.put("op", operation);
                        props.put("version", String.valueOf(expectedVersion));
                        request.setType(ConfigRequest.Type.PUT_CONFIG);
                        request.setProperties(props);
                        
                        ConfigResponse putResponse = applyRequest(stateMachine, request, 
                            logIndex.getAndIncrement());
                        assertTrue("Put operation should succeed", putResponse.isSuccess());
                        
                        ConfigEntry putEntry = putResponse.getConfigEntry();
                        assertNotNull("Put entry should not be null", putEntry);
                        assertEquals("Version should be " + expectedVersion, expectedVersion, 
                            putEntry.getVersion());
                        break;
                        
                    case "DELETE":
                        ConfigEntry beforeDelete = stateMachine.getStorage().getConfig(namespace, key);
                        if (beforeDelete != null && !beforeDelete.isDeleted()) {
                            expectedVersion++;
                            request.setType(ConfigRequest.Type.DELETE_CONFIG);
                            
                            ConfigResponse deleteResponse = applyRequest(stateMachine, request, 
                                logIndex.getAndIncrement());
                            assertTrue("Delete operation should succeed", deleteResponse.isSuccess());
                        }
                        break;
                        
                    case "ROLLBACK":
                        ConfigEntry beforeRollback = stateMachine.getStorage().getConfig(namespace, key);
                        if (beforeRollback != null && !beforeRollback.isDeleted() && 
                            beforeRollback.getVersion() > 1) {
                            expectedVersion++;
                            long rollbackToVersion = beforeRollback.getVersion() - 1;
                            request.setType(ConfigRequest.Type.ROLLBACK_CONFIG);
                            request.setVersion(rollbackToVersion);
                            
                            ConfigResponse rollbackResponse = applyRequest(stateMachine, request, 
                                logIndex.getAndIncrement());
                            assertTrue("Rollback operation should succeed", rollbackResponse.isSuccess());
                            
                            ConfigEntry rollbackEntry = rollbackResponse.getConfigEntry();
                            assertNotNull("Rollback entry should not be null", rollbackEntry);
                            assertEquals("Version should be " + expectedVersion, expectedVersion, 
                                rollbackEntry.getVersion());
                        }
                        break;
                }
            }
            
            // Verify final history has monotonic versions
            List<ConfigEntry> history = stateMachine.getStorage().getConfigHistory(namespace, key);
            for (int i = 0; i < history.size(); i++) {
                assertEquals("Version should be sequential", (long)(i + 1), history.get(i).getVersion());
            }
        });
    }

    /**
     * Helper method to apply a request to the state machine
     */
    private ConfigResponse applyRequest(ConfigStateMachine stateMachine, ConfigRequest request, long logIndex) {
        try {
            byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

            LogEntry logEntry = new LogEntry(EnumOutter.EntryType.ENTRY_TYPE_DATA);
            logEntry.setId(new LogId(logIndex, 1));
            logEntry.setData(ByteBuffer.wrap(data));

            List<LogEntry> entries = new ArrayList<>();
            entries.add(logEntry);

            ConfigResponseHolder holder = new ConfigResponseHolder();
            TestIterator iterator = new TestIterator(entries, new ConfigClosure(request, holder));

            stateMachine.onApply(iterator);

            return holder.getResponse();
        } catch (CodecException e) {
            throw new RuntimeException("Failed to serialize request", e);
        }
    }

    /**
     * Generator for operation sequences
     */
    private Gen<List<String>> generateOperationSequence() {
        return integers().between(3, 10).map(size -> {
            List<String> operations = new ArrayList<>();
            operations.add("CREATE");  // Always start with create

            for (int i = 1; i < size; i++) {
                int choice = i % 3;
                switch (choice) {
                    case 0:
                        operations.add("UPDATE");
                        break;
                    case 1:
                        operations.add("DELETE");
                        break;
                    case 2:
                        operations.add("ROLLBACK");
                        break;
                }
            }
            return operations;
        });
    }

    /**
     * Test iterator implementation
     */
    private static class TestIterator implements Iterator {
        private final List<LogEntry> entries;
        private int                  index;
        private final Closure        closure;

        public TestIterator(List<LogEntry> entries, Closure closure) {
            this.entries = entries;
            this.index = 0;
            this.closure = closure;
        }

        @Override
        public boolean hasNext() {
            return index < entries.size();
        }

        @Override
        public ByteBuffer next() {
            ByteBuffer data = getData();
            index++; // Move to next entry
            return data;
        }

        @Override
        public ByteBuffer getData() {
            if (index < entries.size()) {
                return entries.get(index).getData();
            }
            return null;
        }

        @Override
        public long getIndex() {
            if (index < entries.size()) {
                return entries.get(index).getId().getIndex();
            }
            return -1;
        }

        @Override
        public long getTerm() {
            if (index < entries.size()) {
                return entries.get(index).getId().getTerm();
            }
            return -1;
        }

        @Override
        public Closure done() {
            return closure;
        }

        @Override
        public void setAutoCommitPerLog(boolean status) {
            // Not implemented for testing
        }

        @Override
        public boolean commit() {
            // Not implemented for testing
            return true;
        }

        @Override
        public void commitAndSnapshotSync(Closure done) {
            // Not implemented for testing
        }

        @Override
        public void setErrorAndRollback(long ntail, Status st) {
            // Not implemented for testing
        }
    }

    /**
     * Holder for capturing response from closure
     */
    private static class ConfigResponseHolder implements ConfigCallback {
        private ConfigResponse response;

        @Override
        public void onSuccess(ConfigResponse response) {
            this.response = response;
        }

        @Override
        public void onError(String errorMsg) {
            this.response = ConfigResponse.error(errorMsg);
        }

        public ConfigResponse getResponse() {
            return response;
        }
    }
}
