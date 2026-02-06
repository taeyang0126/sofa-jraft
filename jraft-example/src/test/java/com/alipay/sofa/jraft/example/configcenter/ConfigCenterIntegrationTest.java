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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.entity.PeerId;

/**
 * Integration tests for distributed configuration center.
 * Tests multi-node cluster setup, client-server interaction, data consistency, and failover.
 * 
 * Requirements: 4.1, 4.2
 *
 * @author lei
 */
public class ConfigCenterIntegrationTest {

    private static final Logger LOG                  = LoggerFactory.getLogger(ConfigCenterIntegrationTest.class);

    private static final String GROUP_ID             = "config_center_test";
    private static final int    BASE_PORT            = 18080;
    private static final int    BASE_RAFT_PORT       = 18081;
    private static final int    NODE_COUNT           = 3;
    private static final int    ELECTION_TIMEOUT_MS  = 5000;
    private static final int    OPERATION_TIMEOUT_MS = 10000;

    @Rule
    public TemporaryFolder      tempFolder           = new TemporaryFolder();

    private List<ConfigServer>  servers;
    private List<String>        dataPaths;
    private AdminClient         adminClient;
    private AppClient           appClient;

    @Before
    public void setUp() throws Exception {
        LOG.info("Setting up 3-node Raft cluster for integration test");

        servers = new ArrayList<>();
        dataPaths = new ArrayList<>();

        // Build initial server list
        StringBuilder initialServerList = new StringBuilder();
        for (int i = 0; i < NODE_COUNT; i++) {
            if (i > 0) {
                initialServerList.append(",");
            }
            initialServerList.append("127.0.0.1:").append(BASE_RAFT_PORT + i);
        }

        // Start all nodes
        for (int i = 0; i < NODE_COUNT; i++) {
            String dataPath = tempFolder.newFolder("node" + i).getAbsolutePath();
            dataPaths.add(dataPath);

            ConfigServerOptions opts = new ConfigServerOptions(dataPath, GROUP_ID, "127.0.0.1:" + (BASE_RAFT_PORT + i),
                initialServerList.toString(), BASE_PORT + i);

            ConfigServer server = new ConfigServer();
            boolean started = server.start(opts);
            assertTrue("Failed to start server " + i, started);
            servers.add(server);

            LOG.info("Started node {}: HTTP port={}, Raft port={}", i, BASE_PORT + i, BASE_RAFT_PORT + i);
        }

        // Wait for leader election
        LOG.info("Waiting for leader election...");
        waitForLeader();

        // Find the leader and create clients pointing to it
        ConfigServer leaderServer = getLeaderServer();
        assertNotNull("Leader server should exist", leaderServer);
        int leaderPort = leaderServer.getOptions().getPort();
        String serverUrl = "http://127.0.0.1:" + leaderPort;

        LOG.info("Leader is on port {}", leaderPort);

        adminClient = new AdminClient(new AdminClientOptions(serverUrl, OPERATION_TIMEOUT_MS, 3));
        appClient = new AppClient(new AppClientOptions(serverUrl, OPERATION_TIMEOUT_MS, 30000));

        LOG.info("Integration test setup completed");
    }

    @After
    public void tearDown() throws Exception {
        LOG.info("Tearing down integration test");

        // Shutdown clients
        if (adminClient != null) {
            adminClient.shutdown();
        }
        if (appClient != null) {
            appClient.shutdown();
        }

        // Shutdown all servers
        for (ConfigServer server : servers) {
            if (server != null) {
                server.shutdown();
            }
        }

        // Clean up data directories
        for (String dataPath : dataPaths) {
            try {
                FileUtils.deleteDirectory(new File(dataPath));
            } catch (IOException e) {
                LOG.warn("Failed to delete data directory: {}", dataPath, e);
            }
        }

        LOG.info("Integration test teardown completed");
    }

    // ========== Cluster Setup Tests ==========

