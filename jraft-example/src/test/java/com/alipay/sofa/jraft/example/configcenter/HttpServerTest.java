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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.entity.NodeId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.Task;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Unit tests for HttpServer.
 * Tests REST API endpoints and error handling.
 *
 * @author lei
 */
public class HttpServerTest {

    private static final int    TEST_PORT = 8888;
    private static final String TEST_HOST = "localhost";

    private HttpServer          httpServer;
    private ConfigStateMachine  stateMachine;
    private ConfigStorage       storage;
    private Node                node;
    private Vertx               vertx;
    private HttpClient          httpClient;

    @Before
    public void setUp() throws Exception {
        // Create storage and state machine
        storage = new MemoryConfigStorage();
        stateMachine = new ConfigStateMachine(storage);

        // Mock Node
        node = mock(Node.class);
        PeerId peerId = new PeerId("localhost", TEST_PORT);
        NodeId nodeId = new NodeId("test_group", peerId);
        when(node.getNodeId()).thenReturn(nodeId);
        when(node.isLeader()).thenReturn(true);
        when(node.getLeaderId()).thenReturn(peerId);

        // Mock ReadIndex to always succeed
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ReadIndexClosure closure = (ReadIndexClosure) args[1];
            closure.run(Status.OK(), 1L, null);
            return null;
        }).when(node).readIndex(any(byte[].class), any(ReadIndexClosure.class));

        // Mock apply to execute task synchronously
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Task task = (Task) args[0];
            if (task.getDone() != null) {
                // Execute the task synchronously for testing
                ByteBuffer data = task.getData();
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                
                // Simulate state machine processing
                ConfigClosure closure = (ConfigClosure) task.getDone();
                ConfigRequest request = closure.getRequest();
                
                ConfigResponse response;
                try {
                    switch (request.getType()) {
                        case PUT_CONFIG:
                            ConfigEntry putEntry = new ConfigEntry(request.getNamespace(), request.getKey(),
                                request.getProperties());
                            storage.putConfig(request.getNamespace(), request.getKey(), putEntry);
                            response = ConfigResponse.success(putEntry);
                            break;
                        case DELETE_CONFIG:
                            boolean deleted = storage.deleteConfig(request.getNamespace(), request.getKey());
                            if (deleted) {
                                response = ConfigResponse.success();
                            } else {
                                response = ConfigResponse.error("Config not found");
                            }
                            break;
                        case ROLLBACK_CONFIG:
                            ConfigEntry rollbackEntry = storage.getConfigByVersion(request.getNamespace(),
                                request.getKey(), request.getVersion());
                            if (rollbackEntry != null) {
                                ConfigEntry newEntry = new ConfigEntry(request.getNamespace(), request.getKey(),
                                    rollbackEntry.getProperties());
                                storage.putConfig(request.getNamespace(), request.getKey(), newEntry);
                                response = ConfigResponse.success(newEntry);
                            } else {
                                response = ConfigResponse.error("Config version not found");
                            }
                            break;
                        default:
                            response = ConfigResponse.error("Unsupported operation");
                    }
                    
                    closure.setResponse(response);
                    closure.run(Status.OK());
                } catch (Exception e) {
                    closure.setResponse(ConfigResponse.error(e.getMessage()));
                    closure.run(new Status(-1, e.getMessage()));
                }
            }
            return null;
        }).when(node).apply(any(Task.class));

        // Create and start HTTP server
        httpServer = new HttpServer(stateMachine, node, TEST_PORT);
        httpServer.start();

        // Wait for server to start
        Thread.sleep(1000);

        // Create HTTP client
        vertx = Vertx.vertx();
        HttpClientOptions options = new HttpClientOptions().setDefaultHost(TEST_HOST).setDefaultPort(TEST_PORT);
        httpClient = vertx.createHttpClient(options);
    }

    @After
    public void tearDown() throws Exception {
        if (httpClient != null) {
            httpClient.close();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
        if (vertx != null) {
            vertx.close();
        }
        // Wait for cleanup
        Thread.sleep(500);
    }

    @Test
    public void testPutConfig() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        JsonObject requestBody = new JsonObject().put("url", "jdbc:mysql://localhost:3306/test").put("username",
            "root").put("password", "secret");

        HttpClientRequest request = httpClient.request(HttpMethod.PUT, "/config/default/db.config", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        request.putHeader("Content-Type", "application/json");
        request.end(requestBody.encode());

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals("default", response.getString("namespace"));
        assertEquals("db.config", response.getString("key"));
        assertEquals(1L, response.getLong("version").longValue());

        JsonObject properties = response.getJsonObject("properties");
        assertNotNull(properties);
        assertEquals("jdbc:mysql://localhost:3306/test", properties.getString("url"));
        assertEquals("root", properties.getString("username"));
        assertEquals("secret", properties.getString("password"));
    }

    @Test
    public void testPutConfigInvalidJson() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] statusCodeHolder = new int[1];
        final JsonObject[] responseHolder = new JsonObject[1];

        HttpClientRequest request = httpClient.request(HttpMethod.PUT, "/config/default/test.key", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                try {
                    responseHolder[0] = body.toJsonObject();
                } catch (Exception e) {
                    // Ignore JSON parse error
                }
                latch.countDown();
            });
        });

        request.putHeader("Content-Type", "application/json");
        request.end("invalid json");

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Invalid JSON should return 500 error from server
        assertEquals(500, statusCodeHolder[0]);
    }

    @Test
    public void testGetConfig() throws Exception {
        // First, put a config
        Map<String, String> properties = new HashMap<>();
        properties.put("timeout", "5000");
        properties.put("retries", "3");
        ConfigEntry entry = new ConfigEntry("default", "app.config", properties);
        storage.putConfig("default", "app.config", entry);

        // Now get the config
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default/app.config", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals("default", response.getString("namespace"));
        assertEquals("app.config", response.getString("key"));

        JsonObject props = response.getJsonObject("properties");
        assertNotNull(props);
        assertEquals("5000", props.getString("timeout"));
        assertEquals("3", props.getString("retries"));
    }

    @Test
    public void testGetConfigNotFound() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default/nonexistent.key", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(404, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertFalse(response.getBoolean("success"));
        assertTrue(response.getString("error").contains("Config not found"));
    }

    @Test
    public void testDeleteConfig() throws Exception {
        // First, put a config
        Map<String, String> properties = new HashMap<>();
        properties.put("value", "test");
        ConfigEntry entry = new ConfigEntry("default", "test.key", properties);
        storage.putConfig("default", "test.key", entry);

        // Now delete the config
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        HttpClientRequest request = httpClient.request(HttpMethod.DELETE, "/config/default/test.key", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        request.end();

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testDeleteConfigNotFound() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        HttpClientRequest request = httpClient.request(HttpMethod.DELETE, "/config/default/nonexistent.key",
            response -> {
                statusCodeHolder[0] = response.statusCode();
                response.bodyHandler(body -> {
                    responseHolder[0] = body.toJsonObject();
                    latch.countDown();
                });
            });

        request.end();

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Delete non-existent config should return success (idempotent operation)
        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        // The mock returns success for delete operations
        assertTrue(response.getBoolean("success"));
    }

    @Test
    public void testListConfigs() throws Exception {
        // Put some configs
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "test1");
        ConfigEntry entry1 = new ConfigEntry("default", "key1", properties1);
        storage.putConfig("default", "key1", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "test2");
        ConfigEntry entry2 = new ConfigEntry("default", "key2", properties2);
        storage.putConfig("default", "key2", entry2);

        // List configs
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals(2, response.getInteger("count").intValue());

        JsonArray configs = response.getJsonArray("configs");
        assertNotNull(configs);
        assertEquals(2, configs.size());
    }

    @Test
    public void testListConfigsEmptyNamespace() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/empty_namespace", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals(0, response.getInteger("count").intValue());

        JsonArray configs = response.getJsonArray("configs");
        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    public void testGetConfigHistory() throws Exception {
        // Put and update a config to create history
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "v1");
        ConfigEntry entry1 = new ConfigEntry("default", "test.key", properties1);
        storage.putConfig("default", "test.key", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "v2");
        ConfigEntry entry2 = new ConfigEntry("default", "test.key", properties2);
        storage.putConfig("default", "test.key", entry2);

        // Get history
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default/test.key/history", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals(2, response.getInteger("count").intValue());

        JsonArray configs = response.getJsonArray("configs");
        assertNotNull(configs);
        assertEquals(2, configs.size());
    }

    @Test
    public void testGetConfigByVersion() throws Exception {
        // Put and update a config to create versions
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "v1");
        ConfigEntry entry1 = new ConfigEntry("default", "test.key", properties1);
        storage.putConfig("default", "test.key", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "v2");
        ConfigEntry entry2 = new ConfigEntry("default", "test.key", properties2);
        storage.putConfig("default", "test.key", entry2);

        // Get version 1
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default/test.key/version/1", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals("default", response.getString("namespace"));
        assertEquals("test.key", response.getString("key"));
        assertEquals(1L, response.getLong("version").longValue());

        JsonObject props = response.getJsonObject("properties");
        assertNotNull(props);
        assertEquals("v1", props.getString("value"));
    }

    @Test
    public void testGetConfigByVersionNotFound() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default/test.key/version/999", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(404, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertFalse(response.getBoolean("success"));
        assertTrue(response.getString("error").contains("Config not found"));
    }

    @Test
    public void testGetConfigByVersionInvalidVersion() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        httpClient.getNow("/config/default/test.key/version/invalid", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(400, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertFalse(response.getBoolean("success"));
        assertTrue(response.getString("error").contains("Invalid version number"));
    }

    @Test
    public void testRollbackConfig() throws Exception {
        // Put and update a config
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "v1");
        ConfigEntry entry1 = new ConfigEntry("default", "test.key", properties1);
        storage.putConfig("default", "test.key", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "v2");
        ConfigEntry entry2 = new ConfigEntry("default", "test.key", properties2);
        storage.putConfig("default", "test.key", entry2);

        // Rollback to version 1
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        JsonObject requestBody = new JsonObject().put("version", 1);

        HttpClientRequest request = httpClient.request(HttpMethod.POST, "/config/default/test.key/rollback",
            response -> {
                statusCodeHolder[0] = response.statusCode();
                response.bodyHandler(body -> {
                    responseHolder[0] = body.toJsonObject();
                    latch.countDown();
                });
            });

        request.putHeader("Content-Type", "application/json");
        request.end(requestBody.encode());

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(200, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertEquals("default", response.getString("namespace"));
        assertEquals("test.key", response.getString("key"));
        assertEquals(3L, response.getLong("version").longValue()); // New version created

        JsonObject props = response.getJsonObject("properties");
        assertNotNull(props);
        assertEquals("v1", props.getString("value")); // Value from version 1
    }

    @Test
    public void testRollbackConfigMissingVersion() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        JsonObject requestBody = new JsonObject(); // Missing version

        HttpClientRequest request = httpClient.request(HttpMethod.POST, "/config/default/test.key/rollback",
            response -> {
                statusCodeHolder[0] = response.statusCode();
                response.bodyHandler(body -> {
                    responseHolder[0] = body.toJsonObject();
                    latch.countDown();
                });
            });

        request.putHeader("Content-Type", "application/json");
        request.end(requestBody.encode());

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(400, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertFalse(response.getBoolean("success"));
        assertTrue(response.getString("error").contains("Version is required"));
    }

    @Test
    public void testPutConfigWhenNotLeader() throws Exception {
        // Mock node as not leader
        when(node.isLeader()).thenReturn(false);
        PeerId otherLeader = new PeerId("otherhost", 8889);
        when(node.getLeaderId()).thenReturn(otherLeader);

        final CountDownLatch latch = new CountDownLatch(1);
        final JsonObject[] responseHolder = new JsonObject[1];
        final int[] statusCodeHolder = new int[1];

        JsonObject requestBody = new JsonObject().put("value", "test");

        HttpClientRequest request = httpClient.request(HttpMethod.PUT, "/config/default/test.key", response -> {
            statusCodeHolder[0] = response.statusCode();
            response.bodyHandler(body -> {
                responseHolder[0] = body.toJsonObject();
                latch.countDown();
            });
        });

        request.putHeader("Content-Type", "application/json");
        request.end(requestBody.encode());

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(500, statusCodeHolder[0]);
        JsonObject response = responseHolder[0];
        assertNotNull(response);
        assertFalse(response.getBoolean("success"));
        assertTrue(response.getString("error").contains("Not leader"));
    }
}
