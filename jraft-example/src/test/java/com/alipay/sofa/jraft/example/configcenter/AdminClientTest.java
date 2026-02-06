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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * Unit tests for AdminClient.
 * Tests HTTP request sending, error handling, and retry mechanism.
 * 
 * Requirements: 5.2, 5.3, 5.4
 *
 * @author lei
 */
public class AdminClientTest {

    private static final int    TEST_PORT       = 9999;
    private static final String TEST_HOST       = "localhost";
    private static final String TEST_SERVER_URL = "http://" + TEST_HOST + ":" + TEST_PORT;

    private AdminClient         adminClient;
    private Vertx               vertx;
    private HttpServer          mockServer;

    @Before
    public void setUp() throws Exception {
        vertx = Vertx.vertx();

        // Create admin client with test configuration
        AdminClientOptions options = new AdminClientOptions(TEST_SERVER_URL, 5000, 3);
        adminClient = new AdminClient(options);
    }

    @After
    public void tearDown() throws Exception {
        if (adminClient != null) {
            adminClient.shutdown();
        }
        if (mockServer != null) {
            CountDownLatch latch = new CountDownLatch(1);
            mockServer.close(result -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close(result -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
        }
    }

    // ========== Constructor and Initialization Tests ==========

    @Test(expected = NullPointerException.class)
    public void testConstructorWithNullOptions() {
        new AdminClient(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidUrl() {
        AdminClientOptions options = new AdminClientOptions("invalid-url", 5000, 3);
        new AdminClient(options);
    }

    @Test
    public void testConstructorWithValidOptions() {
        AdminClientOptions options = new AdminClientOptions(TEST_SERVER_URL, 5000, 3);
        AdminClient client = new AdminClient(options);
        assertNotNull(client);
        client.shutdown();
    }

    // ========== Null Parameter Tests ==========

    @Test(expected = NullPointerException.class)
    public void testPutConfigWithNullNamespace() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        adminClient.putConfig(null, "test-key", properties);
    }

    @Test(expected = NullPointerException.class)
    public void testPutConfigWithNullKey() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        adminClient.putConfig("test-namespace", null, properties);
    }

    @Test(expected = NullPointerException.class)
    public void testPutConfigWithNullProperties() {
        adminClient.putConfig("test-namespace", "test-key", (Map<String, String>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPutConfigWithEmptyProperties() {
        Map<String, String> properties = new HashMap<>();
        adminClient.putConfig("test-namespace", "test-key", properties);
    }

    @Test(expected = NullPointerException.class)
    public void testPutConfigSinglePropertyWithNullPropertyKey() {
        adminClient.putConfig("test-namespace", "test-key", null, "value1");
    }

    @Test(expected = NullPointerException.class)
    public void testPutConfigSinglePropertyWithNullPropertyValue() {
        adminClient.putConfig("test-namespace", "test-key", "key1", null);
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteConfigWithNullNamespace() {
        adminClient.deleteConfig(null, "test-key");
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteConfigWithNullKey() {
        adminClient.deleteConfig("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testListConfigsWithNullNamespace() {
        adminClient.listConfigs(null);
    }

    @Test(expected = NullPointerException.class)
    public void testRollbackConfigWithNullNamespace() {
        adminClient.rollbackConfig(null, "test-key", 1L);
    }

    @Test(expected = NullPointerException.class)
    public void testRollbackConfigWithNullKey() {
        adminClient.rollbackConfig("test-namespace", null, 1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRollbackConfigWithZeroVersion() {
        adminClient.rollbackConfig("test-namespace", "test-key", 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRollbackConfigWithNegativeVersion() {
        adminClient.rollbackConfig("test-namespace", "test-key", -1L);
    }

    // ========== Async Null Parameter Tests ==========

    @Test(expected = NullPointerException.class)
    public void testPutConfigAsyncWithNullCallback() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        adminClient.putConfigAsync("test-namespace", "test-key", properties, null);
    }

    @Test
    public void testPutConfigAsyncWithEmptyProperties() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>();

        Map<String, String> properties = new HashMap<>();
        adminClient.putConfigAsync("test-namespace", "test-key", properties, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                errorRef.set(errorMsg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals("properties cannot be empty", errorRef.get());
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteConfigAsyncWithNullCallback() {
        adminClient.deleteConfigAsync("test-namespace", "test-key", null);
    }

    @Test(expected = NullPointerException.class)
    public void testListConfigsAsyncWithNullCallback() {
        adminClient.listConfigsAsync("test-namespace", null);
    }

    @Test(expected = NullPointerException.class)
    public void testRollbackConfigAsyncWithNullCallback() {
        adminClient.rollbackConfigAsync("test-namespace", "test-key", 1L, null);
    }

    @Test
    public void testRollbackConfigAsyncWithInvalidVersion() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>();

        adminClient.rollbackConfigAsync("test-namespace", "test-key", 0L, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                errorRef.set(errorMsg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals("version must be positive", errorRef.get());
    }

    // ========== HTTP Request Tests ==========

    @Test
    public void testPutConfigSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.PUT 
                && request.path().equals("/config/test-namespace/test-key")) {
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

        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        ConfigResponse response = adminClient.putConfig("test-namespace", "test-key", properties);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("test-namespace", response.getConfigEntry().getNamespace());
        assertEquals("test-key", response.getConfigEntry().getKey());
    }

    @Test
    public void testPutConfigSinglePropertySuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.PUT 
                && request.path().equals("/config/test-namespace/test-key")) {
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

        ConfigResponse response = adminClient.putConfig("test-namespace", "test-key", "key1", "value1");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("test-namespace", response.getConfigEntry().getNamespace());
        assertEquals("test-key", response.getConfigEntry().getKey());
    }

    @Test
    public void testDeleteConfigSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.DELETE 
                && request.path().equals("/config/test-namespace/test-key")) {
                JsonObject response = new JsonObject().put("success", true);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        ConfigResponse response = adminClient.deleteConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertTrue(response.isSuccess());
    }

    @Test
    public void testListConfigsSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET 
                && request.path().equals("/config/test-namespace")) {
                JsonArray configs = new JsonArray()
                    .add(new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "key1")
                        .put("properties", new JsonObject().put("prop1", "value1"))
                        .put("version", 1L))
                    .add(new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "key2")
                        .put("properties", new JsonObject().put("prop2", "value2"))
                        .put("version", 1L));
                JsonObject response = new JsonObject().put("configs", configs);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        ConfigResponse response = adminClient.listConfigs("test-namespace");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertTrue(response.hasConfigEntries());
        assertEquals(2, response.getConfigEntries().size());
    }

    @Test
    public void testRollbackConfigSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.POST 
                && request.path().equals("/config/test-namespace/test-key/rollback")) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "old-value"))
                    .put("version", 3L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            } else {
                request.response().setStatusCode(404).end();
            }
        });

        ConfigResponse response = adminClient.rollbackConfig("test-namespace", "test-key", 1L);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("test-namespace", response.getConfigEntry().getNamespace());
        assertEquals("test-key", response.getConfigEntry().getKey());
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testPutConfigServerError() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("error", "Internal server error");
            request.response().setStatusCode(500).end(response.encode());
        });

        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        ConfigResponse response = adminClient.putConfig("test-namespace", "test-key", properties);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
        assertTrue(response.getErrorMsg().contains("Internal server error"));
    }

