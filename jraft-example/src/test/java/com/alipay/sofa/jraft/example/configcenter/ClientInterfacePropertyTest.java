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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.quicktheories.core.Gen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;

import static org.junit.Assert.*;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.*;

/**
 * Property-based tests for client interface consistency.
 * Tests that synchronous and asynchronous interfaces produce the same results
 * and that timeout settings are properly handled.
 *
 * @author lei
 */
public class ClientInterfacePropertyTest {

    private static final Logger LOG         = LoggerFactory.getLogger(ClientInterfacePropertyTest.class);

    private ConfigServer        server;
    private AdminClient         adminClient;
    private AppClient           appClient;
    private String              dataPath;
    private static final int    SERVER_PORT = 8091;

    @Before
    public void setUp() throws Exception {
        // Create temporary data directory
        dataPath = "/tmp/config-center-client-test-" + System.currentTimeMillis();
        FileUtils.deleteQuietly(new File(dataPath));

        // Start config server
        ConfigServerOptions serverOptions = new ConfigServerOptions();
        serverOptions.setDataPath(dataPath);
        serverOptions.setGroupId("config-center-test");
        serverOptions.setServerId("127.0.0.1:8091");
        serverOptions.setInitialServerList("127.0.0.1:8091");
        serverOptions.setPort(SERVER_PORT);

        server = new ConfigServer();
        boolean started = server.start(serverOptions);
        assertTrue("Server should start successfully", started);

        // Wait for server to be ready and leader election to complete
        waitForLeaderElection();

        // Create clients
        AdminClientOptions adminOptions = new AdminClientOptions("http://127.0.0.1:" + SERVER_PORT);
        adminOptions.setTimeoutMs(5000);
        adminOptions.setMaxRetries(3);
        adminClient = new AdminClient(adminOptions);

        AppClientOptions appOptions = new AppClientOptions("http://127.0.0.1:" + SERVER_PORT);
        appOptions.setTimeoutMs(5000);
        appOptions.setPollingIntervalMs(1000);
        appClient = new AppClient(appOptions);
    }

    @After
    public void tearDown() throws Exception {
        // Shutdown clients
        if (adminClient != null) {
            adminClient.shutdown();
        }
        if (appClient != null) {
            appClient.shutdown();
        }

        // Shutdown server
        if (server != null) {
            server.shutdown();
        }

        // Clean up data directory
        if (dataPath != null) {
            FileUtils.deleteQuietly(new File(dataPath));
        }
    }

