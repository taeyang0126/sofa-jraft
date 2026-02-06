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
 * Property-based tests for AppClient.
 * Tests namespace isolation properties to ensure operations in one namespace
 * do not affect configurations in another namespace.
 *
 * @author lei
 */
public class AppClientPropertyTest {

    private static final Logger LOG             = LoggerFactory.getLogger(AppClientPropertyTest.class);
    private static final String TEST_DATA_PATH  = "/tmp/app_client_property_test";
    private static final String GROUP_ID        = "config_center_test";
    private static final String SERVER_ID       = "127.0.0.1:8381";
    private static final String INITIAL_SERVERS = "127.0.0.1:8381";
    private static final int    HTTP_PORT       = 8380;

    private ConfigServer        server;
    private AdminClient         adminClient;
    private AppClient           appClient;
    private String              testDataPath;

    @Before
    public void setUp() throws Exception {
        // Create unique test data path for each test
        testDataPath = TEST_DATA_PATH + "_" + System.currentTimeMillis();
        FileUtils.forceMkdir(new File(testDataPath));
        LOG.info("Test data path: {}", testDataPath);

        // Start ConfigServer
        ConfigServerOptions serverOpts = new ConfigServerOptions(testDataPath, GROUP_ID, SERVER_ID, INITIAL_SERVERS,
            HTTP_PORT);
        server = new ConfigServer();
        boolean started = server.start(serverOpts);
        assertTrue("Server should start successfully", started);

        // Wait for server to be ready
        waitForLeader(server, 5000);
        LOG.info("ConfigServer started and ready");

        // Wait for HTTP server to be ready
        Thread.sleep(1000);
        LOG.info("Waiting for HTTP server to be ready...");

        // Initialize AdminClient for setup operations
        AdminClientOptions adminOpts = new AdminClientOptions();
        adminOpts.setServerUrl("http://127.0.0.1:" + HTTP_PORT);
        adminOpts.setTimeoutMs(5000);
        adminClient = new AdminClient(adminOpts);
        LOG.info("AdminClient initialized");

        // Initialize AppClient for read operations
        AppClientOptions appOpts = new AppClientOptions();
        appOpts.setServerUrl("http://127.0.0.1:" + HTTP_PORT);
        appOpts.setTimeoutMs(5000);
        appClient = new AppClient(appOpts);
        LOG.info("AppClient initialized");
    }