    @Test
    public void testDeleteConfigNotFound() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("error", "Config not found");
            request.response().setStatusCode(404).end(response.encode());
        });

        ConfigResponse response = adminClient.deleteConfig("test-namespace", "test-key");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
        assertTrue(response.getErrorMsg().contains("Config not found"));
    }

    @Test
    public void testListConfigsEmptyResponse() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("configs", new JsonArray());
            request.response().setStatusCode(200).end(response.encode());
        });

        ConfigResponse response = adminClient.listConfigs("test-namespace");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertFalse(response.hasConfigEntries());
        assertEquals(0, response.getConfigEntries().size());
    }

    @Test
    public void testServerConnectionFailure() {
        // No mock server started - connection should fail
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        ConfigResponse response = adminClient.putConfig("test-namespace", "test-key", properties);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
    }

    // ========== Async Operation Tests ==========

    @Test
    public void testPutConfigAsyncSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.PUT) {
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
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();

        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        adminClient.putConfigAsync("test-namespace", "test-key", properties, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(responseRef.get());
        assertTrue(responseRef.get().isSuccess());
    }

    @Test
    public void testPutConfigAsyncSinglePropertySuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.PUT) {
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
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();

        adminClient.putConfigAsync("test-namespace", "test-key", "key1", "value1", new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(responseRef.get());
        assertTrue(responseRef.get().isSuccess());
    }

    @Test
    public void testDeleteConfigAsyncSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.DELETE) {
                JsonObject response = new JsonObject().put("success", true);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();

        adminClient.deleteConfigAsync("test-namespace", "test-key", new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(responseRef.get());
        assertTrue(responseRef.get().isSuccess());
    }

    @Test
    public void testListConfigsAsyncSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.GET) {
                JsonArray configs = new JsonArray()
                    .add(new JsonObject()
                        .put("namespace", "test-namespace")
                        .put("key", "key1")
                        .put("properties", new JsonObject().put("prop1", "value1"))
                        .put("version", 1L));
                JsonObject response = new JsonObject().put("configs", configs);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();

        adminClient.listConfigsAsync("test-namespace", new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(responseRef.get());
        assertTrue(responseRef.get().isSuccess());
        assertTrue(responseRef.get().hasConfigEntries());
    }

    @Test
    public void testRollbackConfigAsyncSuccess() throws Exception {
        startMockServer(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.POST) {
                JsonObject response = new JsonObject()
                    .put("namespace", "test-namespace")
                    .put("key", "test-key")
                    .put("properties", new JsonObject().put("key1", "old-value"))
                    .put("version", 3L)
                    .put("createTime", System.currentTimeMillis())
                    .put("updateTime", System.currentTimeMillis())
                    .put("deleted", false);
                request.response().setStatusCode(200).end(response.encode());
            }
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();

        adminClient.rollbackConfigAsync("test-namespace", "test-key", 1L, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(responseRef.get());
        assertTrue(responseRef.get().isSuccess());
    }

    @Test
    public void testPutConfigAsyncError() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("error", "Server error");
            request.response().setStatusCode(500).end(response.encode());
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>();

        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        adminClient.putConfigAsync("test-namespace", "test-key", properties, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                errorRef.set(errorMsg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().contains("Server error"));
    }

    @Test
    public void testDeleteConfigAsyncError() throws Exception {
        startMockServer(request -> {
            JsonObject response = new JsonObject().put("error", "Config not found");
            request.response().setStatusCode(404).end(response.encode());
        });

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> errorRef = new AtomicReference<>();

        adminClient.deleteConfigAsync("test-namespace", "test-key", new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                errorRef.set(errorMsg);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get().contains("Config not found"));
    }

    // ========== Helper Methods ==========

    private void startMockServer(io.vertx.core.Handler<io.vertx.core.http.HttpServerRequest> requestHandler)
                                                                                                              throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        HttpServerOptions options = new HttpServerOptions().setPort(TEST_PORT).setHost(TEST_HOST);
        mockServer = vertx.createHttpServer(options);
        mockServer.requestHandler(requestHandler);
        mockServer.listen(result -> {
            if (result.succeeded()) {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        // Give server time to fully start
        Thread.sleep(100);
    }
}