    /**
     * Feature: distributed-config-center, Property 5: 客户端接口一致性
     * 
     * For any namespace, key and properties, the synchronous putConfig interface
     * and asynchronous putConfigAsync interface should produce the same result.
     * 
     * Validates: Requirements 5.2, 5.3
     */
    @Test
    public void testPutConfigSyncAsyncConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 15),  // namespace
            strings().allPossible().ofLengthBetween(1, 15),  // key
            generateProperties()                              // properties
        ).checkAssert((namespace, key, properties) -> {
            try {
                // Test synchronous interface
                ConfigResponse syncResponse = adminClient.putConfig(namespace, key, properties);
                assertTrue("Sync putConfig should succeed", syncResponse.isSuccess());
                assertNotNull("Sync response should have config entry", syncResponse.getConfigEntry());
                assertEquals("Sync response namespace should match", namespace, 
                    syncResponse.getConfigEntry().getNamespace());
                assertEquals("Sync response key should match", key, 
                    syncResponse.getConfigEntry().getKey());
                assertEquals("Sync response properties should match", properties, 
                    syncResponse.getConfigEntry().getProperties());

                // Delete the config to prepare for async test
                adminClient.deleteConfig(namespace, key);
                Thread.sleep(100);

                // Test asynchronous interface
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<ConfigResponse> asyncResponseRef = new AtomicReference<>();
                AtomicReference<String> errorRef = new AtomicReference<>();

                adminClient.putConfigAsync(namespace, key, properties, new ConfigCallback() {
                    @Override
                    public void onSuccess(ConfigResponse response) {
                        asyncResponseRef.set(response);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        errorRef.set(errorMsg);
                        latch.countDown();
                    }
                });

                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertTrue("Async operation should complete within timeout", completed);
                assertNull("Async operation should not have error: " + errorRef.get(), errorRef.get());

                ConfigResponse asyncResponse = asyncResponseRef.get();
                assertNotNull("Async response should not be null", asyncResponse);
                assertTrue("Async putConfig should succeed", asyncResponse.isSuccess());
                assertNotNull("Async response should have config entry", asyncResponse.getConfigEntry());
                assertEquals("Async response namespace should match", namespace, 
                    asyncResponse.getConfigEntry().getNamespace());
                assertEquals("Async response key should match", key, 
                    asyncResponse.getConfigEntry().getKey());
                assertEquals("Async response properties should match", properties, 
                    asyncResponse.getConfigEntry().getProperties());

                // Verify both interfaces produce consistent results
                assertEquals("Sync and async should have same success status", 
                    syncResponse.isSuccess(), asyncResponse.isSuccess());
                assertEquals("Sync and async should have same namespace", 
                    syncResponse.getConfigEntry().getNamespace(), 
                    asyncResponse.getConfigEntry().getNamespace());
                assertEquals("Sync and async should have same key", 
                    syncResponse.getConfigEntry().getKey(), 
                    asyncResponse.getConfigEntry().getKey());
                assertEquals("Sync and async should have same properties", 
                    syncResponse.getConfigEntry().getProperties(), 
                    asyncResponse.getConfigEntry().getProperties());

                // Clean up
                adminClient.deleteConfig(namespace, key);
                Thread.sleep(50);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted: " + e.getMessage());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 5: 客户端接口一致性
     * 
     * For any namespace and key, the synchronous deleteConfig interface
     * and asynchronous deleteConfigAsync interface should produce the same result.
     * 
     * Validates: Requirements 5.2, 5.3
     */
    @Test
    public void testDeleteConfigSyncAsyncConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 15),  // namespace
            strings().allPossible().ofLengthBetween(1, 15)   // key
        ).checkAssert((namespace, key) -> {
            try {
                // Create a config entry first
                Map<String, String> properties = new HashMap<>();
                properties.put("test", "value");
                adminClient.putConfig(namespace, key, properties);
                Thread.sleep(100);

                // Test synchronous delete
                ConfigResponse syncResponse = adminClient.deleteConfig(namespace, key);
                assertTrue("Sync deleteConfig should succeed", syncResponse.isSuccess());

                // Verify config is deleted
                ConfigResponse getResponse = appClient.getConfig(namespace, key);
                assertFalse("Config should not exist after sync delete", getResponse.isSuccess());

                // Create config again for async test
                adminClient.putConfig(namespace, key, properties);
                Thread.sleep(100);

                // Test asynchronous delete
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<ConfigResponse> asyncResponseRef = new AtomicReference<>();
                AtomicReference<String> errorRef = new AtomicReference<>();

                adminClient.deleteConfigAsync(namespace, key, new ConfigCallback() {
                    @Override
                    public void onSuccess(ConfigResponse response) {
                        asyncResponseRef.set(response);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        errorRef.set(errorMsg);
                        latch.countDown();
                    }
                });

                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertTrue("Async operation should complete within timeout", completed);
                assertNull("Async operation should not have error: " + errorRef.get(), errorRef.get());

                ConfigResponse asyncResponse = asyncResponseRef.get();
                assertNotNull("Async response should not be null", asyncResponse);
                assertTrue("Async deleteConfig should succeed", asyncResponse.isSuccess());

                // Verify config is deleted
                ConfigResponse getResponse2 = appClient.getConfig(namespace, key);
                assertFalse("Config should not exist after async delete", getResponse2.isSuccess());

                // Verify both interfaces produce consistent results
                assertEquals("Sync and async should have same success status", 
                    syncResponse.isSuccess(), asyncResponse.isSuccess());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted: " + e.getMessage());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 5: 客户端接口一致性
     * 
     * For any namespace, the synchronous listConfigs interface
     * and asynchronous listConfigsAsync interface should produce the same result.
     * 
     * Validates: Requirements 5.2, 5.3
     */
    @Test
    public void testListConfigsSyncAsyncConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 15),  // namespace
            integers().between(1, 5)                          // number of configs
        ).checkAssert((namespace, numConfigs) -> {
            try {
                // Create multiple configs
                for (int i = 0; i < numConfigs; i++) {
                    String key = "key-" + i;
                    Map<String, String> properties = new HashMap<>();
                    properties.put("index", String.valueOf(i));
                    adminClient.putConfig(namespace, key, properties);
                }
                Thread.sleep(200);

                // Test synchronous list
                ConfigResponse syncResponse = adminClient.listConfigs(namespace);
                assertTrue("Sync listConfigs should succeed", syncResponse.isSuccess());
                assertEquals("Sync should return correct number of configs", 
                    (long)numConfigs, (long)syncResponse.getConfigEntries().size());

                // Test asynchronous list
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<ConfigResponse> asyncResponseRef = new AtomicReference<>();
                AtomicReference<String> errorRef = new AtomicReference<>();

                adminClient.listConfigsAsync(namespace, new ConfigCallback() {
                    @Override
                    public void onSuccess(ConfigResponse response) {
                        asyncResponseRef.set(response);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        errorRef.set(errorMsg);
                        latch.countDown();
                    }
                });

                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertTrue("Async operation should complete within timeout", completed);
                assertNull("Async operation should not have error: " + errorRef.get(), errorRef.get());

                ConfigResponse asyncResponse = asyncResponseRef.get();
                assertNotNull("Async response should not be null", asyncResponse);
                assertTrue("Async listConfigs should succeed", asyncResponse.isSuccess());
                assertEquals("Async should return correct number of configs", 
                    (long)numConfigs, (long)asyncResponse.getConfigEntries().size());

                // Verify both interfaces produce consistent results
                assertEquals("Sync and async should have same success status", 
                    syncResponse.isSuccess(), asyncResponse.isSuccess());
                assertEquals("Sync and async should return same number of configs", 
                    syncResponse.getConfigEntries().size(), 
                    asyncResponse.getConfigEntries().size());

                // Clean up
                for (int i = 0; i < numConfigs; i++) {
                    adminClient.deleteConfig(namespace, "key-" + i);
                }
                Thread.sleep(100);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted: " + e.getMessage());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 5: 客户端接口一致性
     * 
     * For any valid timeout setting, client operations should respect the timeout
     * and return within the specified time limit or throw timeout exception.
     * 
     * Validates: Requirements 5.4
     */
    @Test
    public void testTimeoutHandling() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 15),  // namespace
            strings().allPossible().ofLengthBetween(1, 15),  // key
            integers().between(1000, 10000)                   // timeout in ms
        ).checkAssert((namespace, key, timeoutMs) -> {
            try {
                // Create a client with specific timeout
                AdminClientOptions options = new AdminClientOptions("http://127.0.0.1:" + SERVER_PORT);
                options.setTimeoutMs(timeoutMs);
                options.setMaxRetries(0); // No retries for timeout test
                AdminClient timeoutClient = new AdminClient(options);

                try {
                    // Perform operation and measure time
                    long startTime = System.currentTimeMillis();
                    Map<String, String> properties = new HashMap<>();
                    properties.put("test", "value");
                    ConfigResponse response = timeoutClient.putConfig(namespace, key, properties);
                    long elapsedTime = System.currentTimeMillis() - startTime;

                    // If operation succeeds, it should complete within timeout
                    if (response.isSuccess()) {
                        assertTrue("Operation should complete within timeout + buffer", 
                            elapsedTime <= timeoutMs + 2000); // 2s buffer for processing
                    }

                    // Clean up
                    timeoutClient.deleteConfig(namespace, key);

                } finally {
                    timeoutClient.shutdown();
                }

            } catch (Exception e) {
                // Timeout exceptions are expected for some cases
                // Just verify the exception is related to timeout
                String errorMsg = e.getMessage().toLowerCase();
                boolean isTimeoutRelated = errorMsg.contains("timeout") || 
                                          errorMsg.contains("timed out") ||
                                          errorMsg.contains("connection");
                assertTrue("Exception should be timeout-related: " + e.getMessage(), isTimeoutRelated);
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 5: 客户端接口一致性
     * 
     * For any namespace and key, async operations should properly handle
     * callback invocation and should not block the calling thread.
     * 
     * Validates: Requirements 5.3, 5.5
     */
    @Test
    public void testAsyncNonBlocking() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 15),  // namespace
            strings().allPossible().ofLengthBetween(1, 15)   // key
        ).checkAssert((namespace, key) -> {
            try {
                Map<String, String> properties = new HashMap<>();
                properties.put("test", "value");

                // Measure time for async call (should return immediately)
                long startTime = System.currentTimeMillis();
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Boolean> callbackInvoked = new AtomicReference<>(false);

                adminClient.putConfigAsync(namespace, key, properties, new ConfigCallback() {
                    @Override
                    public void onSuccess(ConfigResponse response) {
                        callbackInvoked.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        callbackInvoked.set(true);
                        latch.countDown();
                    }
                });

                long asyncCallTime = System.currentTimeMillis() - startTime;

                // Async call should return immediately (within 100ms)
                assertTrue("Async call should return immediately", asyncCallTime < 100);

                // Wait for callback
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertTrue("Callback should be invoked within timeout", completed);
                assertTrue("Callback should have been invoked", callbackInvoked.get());

                // Clean up
                adminClient.deleteConfig(namespace, key);
                Thread.sleep(50);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted: " + e.getMessage());
            }
        });
    }

    /**
     * Feature: distributed-config-center, Property 5: 客户端接口一致性
     * 
     * For any namespace and key, rollback operations should work consistently
     * in both synchronous and asynchronous modes.
     * 
     * Validates: Requirements 5.2, 5.3
     */
    @Test
    public void testRollbackSyncAsyncConsistency() {
        qt().withExamples(100).forAll(
            strings().allPossible().ofLengthBetween(1, 15),  // namespace
            strings().allPossible().ofLengthBetween(1, 15)   // key
        ).checkAssert((namespace, key) -> {
            try {
                // Create initial config
                Map<String, String> properties1 = new HashMap<>();
                properties1.put("version", "1");
                adminClient.putConfig(namespace, key, properties1);
                Thread.sleep(100);

                // Update to version 2
                Map<String, String> properties2 = new HashMap<>();
                properties2.put("version", "2");
                adminClient.putConfig(namespace, key, properties2);
                Thread.sleep(100);

                // Test synchronous rollback to version 1
                ConfigResponse syncResponse = adminClient.rollbackConfig(namespace, key, 1);
                assertTrue("Sync rollback should succeed", syncResponse.isSuccess());

                // Verify rollback
                ConfigResponse getResponse1 = appClient.getConfig(namespace, key);
                assertTrue("Config should exist after rollback", getResponse1.isSuccess());
                assertEquals("Config should have version 1 properties", "1", 
                    getResponse1.getConfigEntry().getProperty("version"));

                // Update to version 4 (version 3 was created by rollback)
                Map<String, String> properties4 = new HashMap<>();
                properties4.put("version", "4");
                adminClient.putConfig(namespace, key, properties4);
                Thread.sleep(100);

                // Test asynchronous rollback to version 1
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<ConfigResponse> asyncResponseRef = new AtomicReference<>();
                AtomicReference<String> errorRef = new AtomicReference<>();

                adminClient.rollbackConfigAsync(namespace, key, 1, new ConfigCallback() {
                    @Override
                    public void onSuccess(ConfigResponse response) {
                        asyncResponseRef.set(response);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String errorMsg) {
                        errorRef.set(errorMsg);
                        latch.countDown();
                    }
                });

                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertTrue("Async operation should complete within timeout", completed);
                assertNull("Async operation should not have error: " + errorRef.get(), errorRef.get());

                ConfigResponse asyncResponse = asyncResponseRef.get();
                assertNotNull("Async response should not be null", asyncResponse);
                assertTrue("Async rollback should succeed", asyncResponse.isSuccess());

                // Verify rollback
                ConfigResponse getResponse2 = appClient.getConfig(namespace, key);
                assertTrue("Config should exist after async rollback", getResponse2.isSuccess());
                assertEquals("Config should have version 1 properties", "1", 
                    getResponse2.getConfigEntry().getProperty("version"));

                // Verify both interfaces produce consistent results
                assertEquals("Sync and async should have same success status", 
                    syncResponse.isSuccess(), asyncResponse.isSuccess());

                // Clean up
                adminClient.deleteConfig(namespace, key);
                Thread.sleep(50);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted: " + e.getMessage());
            }
        });
    }

    /**
     * Wait for leader election to complete
     */
    private void waitForLeaderElection() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeout = 10000; // 10 seconds timeout

        while (System.currentTimeMillis() - startTime < timeout) {
            if (server.getNode() != null && server.getNode().isLeader()) {
                LOG.info("Leader elected successfully");
                // Wait a bit more for the HTTP server to be fully ready
                Thread.sleep(2000);

                // Verify HTTP server is responding
                if (isServerReady()) {
                    LOG.info("HTTP server is ready");
                    return;
                }
            }
            Thread.sleep(500);
        }

        throw new IllegalStateException("Leader election or HTTP server startup timeout after " + timeout + "ms");
    }

    /**
     * Check if HTTP server is ready by calling health endpoint
     */
    private boolean isServerReady() {
        try {
            ConfigResponse response = appClient.getConfig("_health_check", "_test");
            // We don't care if the config exists, just that the server responds
            return true;
        } catch (Exception e) {
            LOG.warn("Server not ready yet: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generator for random property maps
     */
    private Gen<Map<String, String>> generateProperties() {
        return integers().between(1, 3).map(size -> {
            Map<String, String> properties = new HashMap<>();
            for (int i = 0; i < size; i++) {
                properties.put("key-" + i, "value-" + i);
            }
            return properties;
        });
    }
}
