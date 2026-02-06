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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quicktheories.core.Gen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.Task;

import static org.junit.Assert.*;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.*;

/**
 * Property-based tests for ConfigServer.
 * Tests data persistence consistency across server restarts.
 *
 * @author lei
 */
public class ConfigServerPropertyTest {

    private static final Logger LOG             = LoggerFactory.getLogger(ConfigServerPropertyTest.class);
    private static final String TEST_DATA_PATH  = "/tmp/config_server_property_test";
    private static final String GROUP_ID        = "config_center_test";
    private static final String SERVER_ID       = "127.0.0.1:8181";
    private static final String INITIAL_SERVERS = "127.0.0.1:8181";
    private static final int    HTTP_PORT       = 8180;

    private ConfigServer        server;
    private String              testDataPath;

    @Before
    public void setUp() throws IOException {
        // Create unique test data path for each test
        testDataPath = TEST_DATA_PATH + "_" + System.currentTimeMillis();
        FileUtils.forceMkdir(new File(testDataPath));
        LOG.info("Test data path: {}", testDataPath);
    }

    @After
    public void tearDown() throws IOException {
        // Shutdown server if running
        if (server != null) {
            server.shutdown();
            server = null;
        }

        // Clean up test data
        try {
            FileUtils.deleteDirectory(new File(testDataPath));
            LOG.info("Cleaned up test data path: {}", testDataPath);
        } catch (IOException e) {
            LOG.warn("Failed to clean up test data path: {}", testDataPath, e);
        }
    }