    @After
    public void tearDown() throws IOException {
        // Shutdown clients
        if (appClient != null) {
            appClient.shutdown();
            appClient = null;
        }

        if (adminClient != null) {
            adminClient.shutdown();
            adminClient = null;
        }

        // Shutdown server
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
     * Feature: distributed-config-center, Property 2: 命名空间隔离性
     * 
     * For any two different namespaces and the same configuration key,
     * operations in one namespace should not affect the configuration
     * with the same name in another namespace.
     * 
     * Validates: Requirements 2.1
     */
    @Test
    public void testNamespaceIsolation() {
        qt().withExamples(100).forAll(
            strings().betweenCodePoints('a', 'z').ofLengthBetween(1, 20),  // namespace1 (alphanumeric only)
            strings().betweenCodePoints('a', 'z').ofLengthBetween(1, 20),  // namespace2 (alphanumeric only)
            strings().betweenCodePoints('a', 'z').ofLengthBetween(1, 20),  // key (alphanumeric only)
            generatePropertiesPair()                                         // pair of properties
        ).assuming((ns1, ns2, key, propsPair) -> {
            // Only test when namespaces are different
            return !ns1.equals(ns2);
        }).checkAssert((namespace1, namespace2, key, propertiesPair) -> {
            Map<String, String> properties1 = propertiesPair[0];
            Map<String, String> properties2 = propertiesPair[1];
            try {
                LOG.debug("Testing namespace isolation: ns1={}, ns2={}, key={}", namespace1, namespace2, key);

                // Create config in namespace1
                ConfigResponse putResponse1 = adminClient.putConfig(namespace1, key, properties1);
                if (!putResponse1.isSuccess()) {
                    LOG.warn("Failed to put config in namespace1: {}, retrying...", putResponse1.getErrorMsg());
                    Thread.sleep(500);
                    putResponse1 = adminClient.putConfig(namespace1, key, properties1);
                }
                assertTrue("Put config in namespace1 should succeed: " + putResponse1.getErrorMsg(),
                    putResponse1.isSuccess());
                assertNotNull("Put response1 should contain config entry", putResponse1.getConfigEntry());

                // Create config in namespace2 with same key but different properties
                ConfigResponse putResponse2 = adminClient.putConfig(namespace2, key, properties2);
                if (!putResponse2.isSuccess()) {
                    LOG.warn("Failed to put config in namespace2: {}, retrying...", putResponse2.getErrorMsg());
                    Thread.sleep(500);
                    putResponse2 = adminClient.putConfig(namespace2, key, properties2);
                }
                assertTrue("Put config in namespace2 should succeed: " + putResponse2.getErrorMsg(),
                    putResponse2.isSuccess());
                assertNotNull("Put response2 should contain config entry", putResponse2.getConfigEntry());

                // Wait a bit for consistency
                Thread.sleep(100);

                // Read config from namespace1 using AppClient
                ConfigResponse getResponse1 = appClient.getConfig(namespace1, key);
                assertTrue("Get config from namespace1 should succeed: " + getResponse1.getErrorMsg(),
                    getResponse1.isSuccess());
                assertNotNull("Get response1 should contain config entry", getResponse1.getConfigEntry());

                ConfigEntry entry1 = getResponse1.getConfigEntry();
                assertEquals("Namespace1 should match", namespace1, entry1.getNamespace());
                assertEquals("Key should match", key, entry1.getKey());
                assertEquals("Properties in namespace1 should match original", properties1, entry1.getProperties());

                // Read config from namespace2 using AppClient
                ConfigResponse getResponse2 = appClient.getConfig(namespace2, key);
                assertTrue("Get config from namespace2 should succeed: " + getResponse2.getErrorMsg(),
                    getResponse2.isSuccess());
                assertNotNull("Get response2 should contain config entry", getResponse2.getConfigEntry());

                ConfigEntry entry2 = getResponse2.getConfigEntry();
                assertEquals("Namespace2 should match", namespace2, entry2.getNamespace());
                assertEquals("Key should match", key, entry2.getKey());
                assertEquals("Properties in namespace2 should match original", properties2, entry2.getProperties());

                // Verify isolation: properties should be different if input was different
                if (!properties1.equals(properties2)) {
                    assertNotEquals("Properties should be different across namespaces", entry1.getProperties(),
                        entry2.getProperties());
                }

                // Update config in namespace1
                Map<String, String> updatedProperties1 = new HashMap<>(properties1);
                updatedProperties1.put("updated", "true");
                updatedProperties1.put("namespace", namespace1);

                ConfigResponse updateResponse1 = adminClient.putConfig(namespace1, key, updatedProperties1);
                assertTrue("Update config in namespace1 should succeed: " + updateResponse1.getErrorMsg(),
                    updateResponse1.isSuccess());

                // Wait for consistency
                Thread.sleep(100);

                // Verify namespace1 was updated
                ConfigResponse getUpdated1 = appClient.getConfig(namespace1, key);
                assertTrue("Get updated config from namespace1 should succeed: " + getUpdated1.getErrorMsg(),
                    getUpdated1.isSuccess());
                assertEquals("Updated properties in namespace1 should match", updatedProperties1,
                    getUpdated1.getConfigEntry().getProperties());
                assertEquals("Version in namespace1 should be 2", 2L, getUpdated1.getConfigEntry().getVersion());

                // Verify namespace2 was NOT affected by update in namespace1
                ConfigResponse getUnchanged2 = appClient.getConfig(namespace2, key);
                assertTrue("Get config from namespace2 should still succeed: " + getUnchanged2.getErrorMsg(),
                    getUnchanged2.isSuccess());
                assertEquals("Properties in namespace2 should remain unchanged", properties2,
                    getUnchanged2.getConfigEntry().getProperties());
                assertEquals("Version in namespace2 should still be 1", 1L,
                    getUnchanged2.getConfigEntry().getVersion());

                // Delete config in namespace1
                ConfigResponse deleteResponse1 = adminClient.deleteConfig(namespace1, key);
                assertTrue("Delete config in namespace1 should succeed: " + deleteResponse1.getErrorMsg(),
                    deleteResponse1.isSuccess());

                // Wait for consistency
                Thread.sleep(100);

                // Verify namespace1 config is deleted
                ConfigResponse getDeleted1 = appClient.getConfig(namespace1, key);
                assertFalse("Get config from namespace1 should fail after delete", getDeleted1.isSuccess());

                // Verify namespace2 config still exists and is unchanged
                ConfigResponse getStillExists2 = appClient.getConfig(namespace2, key);
                assertTrue("Get config from namespace2 should still succeed after delete in namespace1: "
                           + getStillExists2.getErrorMsg(), getStillExists2.isSuccess());
                assertEquals("Properties in namespace2 should still be unchanged", properties2,
                    getStillExists2.getConfigEntry().getProperties());
                assertEquals("Version in namespace2 should still be 1", 1L,
                    getStillExists2.getConfigEntry().getVersion());

                LOG.debug("Namespace isolation verified: ns1={}, ns2={}, key={}", namespace1, namespace2, key);

            } catch (Exception e) {
                LOG.error("Test failed for namespace1={}, namespace2={}, key={}", namespace1, namespace2, key, e);
                fail("Test failed: " + e.getMessage());
            }
        });
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

    /**
     * Generator for a pair of property maps
     */
    @SuppressWarnings("unchecked")
    private Gen<Map<String, String>[]> generatePropertiesPair() {
        return integers().between(1, 5).zip(integers().between(1, 5), (size1, size2) -> {
            Map<String, String> properties1 = new HashMap<>();
            for (int i = 0; i < size1; i++) {
                properties1.put("key-" + i, "value-" + i);
            }

            Map<String, String> properties2 = new HashMap<>();
            for (int i = 0; i < size2; i++) {
                properties2.put("prop-" + i, "val-" + i);
            }

            return new Map[] { properties1, properties2 };
        });
    }
}
