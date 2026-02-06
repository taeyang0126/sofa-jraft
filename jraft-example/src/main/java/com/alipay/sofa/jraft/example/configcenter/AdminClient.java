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

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;

/**
 * Admin client for configuration center management operations.
 * Supports synchronous and asynchronous interfaces for config CRUD operations.
 * Implements error handling and retry mechanism.
 *
 * @author lei
 */
public class AdminClient {

    private static final Logger                 LOG = LoggerFactory.getLogger(AdminClient.class);

    private final String                        serverUrl;
    private final String                        host;
    private final int                           port;
    private final io.vertx.core.http.HttpClient httpClient;
    private final ObjectMapper                  objectMapper;
    private final int                           timeoutMs;
    private final int                           maxRetries;
    private final Vertx                         vertx;

    public AdminClient(AdminClientOptions options) {
        Objects.requireNonNull(options, "options cannot be null");
        options.validate();

        this.serverUrl = options.getServerUrl();
        this.timeoutMs = options.getTimeoutMs();
        this.maxRetries = options.getMaxRetries();
        this.objectMapper = new ObjectMapper();
        this.vertx = Vertx.vertx();

        // Parse server URL to extract host and port
        try {
            URL url = new URL(serverUrl);
            this.host = url.getHost();
            this.port = url.getPort() > 0 ? url.getPort() : (url.getProtocol().equals("https") ? 443 : 80);
        } catch (Exception e) {
            LOG.error("Failed to parse server URL: {}", serverUrl, e);
            throw new IllegalArgumentException("Invalid server URL: " + serverUrl, e);
        }

        HttpClientOptions clientOptions = new HttpClientOptions().setConnectTimeout(timeoutMs)
            .setIdleTimeout(timeoutMs).setMaxPoolSize(10);

        this.httpClient = vertx.createHttpClient(clientOptions);

        LOG.info("AdminClient initialized with serverUrl={}, host={}, port={}, timeoutMs={}, maxRetries={}", serverUrl,
            host, port, timeoutMs, maxRetries);
    }

    /**
     * Put a configuration entry (synchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param properties the configuration properties
     * @return the configuration response
     */
    public ConfigResponse putConfig(String namespace, String key, Map<String, String> properties) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(properties, "properties cannot be null");

        if (properties.isEmpty()) {
            throw new IllegalArgumentException("properties cannot be empty");
        }