    @Test
    public void testClusterStartup() {
        // Verify all nodes are started
        assertEquals(NODE_COUNT, servers.size());
        for (ConfigServer server : servers) {
            assertNotNull(server.getNode());
            assertNotNull(server.getFsm());
            assertNotNull(server.getHttpServer());
        }

        // Verify leader is elected
        Node leader = getLeader();
        assertNotNull("Leader should be elected", leader);
        LOG.info("Leader elected: {}", leader.getNodeId());
    }

    @Test
    public void testLeaderElection() throws Exception {
        Node leader = getLeader();
        assertNotNull("Leader should be elected", leader);

        // Verify only one leader exists
        int leaderCount = 0;
        for (ConfigServer server : servers) {
            if (server.getNode().isLeader()) {
                leaderCount++;
            }
        }
        assertEquals("Should have exactly one leader", 1, leaderCount);
    }

    // ========== Basic CRUD Operations Tests ==========

    @Test
    public void testPutAndGetConfig() throws Exception {
        // Put a configuration
        Map<String, String> properties = new HashMap<>();
        properties.put("db.host", "localhost");
        properties.put("db.port", "3306");

        ConfigResponse putResponse = adminClient.putConfig("test-app", "database", properties);
        assertTrue("Put config should succeed", putResponse.isSuccess());
        assertNotNull(putResponse.getConfigEntry());
        assertEquals("test-app", putResponse.getConfigEntry().getNamespace());
        assertEquals("database", putResponse.getConfigEntry().getKey());
        assertEquals(1L, putResponse.getConfigEntry().getVersion());

        // Wait for replication
        Thread.sleep(500);

        // Get the configuration
        ConfigResponse getResponse = appClient.getConfig("test-app", "database");
        assertTrue("Get config should succeed", getResponse.isSuccess());
        assertNotNull(getResponse.getConfigEntry());
        assertEquals("test-app", getResponse.getConfigEntry().getNamespace());
        assertEquals("database", getResponse.getConfigEntry().getKey());
        assertEquals("localhost", getResponse.getConfigEntry().getProperty("db.host"));
        assertEquals("3306", getResponse.getConfigEntry().getProperty("db.port"));
    }

    @Test
    public void testUpdateConfig() throws Exception {
        // Create initial config
        Map<String, String> properties = new HashMap<>();
        properties.put("timeout", "5000");

        ConfigResponse putResponse = adminClient.putConfig("test-app", "settings", properties);
        assertTrue(putResponse.isSuccess());
        assertEquals(1L, putResponse.getConfigEntry().getVersion());

        Thread.sleep(500);

        // Update config
        Map<String, String> updatedProperties = new HashMap<>();
        updatedProperties.put("timeout", "10000");
        updatedProperties.put("retries", "3");

        ConfigResponse updateResponse = adminClient.putConfig("test-app", "settings", updatedProperties);
        assertTrue(updateResponse.isSuccess());
        assertEquals(2L, updateResponse.getConfigEntry().getVersion());

        Thread.sleep(500);

        // Verify updated config
        ConfigResponse getResponse = appClient.getConfig("test-app", "settings");
        assertTrue(getResponse.isSuccess());
        assertEquals("10000", getResponse.getConfigEntry().getProperty("timeout"));
        assertEquals("3", getResponse.getConfigEntry().getProperty("retries"));
        assertEquals(2L, getResponse.getConfigEntry().getVersion());
    }

    @Test
    public void testDeleteConfig() throws Exception {
        // Create config
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");

        ConfigResponse putResponse = adminClient.putConfig("test-app", "temp-config", properties);
        assertTrue("Put should succeed: " + (putResponse.isSuccess() ? "" : putResponse.getErrorMsg()),
            putResponse.isSuccess());

        Thread.sleep(1000);

        // Delete config
        ConfigResponse deleteResponse = adminClient.deleteConfig("test-app", "temp-config");
        assertTrue("Delete should succeed: " + (deleteResponse.isSuccess() ? "" : deleteResponse.getErrorMsg()),
            deleteResponse.isSuccess());

        Thread.sleep(1000);

        // Verify config is deleted
        ConfigResponse getResponse = appClient.getConfig("test-app", "temp-config");
        assertFalse("Config should not exist after deletion", getResponse.isSuccess());
    }