    /**
     * Feature: distributed-config-center, Property 4: 数据持久化一致性
     * 
     * For any configuration operation, submitted data should persist across system restarts
     * and remain consistent.
     * 
     * Validates: Requirements 4.1, 4.2
     */
    @Test
    public void testDataPersistenceConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            generateProperties()                              // properties
        ).checkAssert((namespace, key, properties) -> {
            try {
                // Start server
                ConfigServer firstServer = startServer();
                assertNotNull("Server should start successfully", firstServer);

                // Wait for server to be ready
                waitForLeader(firstServer, 5000);

                // Submit configuration through state machine
                ConfigRequest putRequest = new ConfigRequest();
                putRequest.setType(ConfigRequest.Type.PUT_CONFIG);
                putRequest.setNamespace(namespace);
                putRequest.setKey(key);
                putRequest.setProperties(properties);

                ConfigResponse putResponse = applyRequest(firstServer, putRequest);
                assertTrue("Put config should succeed: " + putResponse.getErrorMsg(), putResponse.isSuccess());
                assertNotNull("Put response should contain config entry", putResponse.getConfigEntry());

                long originalVersion = putResponse.getConfigEntry().getVersion();
                Map<String, String> originalProperties = putResponse.getConfigEntry().getProperties();

                LOG.info("Submitted config: namespace={}, key={}, version={}", namespace, key, originalVersion);

                // Force snapshot to ensure data is persisted
                triggerSnapshot(firstServer);

                // Shutdown server
                firstServer.shutdown();
                LOG.info("First server shutdown");

                // Wait a bit to ensure clean shutdown
                Thread.sleep(500);

                // Restart server with same data path
                ConfigServer secondServer = startServer();
                assertNotNull("Server should restart successfully", secondServer);

                // Wait for server to be ready
                waitForLeader(secondServer, 5000);

                // Query configuration after restart
                ConfigRequest getRequest = new ConfigRequest();
                getRequest.setType(ConfigRequest.Type.GET_CONFIG);
                getRequest.setNamespace(namespace);
                getRequest.setKey(key);

                ConfigResponse getResponse = applyRequest(secondServer, getRequest);
                assertTrue("Get config should succeed after restart: " + getResponse.getErrorMsg(),
                    getResponse.isSuccess());
                assertNotNull("Get response should contain config entry", getResponse.getConfigEntry());

                // Verify data consistency
                ConfigEntry retrievedEntry = getResponse.getConfigEntry();
                assertEquals("Namespace should match after restart", namespace, retrievedEntry.getNamespace());
                assertEquals("Key should match after restart", key, retrievedEntry.getKey());
                assertEquals("Version should match after restart", originalVersion, retrievedEntry.getVersion());
                assertEquals("Properties should match after restart", originalProperties,
                    retrievedEntry.getProperties());
                assertFalse("Entry should not be deleted", retrievedEntry.isDeleted());

                LOG.info("Verified config after restart: namespace={}, key={}, version={}", namespace, key,
                    retrievedEntry.getVersion());

                // Cleanup
                secondServer.shutdown();
                this.server = null;

            } catch (Exception e) {
                LOG.error("Test failed for namespace={}, key={}", namespace, key, e);
                fail("Test failed: " + e.getMessage());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 4: 数据持久化一致性
     * 
     * For any configuration with multiple updates, all versions should persist across
     * system restarts and history should remain consistent.
     * 
     * Validates: Requirements 4.1, 4.2
     */
    @Test
    public void testVersionHistoryPersistence() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            integers().between(2, 5)                          // number of updates
        ).checkAssert((namespace, key, numUpdates) -> {
            try {
                // Start server
                ConfigServer firstServer = startServer();
                assertNotNull("Server should start successfully", firstServer);

                // Wait for server to be ready
                waitForLeader(firstServer, 5000);

                // Create initial config and perform multiple updates
                for (int i = 0; i <= numUpdates; i++) {
                    Map<String, String> properties = new HashMap<>();
                    properties.put("version", String.valueOf(i));
                    properties.put("update", "update-" + i);

                    ConfigRequest putRequest = new ConfigRequest();
                    putRequest.setType(ConfigRequest.Type.PUT_CONFIG);
                    putRequest.setNamespace(namespace);
                    putRequest.setKey(key);
                    putRequest.setProperties(properties);

                    ConfigResponse putResponse = applyRequest(firstServer, putRequest);
                    assertTrue("Put config should succeed for update " + i + ": " + putResponse.getErrorMsg(),
                        putResponse.isSuccess());

                    LOG.debug("Applied update {}: namespace={}, key={}, version={}", i, namespace, key,
                        putResponse.getConfigEntry().getVersion());
                }

                // Force snapshot
                triggerSnapshot(firstServer);

                // Shutdown server
                firstServer.shutdown();
                LOG.info("First server shutdown after {} updates", numUpdates);

                // Wait for clean shutdown
                Thread.sleep(500);

                // Restart server
                ConfigServer secondServer = startServer();
                assertNotNull("Server should restart successfully", secondServer);

                // Wait for server to be ready
                waitForLeader(secondServer, 5000);

                // Verify current version
                ConfigRequest getRequest = new ConfigRequest();
                getRequest.setType(ConfigRequest.Type.GET_CONFIG);
                getRequest.setNamespace(namespace);
                getRequest.setKey(key);

                ConfigResponse getResponse = applyRequest(secondServer, getRequest);
                assertTrue("Get config should succeed after restart: " + getResponse.getErrorMsg(),
                    getResponse.isSuccess());

                ConfigEntry currentEntry = getResponse.getConfigEntry();
                assertEquals("Current version should be " + (numUpdates + 1), (long) (numUpdates + 1),
                    currentEntry.getVersion());
                assertEquals("Current properties should match last update", String.valueOf(numUpdates),
                    currentEntry.getProperty("version"));

                // Verify history
                ConfigRequest historyRequest = new ConfigRequest();
                historyRequest.setType(ConfigRequest.Type.GET_CONFIG_HISTORY);
                historyRequest.setNamespace(namespace);
                historyRequest.setKey(key);

                ConfigResponse historyResponse = applyRequest(secondServer, historyRequest);
                assertTrue("Get history should succeed after restart: " + historyResponse.getErrorMsg(),
                    historyResponse.isSuccess());
                assertNotNull("History should not be null", historyResponse.getConfigEntries());
                assertEquals("History should contain all versions", (long) (numUpdates + 1),
                    (long) historyResponse.getConfigEntries().size());

                // Verify each historical version
                for (int i = 0; i <= numUpdates; i++) {
                    ConfigRequest versionRequest = new ConfigRequest();
                    versionRequest.setType(ConfigRequest.Type.GET_CONFIG_BY_VERSION);
                    versionRequest.setNamespace(namespace);
                    versionRequest.setKey(key);
                    versionRequest.setVersion(i + 1);

                    ConfigResponse versionResponse = applyRequest(secondServer, versionRequest);
                    assertTrue("Get version " + (i + 1) + " should succeed: " + versionResponse.getErrorMsg(),
                        versionResponse.isSuccess());

                    ConfigEntry versionEntry = versionResponse.getConfigEntry();
                    assertEquals("Version number should match", (long) (i + 1), versionEntry.getVersion());
                    assertEquals("Version properties should match", String.valueOf(i),
                        versionEntry.getProperty("version"));
                }

                LOG.info("Verified {} versions after restart for namespace={}, key={}", numUpdates + 1, namespace,
                    key);

                // Cleanup
                secondServer.shutdown();
                this.server = null;

            } catch (Exception e) {
                LOG.error("Test failed for namespace={}, key={}, numUpdates={}", namespace, key, numUpdates, e);
                fail("Test failed: " + e.getMessage());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 4: 数据持久化一致性
     * 
     * For any deleted configuration, the deletion should persist across system restarts
     * and the config should remain inaccessible.
     * 
     * Validates: Requirements 4.1, 4.2
     */
    @Test
    public void testDeletePersistence() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 20),  // namespace
            strings().allPossible().ofLengthBetween(1, 20),  // key
            generateProperties()                              // properties
        ).checkAssert((namespace, key, properties) -> {
            try {
                // Start server
                ConfigServer firstServer = startServer();
                assertNotNull("Server should start successfully", firstServer);

                // Wait for server to be ready
                waitForLeader(firstServer, 5000);

                // Create config
                ConfigRequest putRequest = new ConfigRequest();
                putRequest.setType(ConfigRequest.Type.PUT_CONFIG);
                putRequest.setNamespace(namespace);
                putRequest.setKey(key);
                putRequest.setProperties(properties);

                ConfigResponse putResponse = applyRequest(firstServer, putRequest);
                assertTrue("Put config should succeed: " + putResponse.getErrorMsg(), putResponse.isSuccess());

                // Delete config
                ConfigRequest deleteRequest = new ConfigRequest();
                deleteRequest.setType(ConfigRequest.Type.DELETE_CONFIG);
                deleteRequest.setNamespace(namespace);
                deleteRequest.setKey(key);

                ConfigResponse deleteResponse = applyRequest(firstServer, deleteRequest);
                assertTrue("Delete config should succeed: " + deleteResponse.getErrorMsg(),
                    deleteResponse.isSuccess());

                LOG.info("Deleted config: namespace={}, key={}", namespace, key);

                // Force snapshot
                triggerSnapshot(firstServer);

                // Shutdown server
                firstServer.shutdown();
                LOG.info("First server shutdown after delete");

                // Wait for clean shutdown
                Thread.sleep(500);

                // Restart server
                ConfigServer secondServer = startServer();
                assertNotNull("Server should restart successfully", secondServer);

                // Wait for server to be ready
                waitForLeader(secondServer, 5000);

                // Verify config is still deleted
                ConfigRequest getRequest = new ConfigRequest();
                getRequest.setType(ConfigRequest.Type.GET_CONFIG);
                getRequest.setNamespace(namespace);
                getRequest.setKey(key);

                ConfigResponse getResponse = applyRequest(secondServer, getRequest);
                assertFalse("Get config should fail for deleted config", getResponse.isSuccess());
                assertNull("Deleted config should return null", getResponse.getConfigEntry());

                LOG.info("Verified deletion persisted after restart: namespace={}, key={}", namespace, key);

                // Cleanup
                secondServer.shutdown();
                this.server = null;

            } catch (Exception e) {
                LOG.error("Test failed for namespace={}, key={}", namespace, key, e);
                fail("Test failed: " + e.getMessage());
            }
        });
    }

    /**
     * Start a ConfigServer instance with test configuration
     */
    private ConfigServer startServer() {
        ConfigServerOptions opts = new ConfigServerOptions(testDataPath, GROUP_ID, SERVER_ID, INITIAL_SERVERS,
            HTTP_PORT);

        ConfigServer newServer = new ConfigServer();
        boolean started = newServer.start(opts);

        if (started) {
            this.server = newServer;
            LOG.info("ConfigServer started successfully");
            return newServer;
        } else {
            LOG.error("Failed to start ConfigServer");
            return null;
        }
    }

    /**
     * Wait for server to elect a leader
     */
    private void waitForLeader(ConfigServer server, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (server.getNode() != null && server.getNode().isLeader()) {
                LOG.info("Server became leader");
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Server did not become leader within timeout");
    }

    /**
     * Apply a request to the state machine through Raft consensus
     */
    private ConfigResponse applyRequest(ConfigServer server, ConfigRequest request) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        AtomicReference<Status> statusRef = new AtomicReference<>();

        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                ConfigResponse errorResponse = ConfigResponse.error(errorMsg);
                responseRef.set(errorResponse);
                latch.countDown();
            }
        });

        // Serialize request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create task
        Task task = new Task();
        task.setData(ByteBuffer.wrap(data));
        task.setDone(closure);

        // Apply task to Raft node
        server.getNode().apply(task);

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        if (!completed) {
            throw new IllegalStateException("Request did not complete within timeout");
        }

        return responseRef.get();
    }

    /**
     * Trigger a snapshot to force data persistence
     */
    private void triggerSnapshot(ConfigServer server) throws InterruptedException {
        if (server.getNode() != null) {
            CountDownLatch latch = new CountDownLatch(1);
            server.getNode().snapshot(status -> {
                if (status.isOk()) {
                    LOG.info("Snapshot completed successfully");
                } else {
                    LOG.warn("Snapshot failed: {}", status);
                }
                latch.countDown();
            });
            latch.await(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Generator for random property maps
     */
    private Gen<Map<String, String>> generateProperties() {
        return integers().between(1, 5).map(size -> {
            Map<String, String> properties = new HashMap<>();
            for (int i = 0; i < size; i++) {
                properties.put("key-" + i, "value-" + i);
            }
            return properties;
        });
    }
}
