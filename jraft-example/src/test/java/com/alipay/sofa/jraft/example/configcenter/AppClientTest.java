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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unit tests for AppClient.
 * Tests consistent reads using ReadIndex and configuration polling mechanism.
 * 
 * Requirements: 1.2, 5.2
 *
 * @author lei
 */
public class AppClientTest {

    private static final String TEST_HOST = "localhost";

    private AppClient           appClient;
    private Vertx               vertx;
    private HttpServer          mockServer;
    private int                 testPort;
    private String              testServerUrl;

    @Before
    public void setUp() throws Exception {
        vertx = Vertx.vertx();

        // Use a random available port for each test
        testPort = 9998 + (int) (Math.random() * 100);
        testServerUrl = "http://" + TEST_HOST + ":" + testPort;

        // Create app client with test configuration
        AppClientOptions options = new AppClientOptions(testServerUrl, 5000, 1000);
        appClient = new AppClient(options);
    }

    @After
    public void tearDown() throws Exception {
        if (mockServer != null) {
            CountDownLatch latch = new CountDownLatch(1);
            mockServer.close(result -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
            mockServer = null;
        }
        if (appClient != null) {
            appClient.shutdown();
            appClient = null;
        }
        if (vertx != null) {
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close(result -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
            vertx = null;
        }
        // Give time for resources to be fully released
        Thread.sleep(200);
    }

    // ========== Constructor and Initialization Tests ==========

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullOptions() {
        new AppClient(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidUrl() {
        AppClientOptions options = new AppClientOptions("invalid-url", 5000, 1000);
        new AppClient(options);
    }

    @Test
    public void testConstructorWithValidOptions() {
        AppClientOptions options = new AppClientOptions(testServerUrl, 5000, 1000);
        AppClient client = new AppClient(options);
        assertNotNull(client);
        client.shutdown();
    }

    // ========== Null Parameter Tests ==========

    @Test(expected = NullPointerException.class)
    public void testGetConfigWithNullNamespace() {
        appClient.getConfig(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigWithNullKey() {
        appClient.getConfig("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigByVersionWithNullNamespace() {
        appClient.getConfigByVersion(null, "test-key", 1L);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigByVersionWithNullKey() {
        appClient.getConfigByVersion("test-namespace", null, 1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConfigByVersionWithZeroVersion() {
        appClient.getConfigByVersion("test-namespace", "test-key", 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetConfigByVersionWithNegativeVersion() {
        appClient.getConfigByVersion("test-namespace", "test-key", -1L);
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigHistoryWithNullNamespace() {
        appClient.getConfigHistory(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testGetConfigHistoryWithNullKey() {
        appClient.getConfigHistory("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testStartPollingWithNullNamespace() {
        appClient.startPolling(null, "test-key", 1000, new TestConfigChangeListener());
    }

    @Test(expected = NullPointerException.class)
    public void testStartPollingWithNullKey() {
        appClient.startPolling("test-namespace", null, 1000, new TestConfigChangeListener());
    }

    @Test(expected = NullPointerException.class)
    public void testStartPollingWithNullListener() {
        appClient.startPolling("test-namespace", "test-key", 1000, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartPollingWithZeroInterval() {
        appClient.startPolling("test-namespace", "test-key", 0, new TestConfigChangeListener());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartPollingWithNegativeInterval() {
        appClient.startPolling("test-namespace", "test-key", -1, new TestConfigChangeListener());
    }

    @Test(expected = NullPointerException.class)
    public void testStopPollingWithNullNamespace() {
        appClient.stopPolling(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testStopPollingWithNullKey() {
        appClient.stopPolling("test-namespace", null);
    }

    // ========== Consistent Read Tests (ReadIndex) ==========

    @Test
    public void testGetConfigSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace/test-key?readIndex=true")) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        ConfigResponse response = appClient.getConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("test-namespace", response.getConfigEntry().getNamespace());
        assertEquals("test-key", response.getConfigEntry().getKey());
        assertEquals("value1", response.getConfigEntry().getProperty("key1"));
    }

    @Test
    public void testGetConfigNotFound() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("error", "Config not found");
            request.response().setStatusCode(404).end(response.encode());
        });

        ConfigResponse response = appClient.getConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
        assertTrue(response.getErrorMsg().contains("Config not found"));
    }

    @Test
    public void testGetConfigByVersionSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace/test-key/version/2")) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "old-value"))
                    .put("version", 2L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        ConfigResponse response = appClient.getConfigByVersion("test-namespace", "test-key", 2L);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("test-namespace", response.getConfigEntry().getNamespace());
        assertEquals("test-key", response.getConfigEntry().getKey());
        assertEquals(2L, response.getConfigEntry().getVersion());
        assertEquals("old-value", response.getConfigEntry().getProperty("key1"));
    }

    @Test
    public void testGetConfigHistorySuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace/test-key/history")) {
                JsonArray configs = new JsonArray()
                    .add(new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value1"))
                        .put("version", 1L))
                    .add(new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value2"))
                        .put("version", 2L))
                    .add(new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value3"))
                        .put("version", 3L));
                JsonObject response = new JsonObject().put("configs", configs);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        ConfigResponse response = appClient.getConfigHistory("test-namespace", "test-key");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertTrue(response.hasConfigEntries());
        assertEquals(3, response.getConfigEntries().size());
        assertEquals(1L, response.getConfigEntries().get(0).getVersion());
        assertEquals(2L, response.getConfigEntries().get(1).getVersion());
        assertEquals(3L, response.getConfigEntries().get(2).getVersion());
    }

    @Test
    public void testGetConfigServerError() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("error", "Internal server error");
            request.response().setStatusCode(500).end(response.encode());
        });

        ConfigResponse response = appClient.getConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
        assertTrue(response.getErrorMsg().contains("Internal server error"));
    }

    @Test
    public void testGetConfigConnectionFailure() {
        // No mock server started - connection should fail
        ConfigResponse response = appClient.getConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
    }

    // ========== Polling Mechanism Tests ==========

    @Test
    public void testStartPollingDetectsNewConfig() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger(0);
        
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace/test-key?readIndex=true")) {
                int count = requestCount.incrementAndGet();
                
                if (count == 1) {
                    // First request: config not found
                    JsonObject response = new JsonObject().put("error", "Config not found");
                    request.response().setStatusCode(404).end(response.encode());
                } else {
                    // Subsequent requests: config exists
                    JsonObject response = new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value1"))
                        .put("version", 1L)
                        .put("createTime", System.currentTimeMillis())
                        .put("updateTime", System.currentTimeMillis())
                        .put("deleted", false);
                    request.response().setStatusCode(200).end(response.encode());
                }
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigEntry> newEntryRef = new AtomicReference<>();

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                if (oldEntry == null && newEntry != null) {
                    newEntryRef.set(newEntry);
                    latch.countDown();
                }
            }
        };

        appClient.startPolling("test-namespace", "test-key", 500, listener);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(newEntryRef.get());
        assertEquals("test-namespace", newEntryRef.get().getNamespace());
        assertEquals("test-key", newEntryRef.get().getKey());
        assertEquals("value1", newEntryRef.get().getProperty("key1"));

        appClient.stopPolling("test-namespace", "test-key");
    }

    @Test
    public void testStartPollingDetectsConfigUpdate() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger(0);
        
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace/test-key?readIndex=true")) {
                int count = requestCount.incrementAndGet();
                
                if (count <= 2) {
                    // First few requests: version 1
                    JsonObject response = new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value1"))
                        .put("version", 1L)
                        .put("createTime", System.currentTimeMillis())
                        .put("updateTime", System.currentTimeMillis())
                        .put("deleted", false);
                    request.response().setStatusCode(200).end(response.encode());
                } else {
                    // Later requests: version 2 with updated value
                    JsonObject response = new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value2"))
                        .put("version", 2L)
                        .put("createTime", System.currentTimeMillis())
                        .put("updateTime", System.currentTimeMillis())
                        .put("deleted", false);
                    request.response().setStatusCode(200).end(response.encode());
                }
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigEntry> oldEntryRef = new AtomicReference<>();
        final AtomicReference<ConfigEntry> newEntryRef = new AtomicReference<>();

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                if (oldEntry != null && newEntry != null && oldEntry.getVersion() != newEntry.getVersion()) {
                    oldEntryRef.set(oldEntry);
                    newEntryRef.set(newEntry);
                    latch.countDown();
                }
            }
        };

        appClient.startPolling("test-namespace", "test-key", 500, listener);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(oldEntryRef.get());
        assertNotNull(newEntryRef.get());
        assertEquals(1L, oldEntryRef.get().getVersion());
        assertEquals("value1", oldEntryRef.get().getProperty("key1"));
        assertEquals(2L, newEntryRef.get().getVersion());
        assertEquals("value2", newEntryRef.get().getProperty("key1"));

        appClient.stopPolling("test-namespace", "test-key");
    }

    @Test
    public void testStartPollingDetectsConfigDeletion() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger(0);
        
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace/test-key?readIndex=true")) {
                int count = requestCount.incrementAndGet();
                
                if (count <= 2) {
                    // First few requests: config exists
                    JsonObject response = new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "test-key")
                        .put("properties", new JsonObject().put("key1", "value1"))
                        .put("version", 1L)
                        .put("createTime", System.currentTimeMillis())
                        .put("updateTime", System.currentTimeMillis())
                        .put("deleted", false);
                    request.response().setStatusCode(200).end(response.encode());
                } else {
                    // Later requests: config deleted
                    JsonObject response = new JsonObject().put("error", "Config not found");
                    request.response().setStatusCode(404).end(response.encode());
                }
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigEntry> oldEntryRef = new AtomicReference<>();

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                if (oldEntry != null && newEntry == null) {
                    oldEntryRef.set(oldEntry);
                    latch.countDown();
                }
            }
        };

        appClient.startPolling("test-namespace", "test-key", 500, listener);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(oldEntryRef.get());
        assertEquals("test-namespace", oldEntryRef.get().getNamespace());
        assertEquals("test-key", oldEntryRef.get().getKey());

        appClient.stopPolling("test-namespace", "test-key");
    }

    @Test
    public void testStartPollingWithDefaultInterval() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                latch.countDown();
            }
        };

        // Use default interval (from AppClientOptions)
        appClient.startPolling("test-namespace", "test-key", listener);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        appClient.stopPolling("test-namespace", "test-key");
    }

