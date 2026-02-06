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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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
 * Application client for configuration center read operations.
 * Supports consistent reads using ReadIndex and configuration polling with change listeners.
 * Implements error handling and automatic retry mechanism.
 *
 * @author lei
 */
public class AppClient {

    private static final Logger                 LOG = LoggerFactory.getLogger(AppClient.class);

    private final String                        serverUrl;
    private final String                        host;
    private final int                           port;
    private final io.vertx.core.http.HttpClient httpClient;
    private final ObjectMapper                  objectMapper;
    private final int                           timeoutMs;
    private final long                          pollingIntervalMs;
    private final Vertx                         vertx;
    private final ScheduledExecutorService      scheduler;
    private final Map<String, PollingTask>      pollingTasks;
    private volatile boolean                    shutdown;

    public AppClient(AppClientOptions options) {
        Objects.requireNonNull(options, "options cannot be null");
        options.validate();

        this.serverUrl = options.getServerUrl();
        this.timeoutMs = options.getTimeoutMs();
        this.pollingIntervalMs = options.getPollingIntervalMs();
        this.objectMapper = new ObjectMapper();
        this.vertx = Vertx.vertx();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.pollingTasks = new ConcurrentHashMap<>();
        this.shutdown = false;

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

        LOG.info("AppClient initialized with serverUrl={}, host={}, port={}, timeoutMs={}, pollingIntervalMs={}",
            serverUrl, host, port, timeoutMs, pollingIntervalMs);
    }

    /**
     * Get a configuration entry with consistent read (using ReadIndex).
     * This ensures the read reflects all committed writes.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @return the configuration response
     */
    public ConfigResponse getConfig(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (shutdown) {
            return ConfigResponse.error("AppClient is shutdown");
        }

        try {
            String path = String.format("/config/%s/%s?readIndex=true", namespace, key);
            return sendHttpRequest(HttpMethod.GET, path, null);
        } catch (Exception e) {
            LOG.error("Failed to get config: namespace={}, key={}", namespace, key, e);
            return ConfigResponse.error("Failed to get config: " + e.getMessage(), e);
        }
    }