        try {
            return putConfigWithRetry(namespace, key, properties, 0);
        } catch (Exception e) {
            LOG.error("Failed to put config: namespace={}, key={}", namespace, key, e);
            return ConfigResponse.error("Failed to put config: " + e.getMessage(), e);
        }
    }

    /**
     * Put a configuration entry with single property (synchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param propertyKey the property key
     * @param propertyValue the property value
     * @return the configuration response
     */
    public ConfigResponse putConfig(String namespace, String key, String propertyKey, String propertyValue) {
        Objects.requireNonNull(propertyKey, "propertyKey cannot be null");
        Objects.requireNonNull(propertyValue, "propertyValue cannot be null");

        Map<String, String> properties = new HashMap<>();
        properties.put(propertyKey, propertyValue);
        return putConfig(namespace, key, properties);
    }

    /**
     * Delete a configuration entry (synchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @return the configuration response
     */
    public ConfigResponse deleteConfig(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        try {
            return deleteConfigWithRetry(namespace, key, 0);
        } catch (Exception e) {
            LOG.error("Failed to delete config: namespace={}, key={}", namespace, key, e);
            return ConfigResponse.error("Failed to delete config: " + e.getMessage(), e);
        }
    }

    /**
     * List all configurations in a namespace (synchronous)
     *
     * @param namespace the namespace
     * @return the configuration response with list of entries
     */
    public ConfigResponse listConfigs(String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");

        try {
            return listConfigsWithRetry(namespace, 0);
        } catch (Exception e) {
            LOG.error("Failed to list configs: namespace={}", namespace, e);
            return ConfigResponse.error("Failed to list configs: " + e.getMessage(), e);
        }
    }

    /**
     * Rollback a configuration to a specific version (synchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param version the target version
     * @return the configuration response
     */
    public ConfigResponse rollbackConfig(String namespace, String key, long version) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }

        try {
            return rollbackConfigWithRetry(namespace, key, version, 0);
        } catch (Exception e) {
            LOG.error("Failed to rollback config: namespace={}, key={}, version={}", namespace, key, version, e);
            return ConfigResponse.error("Failed to rollback config: " + e.getMessage(), e);
        }
    }

    /**
     * Put a configuration entry (asynchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param properties the configuration properties
     * @param callback the callback for async result
     */
    public void putConfigAsync(String namespace, String key, Map<String, String> properties, ConfigCallback callback) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(properties, "properties cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        if (properties.isEmpty()) {
            callback.onError("properties cannot be empty");
            return;
        }

        vertx.executeBlocking(future -> {
            try {
                ConfigResponse response = putConfigWithRetry(namespace, key, properties, 0);
                future.complete(response);
            } catch (Exception e) {
                LOG.error("Failed to put config async: namespace={}, key={}", namespace, key, e);
                future.fail(e);
            }
        }, false, result -> {
            if (result.succeeded()) {
                ConfigResponse response = (ConfigResponse) result.result();
                if (response.isSuccess()) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(response.getErrorMsg());
                }
            } else {
                callback.onError("Failed to put config: " + result.cause().getMessage());
            }
        });
    }

    /**
     * Put a configuration entry with single property (asynchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param propertyKey the property key
     * @param propertyValue the property value
     * @param callback the callback for async result
     */
    public void putConfigAsync(String namespace, String key, String propertyKey, String propertyValue,
                               ConfigCallback callback) {
        Objects.requireNonNull(propertyKey, "propertyKey cannot be null");
        Objects.requireNonNull(propertyValue, "propertyValue cannot be null");

        Map<String, String> properties = new HashMap<>();
        properties.put(propertyKey, propertyValue);
        putConfigAsync(namespace, key, properties, callback);
    }

    /**
     * Delete a configuration entry (asynchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param callback the callback for async result
     */
    public void deleteConfigAsync(String namespace, String key, ConfigCallback callback) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        vertx.executeBlocking(future -> {
            try {
                ConfigResponse response = deleteConfigWithRetry(namespace, key, 0);
                future.complete(response);
            } catch (Exception e) {
                LOG.error("Failed to delete config async: namespace={}, key={}", namespace, key, e);
                future.fail(e);
            }
        }, false, result -> {
            if (result.succeeded()) {
                ConfigResponse response = (ConfigResponse) result.result();
                if (response.isSuccess()) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(response.getErrorMsg());
                }
            } else {
                callback.onError("Failed to delete config: " + result.cause().getMessage());
            }
        });
    }

    /**
     * List all configurations in a namespace (asynchronous)
     *
     * @param namespace the namespace
     * @param callback the callback for async result
     */
    public void listConfigsAsync(String namespace, ConfigCallback callback) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        vertx.executeBlocking(future -> {
            try {
                ConfigResponse response = listConfigsWithRetry(namespace, 0);
                future.complete(response);
            } catch (Exception e) {
                LOG.error("Failed to list configs async: namespace={}", namespace, e);
                future.fail(e);
            }
        }, false, result -> {
            if (result.succeeded()) {
                ConfigResponse response = (ConfigResponse) result.result();
                if (response.isSuccess()) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(response.getErrorMsg());
                }
            } else {
                callback.onError("Failed to list configs: " + result.cause().getMessage());
            }
        });
    }

    /**
     * Rollback a configuration to a specific version (asynchronous)
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param version the target version
     * @param callback the callback for async result
     */
    public void rollbackConfigAsync(String namespace, String key, long version, ConfigCallback callback) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");

        if (version <= 0) {
            callback.onError("version must be positive");
            return;
        }

        vertx.executeBlocking(future -> {
            try {
                ConfigResponse response = rollbackConfigWithRetry(namespace, key, version, 0);
                future.complete(response);
            } catch (Exception e) {
                LOG.error("Failed to rollback config async: namespace={}, key={}, version={}", namespace, key,
                    version, e);
                future.fail(e);
            }
        }, false, result -> {
            if (result.succeeded()) {
                ConfigResponse response = (ConfigResponse) result.result();
                if (response.isSuccess()) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(response.getErrorMsg());
                }
            } else {
                callback.onError("Failed to rollback config: " + result.cause().getMessage());
            }
        });
    }

    /**
     * Shutdown the client and release resources
     */
    public void shutdown() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (vertx != null) {
            vertx.close();
            LOG.info("AdminClient shutdown");
        }
    }

    /**
     * Put config with retry mechanism
     */
    private ConfigResponse putConfigWithRetry(String namespace, String key, Map<String, String> properties,
                                              int retryCount) throws Exception {
        try {
            String path = String.format("/config/%s/%s", namespace, key);
            String jsonBody = objectMapper.writeValueAsString(properties);

            return sendHttpRequest(HttpMethod.PUT, path, jsonBody);

        } catch (Exception e) {
            if (retryCount < maxRetries) {
                LOG.warn("Put config failed, retrying... (attempt {}/{}): namespace={}, key={}", retryCount + 1,
                    maxRetries, namespace, key);
                Thread.sleep(100 * (retryCount + 1)); // Exponential backoff
                return putConfigWithRetry(namespace, key, properties, retryCount + 1);
            }
            throw e;
        }
    }

    /**
     * Delete config with retry mechanism
     */
    private ConfigResponse deleteConfigWithRetry(String namespace, String key, int retryCount) throws Exception {
        try {
            String path = String.format("/config/%s/%s", namespace, key);

            return sendHttpRequest(HttpMethod.DELETE, path, null);

        } catch (Exception e) {
            if (retryCount < maxRetries) {
                LOG.warn("Delete config failed, retrying... (attempt {}/{}): namespace={}, key={}", retryCount + 1,
                    maxRetries, namespace, key);
                Thread.sleep(100 * (retryCount + 1)); // Exponential backoff
                return deleteConfigWithRetry(namespace, key, retryCount + 1);
            }
            throw e;
        }
    }

    /**
     * List configs with retry mechanism
     */
    private ConfigResponse listConfigsWithRetry(String namespace, int retryCount) throws Exception {
        try {
            String path = String.format("/config/%s", namespace);

            return sendHttpRequest(HttpMethod.GET, path, null);

        } catch (Exception e) {
            if (retryCount < maxRetries) {
                LOG.warn("List configs failed, retrying... (attempt {}/{}): namespace={}", retryCount + 1, maxRetries,
                    namespace);
                Thread.sleep(100 * (retryCount + 1)); // Exponential backoff
                return listConfigsWithRetry(namespace, retryCount + 1);
            }
            throw e;
        }
    }

    /**
     * Rollback config with retry mechanism
     */
    private ConfigResponse rollbackConfigWithRetry(String namespace, String key, long version, int retryCount)
                                                                                                              throws Exception {
        try {
            String path = String.format("/config/%s/%s/rollback", namespace, key);
            String jsonBody = String.format("{\"version\":%d}", version);

            return sendHttpRequest(HttpMethod.POST, path, jsonBody);

        } catch (Exception e) {
            if (retryCount < maxRetries) {
                LOG.warn("Rollback config failed, retrying... (attempt {}/{}): namespace={}, key={}, version={}",
                    retryCount + 1, maxRetries, namespace, key, version);
                Thread.sleep(100 * (retryCount + 1)); // Exponential backoff
                return rollbackConfigWithRetry(namespace, key, version, retryCount + 1);
            }
            throw e;
        }
    }

    /**
     * Send HTTP request using Vert.x HTTP client
     */
    private ConfigResponse sendHttpRequest(HttpMethod method, String path, String body) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        io.vertx.core.http.HttpClientRequest request = httpClient.request(method, port, host, path);
        request.setTimeout(timeoutMs);
        request.putHeader("Content-Type", "application/json");

        request.handler(response -> {
            response.bodyHandler(buffer -> {
                try {
                    ConfigResponse configResponse = parseResponse(response.statusCode(), buffer.toString());
                    responseRef.set(configResponse);
                } catch (Exception e) {
                    LOG.error("Failed to parse response", e);
                    errorRef.set(e);
                } finally {
                    latch.countDown();
                }
            });
        });

        request.exceptionHandler(throwable -> {
            LOG.error("HTTP request failed", throwable);
            errorRef.set(throwable);
            latch.countDown();
        });

        if (body != null) {
            request.end(Buffer.buffer(body));
        } else {
            request.end();
        }

        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("HTTP request timeout");
        }

        if (errorRef.get() != null) {
            throw new RuntimeException("HTTP request failed", errorRef.get());
        }

        return responseRef.get();
    }

    /**
     * Parse HTTP response to ConfigResponse
     */
    private ConfigResponse parseResponse(int statusCode, String body) throws Exception {
        if (body == null || body.isEmpty()) {
            return ConfigResponse.error("Empty response from server");
        }

        JsonNode jsonNode = objectMapper.readTree(body);

        // Check if response indicates success
        if (statusCode >= 200 && statusCode < 300) {
            // Check if it's a list response
            if (jsonNode.has("configs")) {
                JsonNode configsNode = jsonNode.get("configs");
                List<ConfigEntry> entries = new ArrayList<>();

                if (configsNode.isArray()) {
                    for (JsonNode configNode : configsNode) {
                        ConfigEntry entry = parseConfigEntry(configNode);
                        if (entry != null) {
                            entries.add(entry);
                        }
                    }
                }

                return ConfigResponse.success(entries);
            }

            // Check if it's a single config entry response
            if (jsonNode.has("namespace") && jsonNode.has("key")) {
                ConfigEntry entry = parseConfigEntry(jsonNode);
                if (entry != null) {
                    return ConfigResponse.success(entry);
                }
            }

            // Check if it's a simple success message
            if (jsonNode.has("success") && jsonNode.get("success").asBoolean()) {
                return ConfigResponse.success();
            }

            return ConfigResponse.success();
        } else {
            // Error response
            String errorMsg = "Unknown error";
            if (jsonNode.has("error")) {
                errorMsg = jsonNode.get("error").asText();
            } else if (jsonNode.has("message")) {
                errorMsg = jsonNode.get("message").asText();
            }

            return ConfigResponse.error(errorMsg);
        }
    }

    /**
     * Parse a JSON node to ConfigEntry
     */
    private ConfigEntry parseConfigEntry(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }

        String namespace = jsonNode.has("namespace") ? jsonNode.get("namespace").asText() : null;
        String key = jsonNode.has("key") ? jsonNode.get("key").asText() : null;

        if (namespace == null || key == null) {
            return null;
        }

        ConfigEntry entry = new ConfigEntry(namespace, key);

        // Parse properties
        if (jsonNode.has("properties")) {
            JsonNode propertiesNode = jsonNode.get("properties");
            Map<String, String> properties = new HashMap<>();
            propertiesNode.fields().forEachRemaining(field -> {
                properties.put(field.getKey(), field.getValue().asText());
            });
            entry.setProperties(properties);
        }

        // Parse metadata
        if (jsonNode.has("version")) {
            entry.setVersion(jsonNode.get("version").asLong());
        }
        if (jsonNode.has("createTime")) {
            entry.setCreateTime(jsonNode.get("createTime").asLong());
        }
        if (jsonNode.has("updateTime")) {
            entry.setUpdateTime(jsonNode.get("updateTime").asLong());
        }
        if (jsonNode.has("deleted")) {
            entry.setDeleted(jsonNode.get("deleted").asBoolean());
        }

        return entry;
    }
}
