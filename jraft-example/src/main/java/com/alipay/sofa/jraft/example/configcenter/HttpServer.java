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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.Task;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * HTTP server for configuration center using Vert.x.
 * Handles REST API requests and integrates with ConfigStateMachine and ReadIndex.
 *
 * @author lei
 */
public class HttpServer {

    private static final Logger           LOG             = LoggerFactory.getLogger(HttpServer.class);
    private static final int              DEFAULT_TIMEOUT = 5000;

    private final Vertx                   vertx;
    private io.vertx.core.http.HttpServer server;
    private final ConfigStateMachine      stateMachine;
    private final Node                    node;
    private final int                     port;

    public HttpServer(ConfigStateMachine stateMachine, Node node, int port) {
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine cannot be null");
        this.node = Objects.requireNonNull(node, "node cannot be null");
        this.port = port;
        this.vertx = Vertx.vertx();
    }

    /**
     * Start the HTTP server
     */
    public void start() {
        final Router router = Router.router(vertx);
        
        // Add body handler to parse request body
        router.route().handler(BodyHandler.create());
        
        // Write operations (need to go through Raft)
        router.put("/config/:namespace/:key").handler(this::handlePutConfig);
        router.delete("/config/:namespace/:key").handler(this::handleDeleteConfig);
        router.post("/config/:namespace/:key/rollback").handler(this::handleRollbackConfig);
        
        // Read operations (use ReadIndex for consistency)
        router.get("/config/:namespace/:key").handler(this::handleGetConfig);
        router.get("/config/:namespace").handler(this::handleListConfigs);
        router.get("/config/:namespace/:key/history").handler(this::handleGetConfigHistory);
        router.get("/config/:namespace/:key/version/:version").handler(this::handleGetConfigByVersion);
        
        // Health check
        router.get("/health").handler(this::handleHealthCheck);
        
        // Leader check
        router.get("/leader").handler(this::handleLeaderCheck);
        
        this.server = vertx.createHttpServer();
        this.server.requestHandler(router).listen(port, result -> {
            if (result.succeeded()) {
                LOG.info("HTTP server started on port {}", port);
            } else {
                LOG.error("Failed to start HTTP server on port {}", port, result.cause());
            }
        });
    }