    /**
     * Get a configuration entry by specific version.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param version the version number
     * @return the configuration response
     */
    public ConfigResponse getConfigByVersion(String namespace, String key, long version) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }

        if (shutdown) {
            return ConfigResponse.error("AppClient is shutdown");
        }

        try {
            String path = String.format("/config/%s/%s/version/%d", namespace, key, version);
            return sendHttpRequest(HttpMethod.GET, path, null);
        } catch (Exception e) {
            LOG.error("Failed to get config by version: namespace={}, key={}, version={}", namespace, key, version, e);
            return ConfigResponse.error("Failed to get config by version: " + e.getMessage(), e);
        }
    }

    /**
     * Get configuration history for a specific key.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @return the configuration response with history entries
     */
    public ConfigResponse getConfigHistory(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (shutdown) {
            return ConfigResponse.error("AppClient is shutdown");
        }

        try {
            String path = String.format("/config/%s/%s/history", namespace, key);
            return sendHttpRequest(HttpMethod.GET, path, null);
        } catch (Exception e) {
            LOG.error("Failed to get config history: namespace={}, key={}", namespace, key, e);
            return ConfigResponse.error("Failed to get config history: " + e.getMessage(), e);
        }
    }

    /**
     * Start polling a configuration entry for changes.
     * The listener will be notified when the configuration value changes.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param intervalMs the polling interval in milliseconds (overrides default)
     * @param listener the change listener
     */
    public void startPolling(String namespace, String key, long intervalMs, ConfigChangeListener listener) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        if (intervalMs <= 0) {
            throw new IllegalArgumentException("intervalMs must be positive");
        }

        if (shutdown) {
            LOG.warn("Cannot start polling, AppClient is shutdown");
            return;
        }

        String taskKey = namespace + ":" + key;

        // Stop existing polling task if any
        stopPolling(namespace, key);

        PollingTask task = new PollingTask(namespace, key, intervalMs, listener);
        pollingTasks.put(taskKey, task);
        task.start();

        LOG.info("Started polling for config: namespace={}, key={}, intervalMs={}", namespace, key, intervalMs);
    }

    /**
     * Start polling a configuration entry for changes using default interval.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param listener the change listener
     */
    public void startPolling(String namespace, String key, ConfigChangeListener listener) {
        startPolling(namespace, key, pollingIntervalMs, listener);
    }

    /**
     * Stop polling a configuration entry.
     *
     * @param namespace the namespace
     * @param key the configuration key
     */
    public void stopPolling(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        String taskKey = namespace + ":" + key;
        PollingTask task = pollingTasks.remove(taskKey);

        if (task != null) {
            task.stop();
            LOG.info("Stopped polling for config: namespace={}, key={}", namespace, key);
        }
    }

    /**
     * Stop all polling tasks.
     */
    public void stopAllPolling() {
        LOG.info("Stopping all polling tasks, count={}", pollingTasks.size());

        for (PollingTask task : pollingTasks.values()) {
            task.stop();
        }

        pollingTasks.clear();
    }

    /**
     * Shutdown the client and release resources.
     */
    public void shutdown() {
        if (shutdown) {
            return;
        }

        shutdown = true;

        LOG.info("Shutting down AppClient");

        // Stop all polling tasks
        stopAllPolling();

        // Shutdown scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Close HTTP client
        if (httpClient != null) {
            httpClient.close();
        }

        // Close Vertx
        if (vertx != null) {
            vertx.close();
        }

        LOG.info("AppClient shutdown complete");
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
            // Check if it's a list response (history or list)
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

    /**
     * Internal polling task for monitoring configuration changes
     */
    private class PollingTask {
        private final String                namespace;
        private final String                key;
        private final long                  intervalMs;
        private final ConfigChangeListener  listener;
        private volatile ConfigEntry        lastEntry;
        private volatile ScheduledFuture<?> scheduledFuture;

        public PollingTask(String namespace, String key, long intervalMs, ConfigChangeListener listener) {
            this.namespace = namespace;
            this.key = key;
            this.intervalMs = intervalMs;
            this.listener = listener;
            this.lastEntry = null;
        }

        public void start() {
            // Initial fetch
            fetchAndCompare();

            // Schedule periodic polling
            scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
                try {
                    fetchAndCompare();
                } catch (Exception e) {
                    LOG.error("Error during polling: namespace={}, key={}", namespace, key, e);
                    try {
                        listener.onError(namespace, key, e.getMessage());
                    } catch (Exception listenerError) {
                        LOG.error("Error in listener.onError: namespace={}, key={}", namespace, key, listenerError);
                    }
                }
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }

        public void stop() {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }

        private void fetchAndCompare() {
            try {
                ConfigResponse response = getConfig(namespace, key);

                if (!response.isSuccess()) {
                    // Config might not exist or error occurred
                    if (lastEntry != null) {
                        // Config was deleted
                        ConfigEntry oldEntry = lastEntry;
                        lastEntry = null;
                        listener.onConfigChanged(namespace, key, oldEntry, null);
                    }
                    return;
                }

                ConfigEntry newEntry = response.getConfigEntry();

                if (newEntry == null) {
                    // No config entry in response
                    if (lastEntry != null) {
                        // Config was deleted
                        ConfigEntry oldEntry = lastEntry;
                        lastEntry = null;
                        listener.onConfigChanged(namespace, key, oldEntry, null);
                    }
                    return;
                }

                // Check if config changed
                if (lastEntry == null) {
                    // New config created
                    lastEntry = newEntry;
                    listener.onConfigChanged(namespace, key, null, newEntry);
                } else if (!configEntriesEqual(lastEntry, newEntry)) {
                    // Config updated
                    ConfigEntry oldEntry = lastEntry;
                    lastEntry = newEntry;
                    listener.onConfigChanged(namespace, key, oldEntry, newEntry);
                }
                // else: no change, do nothing

            } catch (Exception e) {
                LOG.error("Failed to fetch config during polling: namespace={}, key={}", namespace, key, e);
                listener.onError(namespace, key, e.getMessage());
            }
        }

        /**
         * Compare two config entries for equality (ignoring updateTime)
         */
        private boolean configEntriesEqual(ConfigEntry entry1, ConfigEntry entry2) {
            if (entry1 == entry2) {
                return true;
            }
            if (entry1 == null || entry2 == null) {
                return false;
            }

            // Compare version (most important indicator of change)
            if (entry1.getVersion() != entry2.getVersion()) {
                return false;
            }

            // Compare properties
            if (!Objects.equals(entry1.getProperties(), entry2.getProperties())) {
                return false;
            }

            // Compare deleted flag
            if (entry1.isDeleted() != entry2.isDeleted()) {
                return false;
            }

            return true;
        }
    }
}