    @Test
    public void testListConfigs() throws Exception {
        // Create multiple configs in same namespace
        for (int i = 0; i < 5; i++) {
            Map<String, String> properties = new HashMap<>();
            properties.put("value", "config" + i);
            ConfigResponse response = adminClient.putConfig("test-namespace", "config" + i, properties);
            assertTrue("Put config" + i + " should succeed: " + (response.isSuccess() ? "" : response.getErrorMsg()),
                response.isSuccess());
        }

        Thread.sleep(1000);

        // List all configs in namespace
        ConfigResponse listResponse = adminClient.listConfigs("test-namespace");
        assertTrue("List should succeed: " + (listResponse.isSuccess() ? "" : listResponse.getErrorMsg()),
            listResponse.isSuccess());
        assertTrue(listResponse.hasConfigEntries());
        assertEquals(5, listResponse.getConfigEntries().size());
    }

    // ========== Namespace Isolation Tests ==========

    @Test
    public void testNamespaceIsolation() throws Exception {
        // Create same key in different namespaces
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("env", "production");
        adminClient.putConfig("app1", "config", properties1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("env", "development");
        adminClient.putConfig("app2", "config", properties2);

        Thread.sleep(500);

        // Verify configs are isolated
        ConfigResponse response1 = appClient.getConfig("app1", "config");
        assertTrue(response1.isSuccess());
        assertEquals("production", response1.getConfigEntry().getProperty("env"));

        ConfigResponse response2 = appClient.getConfig("app2", "config");
        assertTrue(response2.isSuccess());
        assertEquals("development", response2.getConfigEntry().getProperty("env"));
    }

    // ========== Data Consistency Tests ==========

    @Test
    public void testDataConsistencyAcrossNodes() throws Exception {
        // Write config through admin client
        Map<String, String> properties = new HashMap<>();
        properties.put("consistency", "test");
        ConfigResponse putResponse = adminClient.putConfig("consistency-test", "config1", properties);
        assertTrue("Put should succeed: " + (putResponse.isSuccess() ? "" : putResponse.getErrorMsg()),
            putResponse.isSuccess());

        // Wait for replication
        Thread.sleep(2000);

        // Read from all nodes and verify consistency
        for (int i = 0; i < NODE_COUNT; i++) {
            String serverUrl = "http://127.0.0.1:" + (BASE_PORT + i);
            AppClient client = new AppClient(new AppClientOptions(serverUrl, OPERATION_TIMEOUT_MS, 30000));

            ConfigResponse getResponse = client.getConfig("consistency-test", "config1");
            assertTrue(
                "Node " + i + " should have the config: " + (getResponse.isSuccess() ? "" : getResponse.getErrorMsg()),
                getResponse.isSuccess());
            assertEquals("test", getResponse.getConfigEntry().getProperty("consistency"));

            client.shutdown();
        }
    }

    @Test
    public void testConcurrentWrites() throws Exception {
        final int writeCount = 10;
        final CountDownLatch latch = new CountDownLatch(writeCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        // Perform concurrent writes
        for (int i = 0; i < writeCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    Map<String, String> properties = new HashMap<>();
                    properties.put("index", String.valueOf(index));
                    ConfigResponse response = adminClient.putConfig("concurrent-test", "config" + index, properties);
                    if (response.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    LOG.error("Concurrent write failed", e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue("All writes should complete", latch.await(30, TimeUnit.SECONDS));
        assertEquals("All writes should succeed", writeCount, successCount.get());

        Thread.sleep(1000);

        // Verify all configs exist
        ConfigResponse listResponse = adminClient.listConfigs("concurrent-test");
        assertTrue(listResponse.isSuccess());
        assertEquals(writeCount, listResponse.getConfigEntries().size());
    }

    // ========== Failover Tests ==========

    @Test
    public void testLeaderFailover() throws Exception {
        // Create initial config
        Map<String, String> properties = new HashMap<>();
        properties.put("failover", "test");
        ConfigResponse putResponse = adminClient.putConfig("failover-test", "config1", properties);
        assertTrue("Put should succeed: " + (putResponse.isSuccess() ? "" : putResponse.getErrorMsg()),
            putResponse.isSuccess());

        Thread.sleep(1000);

        // Find and shutdown the leader
        ConfigServer leaderServer = null;
        int leaderIndex = -1;
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getNode().isLeader()) {
                leaderServer = servers.get(i);
                leaderIndex = i;
                break;
            }
        }

        assertNotNull("Leader should exist", leaderServer);
        LOG.info("Shutting down leader at index {}", leaderIndex);
        leaderServer.shutdown();

        // Wait for new leader election
        LOG.info("Waiting for new leader election...");
        Thread.sleep(ELECTION_TIMEOUT_MS + 2000);

        // Verify new leader is elected
        Node newLeader = getLeader();
        assertNotNull("New leader should be elected", newLeader);
        LOG.info("New leader elected: {}", newLeader.getNodeId());

        // Update client to point to a follower node
        int followerIndex = (leaderIndex + 1) % NODE_COUNT;
        String followerUrl = "http://127.0.0.1:" + (BASE_PORT + followerIndex);
        adminClient.shutdown();
        adminClient = new AdminClient(new AdminClientOptions(followerUrl, OPERATION_TIMEOUT_MS, 3));

        // Verify we can still write after failover
        Map<String, String> newProperties = new HashMap<>();
        newProperties.put("failover", "after-leader-change");
        ConfigResponse updateResponse = adminClient.putConfig("failover-test", "config2", newProperties);
        assertTrue(
            "Write should succeed after failover: " + (updateResponse.isSuccess() ? "" : updateResponse.getErrorMsg()),
            updateResponse.isSuccess());

        Thread.sleep(1000);

        // Verify we can still read
        appClient.shutdown();
        appClient = new AppClient(new AppClientOptions(followerUrl, OPERATION_TIMEOUT_MS, 30000));
        ConfigResponse getResponse = appClient.getConfig("failover-test", "config2");
        assertTrue("Read should succeed after failover: " + (getResponse.isSuccess() ? "" : getResponse.getErrorMsg()),
            getResponse.isSuccess());
        assertEquals("after-leader-change", getResponse.getConfigEntry().getProperty("failover"));
    }

    @Test
    public void testFollowerFailure() throws Exception {
        // Create initial config
        Map<String, String> properties = new HashMap<>();
        properties.put("test", "follower-failure");
        ConfigResponse putResponse = adminClient.putConfig("follower-test", "config1", properties);
        assertTrue("Put should succeed: " + (putResponse.isSuccess() ? "" : putResponse.getErrorMsg()),
            putResponse.isSuccess());

        Thread.sleep(1000);

        // Find and shutdown a follower
        ConfigServer followerServer = null;
        for (ConfigServer server : servers) {
            if (!server.getNode().isLeader()) {
                followerServer = server;
                break;
            }
        }

        assertNotNull("Follower should exist", followerServer);
        LOG.info("Shutting down follower");
        followerServer.shutdown();

        Thread.sleep(2000);

        // Verify cluster still works with 2 nodes
        Map<String, String> newProperties = new HashMap<>();
        newProperties.put("test", "after-follower-failure");
        ConfigResponse updateResponse = adminClient.putConfig("follower-test", "config2", newProperties);
        assertTrue(
            "Write should succeed with 2 nodes: " + (updateResponse.isSuccess() ? "" : updateResponse.getErrorMsg()),
            updateResponse.isSuccess());

        Thread.sleep(1000);

        ConfigResponse getResponse = appClient.getConfig("follower-test", "config2");
        assertTrue("Read should succeed with 2 nodes: " + (getResponse.isSuccess() ? "" : getResponse.getErrorMsg()),
            getResponse.isSuccess());
    }

    // ========== Async Operations Tests ==========

    @Test
    public void testAsyncOperations() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicReference<ConfigResponse> putResponseRef = new AtomicReference<>();
        final AtomicReference<ConfigResponse> deleteResponseRef = new AtomicReference<>();

        // Async put
        Map<String, String> properties = new HashMap<>();
        properties.put("async", "test");
        adminClient.putConfigAsync("async-test", "config1", properties, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                putResponseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                LOG.error("Async put failed: {}", errorMsg);
                latch.countDown();
            }
        });

        // Wait for put to complete
        Thread.sleep(2000);

        // Sync get to verify
        ConfigResponse getResponse = appClient.getConfig("async-test", "config1");
        assertTrue("Get should succeed: " + (getResponse.isSuccess() ? "" : getResponse.getErrorMsg()),
            getResponse.isSuccess());
        assertEquals("test", getResponse.getConfigEntry().getProperty("async"));

        // Async delete
        adminClient.deleteConfigAsync("async-test", "config1", new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                deleteResponseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                LOG.error("Async delete failed: {}", errorMsg);
                latch.countDown();
            }
        });

        assertTrue("All async operations should complete", latch.await(30, TimeUnit.SECONDS));

        assertNotNull(putResponseRef.get());
        assertTrue(putResponseRef.get().isSuccess());

        assertNotNull(deleteResponseRef.get());
        assertTrue(deleteResponseRef.get().isSuccess());
    }

    // ========== Version Management Tests ==========

    @Test
    public void testVersionIncrement() throws Exception {
        // Create config
        Map<String, String> properties = new HashMap<>();
        properties.put("version", "1");
        ConfigResponse response1 = adminClient.putConfig("version-test", "config1", properties);
        assertTrue(response1.isSuccess());
        assertEquals(1L, response1.getConfigEntry().getVersion());

        Thread.sleep(500);

        // Update config multiple times
        for (int i = 2; i <= 5; i++) {
            Map<String, String> updatedProperties = new HashMap<>();
            updatedProperties.put("version", String.valueOf(i));
            ConfigResponse response = adminClient.putConfig("version-test", "config1", updatedProperties);
            assertTrue(response.isSuccess());
            assertEquals((long) i, response.getConfigEntry().getVersion());
            Thread.sleep(300);
        }

        // Verify final version
        ConfigResponse getResponse = appClient.getConfig("version-test", "config1");
        assertTrue(getResponse.isSuccess());
        assertEquals(5L, getResponse.getConfigEntry().getVersion());
        assertEquals("5", getResponse.getConfigEntry().getProperty("version"));
    }

    // ========== Helper Methods ==========

    /**
     * Wait for leader election with timeout
     */
    private void waitForLeader() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < ELECTION_TIMEOUT_MS) {
            Node leader = getLeader();
            if (leader != null) {
                LOG.info("Leader elected: {}", leader.getNodeId());
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Leader election timeout");
    }

    /**
     * Get the current leader node
     *
     * @return leader node or null if no leader
     */
    private Node getLeader() {
        for (ConfigServer server : servers) {
            Node node = server.getNode();
            if (node != null && node.isLeader()) {
                return node;
            }
        }
        return null;
    }

    /**
     * Get leader server
     *
     * @return leader server or null if no leader
     */
    private ConfigServer getLeaderServer() {
        for (ConfigServer server : servers) {
            if (server.getNode() != null && server.getNode().isLeader()) {
                return server;
            }
        }
        return null;
    }

    /**
     * Get a follower server
     *
     * @return follower server or null if no follower
     */
    private ConfigServer getFollowerServer() {
        for (ConfigServer server : servers) {
            if (server.getNode() != null && !server.getNode().isLeader()) {
                return server;
            }
        }
        return null;
    }
}