    /**
     * Stop the HTTP server
     */
    public void stop() {
        if (server != null) {
            server.close(result -> {
                if (result.succeeded()) {
                    LOG.info("HTTP server stopped");
                } else {
                    LOG.error("Failed to stop HTTP server", result.cause());
                }
            });
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    /**
     * Handle PUT config request (write operation)
     */
    private void handlePutConfig(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        final String key = context.pathParam("key");

        try {
            final JsonObject body = context.getBodyAsJson();
            if (body == null || body.isEmpty()) {
                sendError(context, 400, "Request body cannot be empty");
                return;
            }

            // Extract properties from request body
            final Map<String, String> properties = new HashMap<>();
            for (String propertyKey : body.fieldNames()) {
                properties.put(propertyKey, body.getString(propertyKey));
            }

            if (properties.isEmpty()) {
                sendError(context, 400, "Properties cannot be empty");
                return;
            }

            final ConfigRequest request = ConfigRequest.createPutConfig(namespace, key, properties);

            // Submit to Raft
            submitTask(request, new ConfigCallback() {
                @Override
                public void onSuccess(ConfigResponse response) {
                    sendConfigEntry(context, 200, response.getConfigEntry());
                }

                @Override
                public void onError(String errorMsg) {
                    sendError(context, 500, errorMsg);
                }
            });

        } catch (Exception e) {
            LOG.error("Failed to handle PUT config request: namespace={}, key={}", namespace, key, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle DELETE config request (write operation)
     */
    private void handleDeleteConfig(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        final String key = context.pathParam("key");

        try {
            final ConfigRequest request = ConfigRequest.createDeleteConfig(namespace, key);

            // Submit to Raft
            submitTask(request, new ConfigCallback() {
                @Override
                public void onSuccess(ConfigResponse response) {
                    sendSuccess(context, 200, "Config deleted successfully");
                }

                @Override
                public void onError(String errorMsg) {
                    sendError(context, 404, errorMsg);
                }
            });

        } catch (Exception e) {
            LOG.error("Failed to handle DELETE config request: namespace={}, key={}", namespace, key, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle GET config request (read operation with ReadIndex)
     */
    private void handleGetConfig(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        final String key = context.pathParam("key");
        
        try {
            // Use ReadIndex for consistent read
            readIndexRead(() -> {
                final ConfigEntry entry = stateMachine.getStorage().getConfig(namespace, key);
                if (entry != null) {
                    sendConfigEntry(context, 200, entry);
                } else {
                    sendError(context, 404, String.format("Config not found: namespace=%s, key=%s", namespace, key));
                }
            }, error -> {
                sendError(context, 500, "Failed to read config: " + error);
            });
            
        } catch (Exception e) {
            LOG.error("Failed to handle GET config request: namespace={}, key={}", namespace, key, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle LIST configs request (read operation with ReadIndex)
     */
    private void handleListConfigs(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        
        try {
            // Use ReadIndex for consistent read
            readIndexRead(() -> {
                final java.util.List<ConfigEntry> entries = stateMachine.getStorage().listConfigs(namespace);
                sendConfigEntries(context, 200, entries);
            }, error -> {
                sendError(context, 500, "Failed to list configs: " + error);
            });
            
        } catch (Exception e) {
            LOG.error("Failed to handle LIST configs request: namespace={}", namespace, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle GET config history request (read operation with ReadIndex)
     */
    private void handleGetConfigHistory(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        final String key = context.pathParam("key");
        
        try {
            // Use ReadIndex for consistent read
            readIndexRead(() -> {
                final java.util.List<ConfigEntry> history = stateMachine.getStorage().getConfigHistory(namespace, key);
                sendConfigEntries(context, 200, history);
            }, error -> {
                sendError(context, 500, "Failed to get config history: " + error);
            });
            
        } catch (Exception e) {
            LOG.error("Failed to handle GET config history request: namespace={}, key={}", namespace, key, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle GET config by version request (read operation with ReadIndex)
     */
    private void handleGetConfigByVersion(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        final String key = context.pathParam("key");
        final String versionStr = context.pathParam("version");
        
        try {
            final long version = Long.parseLong(versionStr);
            
            // Use ReadIndex for consistent read
            readIndexRead(() -> {
                final ConfigEntry entry = stateMachine.getStorage().getConfigByVersion(namespace, key, version);
                if (entry != null) {
                    sendConfigEntry(context, 200, entry);
                } else {
                    sendError(context, 404, String.format("Config not found: namespace=%s, key=%s, version=%d", 
                        namespace, key, version));
                }
            }, error -> {
                sendError(context, 500, "Failed to get config by version: " + error);
            });
            
        } catch (NumberFormatException e) {
            sendError(context, 400, "Invalid version number: " + versionStr);
        } catch (Exception e) {
            LOG.error("Failed to handle GET config by version request: namespace={}, key={}, version={}", 
                namespace, key, versionStr, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle rollback config request (write operation)
     */
    private void handleRollbackConfig(RoutingContext context) {
        final String namespace = context.pathParam("namespace");
        final String key = context.pathParam("key");

        try {
            final JsonObject body = context.getBodyAsJson();
            if (body == null || !body.containsKey("version")) {
                sendError(context, 400, "Version is required in request body");
                return;
            }

            final long version = body.getLong("version");
            final ConfigRequest request = ConfigRequest.createRollbackConfig(namespace, key, version);

            // Submit to Raft
            submitTask(request, new ConfigCallback() {
                @Override
                public void onSuccess(ConfigResponse response) {
                    sendConfigEntry(context, 200, response.getConfigEntry());
                }

                @Override
                public void onError(String errorMsg) {
                    sendError(context, 500, errorMsg);
                }
            });

        } catch (Exception e) {
            LOG.error("Failed to handle rollback config request: namespace={}, key={}", namespace, key, e);
            sendError(context, 500, "Failed to process request: " + e.getMessage());
        }
    }

    /**
     * Handle health check request
     */
    private void handleHealthCheck(RoutingContext context) {
        final JsonObject response = new JsonObject().put("status", "ok").put("isLeader", stateMachine.isLeader())
            .put("nodeId", node.getNodeId().toString());

        context.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(response.encode());
    }

    /**
     * Handle leader check request
     */
    private void handleLeaderCheck(RoutingContext context) {
        final JsonObject response = new JsonObject().put("isLeader", stateMachine.isLeader()).put("nodeId",
            node.getNodeId().toString());

        if (node.getLeaderId() != null) {
            response.put("leaderId", node.getLeaderId().toString());
        }

        context.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(response.encode());
    }

    /**
     * Submit a task to Raft state machine
     */
    private void submitTask(ConfigRequest request, ConfigCallback callback) {
        try {
            // Check if this node is leader
            if (!node.isLeader()) {
                final String leaderId = node.getLeaderId() != null ? node.getLeaderId().toString() : "unknown";
                callback.onError("Not leader, current leader is: " + leaderId);
                return;
            }

            // Serialize request
            final byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

            // Create task
            final Task task = new Task();
            task.setData(ByteBuffer.wrap(data));
            task.setDone(new ConfigClosure(request, callback));

            // Apply task to Raft
            node.apply(task);

        } catch (CodecException e) {
            LOG.error("Failed to serialize request", e);
            callback.onError("Failed to serialize request: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to submit task", e);
            callback.onError("Failed to submit task: " + e.getMessage());
        }
    }

    /**
     * Perform a consistent read using ReadIndex
     */
    private void readIndexRead(Runnable readAction, java.util.function.Consumer<String> errorHandler) {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final Status[] statusHolder = new Status[1];

            // Use ReadIndex to ensure linearizable read
            node.readIndex(ByteBuffer.allocate(0).array(), new com.alipay.sofa.jraft.closure.ReadIndexClosure() {
                @Override
                public void run(Status status, long index, byte[] reqCtx) {
                    statusHolder[0] = status;
                    latch.countDown();
                }
            });

            // Wait for ReadIndex to complete
            if (!latch.await(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                LOG.error("ReadIndex timeout");
                errorHandler.accept("ReadIndex timeout");
                return;
            }

            final Status status = statusHolder[0];
            if (status != null && status.isOk()) {
                // Perform read operation
                readAction.run();
            } else {
                final String errorMsg = status != null ? status.getErrorMsg() : "Unknown error";
                LOG.error("ReadIndex failed: {}", errorMsg);
                errorHandler.accept("ReadIndex failed: " + errorMsg);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("ReadIndex interrupted", e);
            errorHandler.accept("ReadIndex interrupted: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("ReadIndex error", e);
            errorHandler.accept("ReadIndex error: " + e.getMessage());
        }
    }

    /**
     * Send a single config entry as JSON response
     */
    private void sendConfigEntry(RoutingContext context, int statusCode, ConfigEntry entry) {
        final JsonObject properties = new JsonObject();
        for (Map.Entry<String, String> prop : entry.getProperties().entrySet()) {
            properties.put(prop.getKey(), prop.getValue());
        }

        final JsonObject json = new JsonObject().put("namespace", entry.getNamespace()).put("key", entry.getKey())
            .put("properties", properties).put("version", entry.getVersion()).put("createTime", entry.getCreateTime())
            .put("updateTime", entry.getUpdateTime()).put("deleted", entry.isDeleted());

        context.response().setStatusCode(statusCode).putHeader("Content-Type", "application/json").end(json.encode());
    }

    /**
     * Send multiple config entries as JSON response
     */
    private void sendConfigEntries(RoutingContext context, int statusCode, java.util.List<ConfigEntry> entries) {
        final JsonArray array = new JsonArray();
        for (ConfigEntry entry : entries) {
            final JsonObject properties = new JsonObject();
            for (Map.Entry<String, String> prop : entry.getProperties().entrySet()) {
                properties.put(prop.getKey(), prop.getValue());
            }

            final JsonObject json = new JsonObject().put("namespace", entry.getNamespace()).put("key", entry.getKey())
                .put("properties", properties).put("version", entry.getVersion())
                .put("createTime", entry.getCreateTime()).put("updateTime", entry.getUpdateTime())
                .put("deleted", entry.isDeleted());
            array.add(json);
        }

        final JsonObject response = new JsonObject().put("count", entries.size()).put("configs", array);

        context.response().setStatusCode(statusCode).putHeader("Content-Type", "application/json")
            .end(response.encode());
    }

    /**
     * Send success response
     */
    private void sendSuccess(RoutingContext context, int statusCode, String message) {
        final JsonObject response = new JsonObject().put("success", true).put("message", message);

        context.response().setStatusCode(statusCode).putHeader("Content-Type", "application/json")
            .end(response.encode());
    }

    /**
     * Send error response
     */
    private void sendError(RoutingContext context, int statusCode, String errorMsg) {
        final JsonObject response = new JsonObject().put("success", false).put("error", errorMsg);

        context.response().setStatusCode(statusCode).putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
}