    @Test
    public void testStopPolling() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final AtomicInteger changeCount = new AtomicInteger(0);

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                changeCount.incrementAndGet();
            }
        };

        appClient.startPolling("test-namespace", "test-key", 500, listener);
        
        // Wait for at least one poll
        Thread.sleep(1000);
        
        // Stop polling
        appClient.stopPolling("test-namespace", "test-key");
        
        int countAfterStop = changeCount.get();
        
        // Wait to ensure no more polls happen
        Thread.sleep(1500);
        
        // Count should not increase after stopping
        assertEquals(countAfterStop, changeCount.get());
    }

    @Test
    public void testStopAllPolling() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final AtomicInteger changeCount1 = new AtomicInteger(0);
        final AtomicInteger changeCount2 = new AtomicInteger(0);

        TestConfigChangeListener listener1 = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                changeCount1.incrementAndGet();
            }
        };

        TestConfigChangeListener listener2 = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                changeCount2.incrementAndGet();
            }
        };

        appClient.startPolling("test-namespace", "key1", 500, listener1);
        appClient.startPolling("test-namespace", "key2", 500, listener2);
        
        // Wait for at least one poll
        Thread.sleep(1000);
        
        // Stop all polling
        appClient.stopAllPolling();
        
        int count1AfterStop = changeCount1.get();
        int count2AfterStop = changeCount2.get();
        
        // Wait to ensure no more polls happen
        Thread.sleep(1500);
        
        // Counts should not increase after stopping
        assertEquals(count1AfterStop, changeCount1.get());
        assertEquals(count2AfterStop, changeCount2.get());
    }

    @Test
    public void testPollingErrorHandling() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger(0);
        
        startMockServer(request -> {
            int count = requestCount.incrementAndGet();
            
            if (count <= 2) {
                // First few requests: success
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                // Later requests: error
                JsonObject response = new JsonObject().put("error", "Server error");
                request.response().setStatusCode(500).end(response.encode());
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorMsgRef = new AtomicReference<>();

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onError(String namespace, String key, String errorMsg) {
                errorMsgRef.set(errorMsg);
                latch.countDown();
            }
        };

        appClient.startPolling("test-namespace", "test-key", 500, listener);

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(errorMsgRef.get());

        appClient.stopPolling("test-namespace", "test-key");
    }

    @Test
    public void testReplacePollingTask() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final AtomicInteger listener1Count = new AtomicInteger(0);
        final AtomicInteger listener2Count = new AtomicInteger(0);

        TestConfigChangeListener listener1 = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                listener1Count.incrementAndGet();
            }
        };

        TestConfigChangeListener listener2 = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                listener2Count.incrementAndGet();
            }
        };

        // Start first polling
        appClient.startPolling("test-namespace", "test-key", 500, listener1);
        Thread.sleep(1000);
        
        int listener1CountBefore = listener1Count.get();
        assertTrue(listener1CountBefore > 0);
        assertEquals(0, listener2Count.get());
        
        // Replace with second polling (should stop first one)
        appClient.startPolling("test-namespace", "test-key", 500, listener2);
        Thread.sleep(1000);
        
        // First listener should not receive more updates
        assertEquals(listener1CountBefore, listener1Count.get());
        // Second listener should receive updates
        assertTrue(listener2Count.get() > 0);

        appClient.stopPolling("test-namespace", "test-key");
    }

    // ========== Shutdown Tests ==========

    @Test
    public void testShutdownStopsPolling() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "value1"))
                    .put("version", 1L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final AtomicInteger changeCount = new AtomicInteger(0);

        TestConfigChangeListener listener = new TestConfigChangeListener() {
            @Override
            public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
                changeCount.incrementAndGet();
            }
        };

        appClient.startPolling("test-namespace", "test-key", 500, listener);
        
        // Wait for at least one poll
        Thread.sleep(1000);
        
        // Shutdown client
        appClient.shutdown();
        
        int countAfterShutdown = changeCount.get();
        
        // Wait to ensure no more polls happen
        Thread.sleep(1500);
        
        // Count should not increase after shutdown
        assertEquals(countAfterShutdown, changeCount.get());
    }

    @Test
    public void testGetConfigAfterShutdown() {
        appClient.shutdown();

        ConfigResponse response = appClient.getConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMsg().contains("shutdown"));
    }

    @Test
    public void testStartPollingAfterShutdown() {
        appClient.shutdown();

        // Should not throw exception, just log warning
        appClient.startPolling("test-namespace", "test-key", 1000, new TestConfigChangeListener());
    }

    // ========== Helper Methods ==========

    private void startMockServer(io.vertx.core.Handler<io.vertx.core.http.HttpServerRequest> requestHandler)
                                                                                                              throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        
        HttpServerOptions options = new HttpServerOptions().setPort(testPort).setHost(TEST_HOST);
        mockServer = vertx.createHttpServer(options);
        mockServer.requestHandler(requestHandler);
        mockServer.listen(result -> {
            if (result.succeeded()) {
                latch.countDown();
            } else {
                errorRef.set(result.cause());
                latch.countDown();
            }
        });
        
        assertTrue("Mock server failed to start within timeout. Error: " + 
                   (errorRef.get() != null ? errorRef.get().getMessage() : "timeout"), 
                   latch.await(10, TimeUnit.SECONDS));
        
        if (errorRef.get() != null) {
            throw new Exception("Failed to start mock server", errorRef.get());
        }
        
        // Give server time to fully start
        Thread.sleep(100);
    }

    /**
     * Test implementation of ConfigChangeListener
     */
    private static class TestConfigChangeListener implements ConfigChangeListener {
        @Override
        public void onConfigChanged(String namespace, String key, ConfigEntry oldEntry, ConfigEntry newEntry) {
            // Default implementation does nothing
        }

        @Override
        public void onError(String namespace, String key, String errorMsg) {
            // Default implementation does nothing
        }
    }
}
