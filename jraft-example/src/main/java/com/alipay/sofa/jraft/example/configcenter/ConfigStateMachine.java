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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.core.StateMachineAdapter;
import com.alipay.sofa.jraft.error.RaftError;
import com.alipay.sofa.jraft.error.RaftException;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.alipay.sofa.jraft.util.NamedThreadFactory;
import com.alipay.sofa.jraft.util.ThreadPoolUtil;

/**
 * Configuration state machine for distributed configuration center.
 * Handles configuration operations through Raft consensus protocol.
 *
 * @author lei
 */
public class ConfigStateMachine extends StateMachineAdapter {

    private static final Logger       LOG        = LoggerFactory.getLogger(ConfigStateMachine.class);
    private static ThreadPoolExecutor executor   = ThreadPoolUtil
                                                     .newBuilder()
                                                     .poolName("CONFIG_CENTER_EXECUTOR")
                                                     .enableMetric(true)
                                                     .coreThreads(3)
                                                     .maximumThreads(5)
                                                     .keepAliveSeconds(60L)
                                                     .workQueue(new SynchronousQueue<>())
                                                     .threadFactory(
                                                         new NamedThreadFactory("ConfigCenter-Executor-", true))
                                                     .build();

    private final ConfigStorage       storage;
    private final AtomicLong          leaderTerm = new AtomicLong(-1);

    public ConfigStateMachine() {
        this.storage = new MemoryConfigStorage();
    }

    public ConfigStateMachine(ConfigStorage storage) {
        this.storage = storage != null ? storage : new MemoryConfigStorage();
    }

    public boolean isLeader() {
        return this.leaderTerm.get() > 0;
    }

    public ConfigStorage getStorage() {
        return this.storage;
    }

    @Override
    public void onApply(final Iterator iter) {
        while (iter.hasNext()) {
            ConfigResponse response = null;
            ConfigRequest request = null;

            ConfigClosure closure = null;
            if (iter.done() != null) {
                // This task is applied by this node, get value from closure to avoid additional parsing.
                closure = (ConfigClosure) iter.done();
                request = closure.getRequest();
            } else {
                // Have to parse ConfigRequest from this user log.
                final ByteBuffer data = iter.getData();
                try {
                    request = SerializerManager.getSerializer(SerializerManager.Hessian2).deserialize(data.array(),
                        ConfigRequest.class.getName());
                } catch (final CodecException e) {
                    LOG.error("Fail to decode ConfigRequest at logIndex={}", iter.getIndex(), e);
                }
            }

            if (request != null) {
                try {
                    response = processRequest(request, iter.getIndex());
                } catch (Throwable t) {
                    LOG.error("Failed to process request: type={}, namespace={}, key={}, error={}", request.getType(),
                        request.getNamespace(), request.getKey(), t.getMessage(), t);
                    response = ConfigResponse.error("Failed to process request: " + t.getMessage(), t);
                }

                if (closure != null) {
                    closure.setResponse(response);
                    closure.run(Status.OK());
                }
            }

            iter.next();
        }
    }

    /**
     * Process configuration request and return response
     */
    private ConfigResponse processRequest(ConfigRequest request, long logIndex) {
        if (request == null) {
            return ConfigResponse.error("Request cannot be null");
        }

        try {
            request.validate();
        } catch (Exception e) {
            LOG.error("Invalid request: {}", e.getMessage(), e);
            return ConfigResponse.error("Invalid request: " + e.getMessage(), e);
        }

        LOG.debug("Processing request: type={}, namespace={}, key={} at logIndex={}", request.getType(),
            request.getNamespace(), request.getKey(), logIndex);

        switch (request.getType()) {
            case PUT_CONFIG:
                return handlePutConfig(request);
            case DELETE_CONFIG:
                return handleDeleteConfig(request);
            case GET_CONFIG:
                return handleGetConfig(request);
            case LIST_CONFIGS:
                return handleListConfigs(request);
            case GET_CONFIG_HISTORY:
                return handleGetConfigHistory(request);
            case GET_CONFIG_BY_VERSION:
                return handleGetConfigByVersion(request);
            case ROLLBACK_CONFIG:
                return handleRollbackConfig(request);
            default:
                return ConfigResponse.error("Unknown request type: " + request.getType());
        }
    }

    private ConfigResponse handlePutConfig(ConfigRequest request) {
        try {
            ConfigEntry entry = new ConfigEntry(request.getNamespace(), request.getKey(), request.getProperties());
            storage.putConfig(request.getNamespace(), request.getKey(), entry);
            ConfigEntry savedEntry = storage.getConfig(request.getNamespace(), request.getKey());
            LOG.info("Put config: namespace={}, key={}, version={}", request.getNamespace(), request.getKey(),
                savedEntry.getVersion());
            return ConfigResponse.success(savedEntry);
        } catch (Throwable t) {
            LOG.error("Failed to put config: namespace={}, key={}, error={}", request.getNamespace(), request.getKey(),
                t.getMessage(), t);
            return ConfigResponse.error("Failed to put config", t);
        }
    }

    private ConfigResponse handleDeleteConfig(ConfigRequest request) {
        try {
            boolean deleted = storage.deleteConfig(request.getNamespace(), request.getKey());
            if (deleted) {
                LOG.info("Delete config: namespace={}, key={}", request.getNamespace(), request.getKey());
                return ConfigResponse.success();
            } else {
                String errorMsg = String.format("Config not found: namespace=%s, key=%s", request.getNamespace(),
                    request.getKey());
                LOG.warn(errorMsg);
                return ConfigResponse.error(errorMsg);
            }
        } catch (Throwable t) {
            LOG.error("Failed to delete config: namespace={}, key={}, error={}", request.getNamespace(),
                request.getKey(), t.getMessage(), t);
            return ConfigResponse.error("Failed to delete config", t);
        }
    }

    private ConfigResponse handleGetConfig(ConfigRequest request) {
        try {
            ConfigEntry entry = storage.getConfig(request.getNamespace(), request.getKey());
            if (entry != null) {
                LOG.debug("Get config: namespace={}, key={}, version={}", request.getNamespace(), request.getKey(),
                    entry.getVersion());
                return ConfigResponse.success(entry);
            } else {
                String errorMsg = String.format("Config not found: namespace=%s, key=%s", request.getNamespace(),
                    request.getKey());
                LOG.debug(errorMsg);
                return ConfigResponse.error(errorMsg);
            }
        } catch (Throwable t) {
            LOG.error("Failed to get config: namespace={}, key={}, error={}", request.getNamespace(), request.getKey(),
                t.getMessage(), t);
            return ConfigResponse.error("Failed to get config", t);
        }
    }

    private ConfigResponse handleListConfigs(ConfigRequest request) {
        try {
            List<ConfigEntry> entries = storage.listConfigs(request.getNamespace());
            LOG.debug("List configs: namespace={}, count={}", request.getNamespace(), entries.size());
            return ConfigResponse.success(entries);
        } catch (Throwable t) {
            LOG.error("Failed to list configs: namespace={}, error={}", request.getNamespace(), t.getMessage(), t);
            return ConfigResponse.error("Failed to list configs", t);
        }
    }

    private ConfigResponse handleGetConfigHistory(ConfigRequest request) {
        try {
            List<ConfigEntry> history = storage.getConfigHistory(request.getNamespace(), request.getKey());
            LOG.debug("Get config history: namespace={}, key={}, count={}", request.getNamespace(), request.getKey(),
                history.size());
            return ConfigResponse.success(history);
        } catch (Throwable t) {
            LOG.error("Failed to get config history: namespace={}, key={}, error={}", request.getNamespace(),
                request.getKey(), t.getMessage(), t);
            return ConfigResponse.error("Failed to get config history", t);
        }
    }

    private ConfigResponse handleGetConfigByVersion(ConfigRequest request) {
        try {
            ConfigEntry entry = storage.getConfigByVersion(request.getNamespace(), request.getKey(),
                request.getVersion());
            if (entry != null) {
                LOG.debug("Get config by version: namespace={}, key={}, version={}", request.getNamespace(),
                    request.getKey(), request.getVersion());
                return ConfigResponse.success(entry);
            } else {
                String errorMsg = String.format("Config not found: namespace=%s, key=%s, version=%d",
                    request.getNamespace(), request.getKey(), request.getVersion());
                LOG.debug(errorMsg);
                return ConfigResponse.error(errorMsg);
            }
        } catch (Throwable t) {
            LOG.error("Failed to get config by version: namespace={}, key={}, version={}, error={}",
                request.getNamespace(), request.getKey(), request.getVersion(), t.getMessage(), t);
            return ConfigResponse.error("Failed to get config by version", t);
        }
    }

    private ConfigResponse handleRollbackConfig(ConfigRequest request) {
        try {
            // Get the target version
            ConfigEntry targetEntry = storage.getConfigByVersion(request.getNamespace(), request.getKey(),
                request.getVersion());
            if (targetEntry == null) {
                String errorMsg = String.format("Config version not found: namespace=%s, key=%s, version=%d",
                    request.getNamespace(), request.getKey(), request.getVersion());
                LOG.warn(errorMsg);
                return ConfigResponse.error(errorMsg);
            }

            // Create a new entry with the target version's properties
            ConfigEntry newEntry = new ConfigEntry(request.getNamespace(), request.getKey(),
                targetEntry.getProperties());
            storage.putConfig(request.getNamespace(), request.getKey(), newEntry);

            ConfigEntry savedEntry = storage.getConfig(request.getNamespace(), request.getKey());
            LOG.info("Rollback config: namespace={}, key={}, from version={} to new version={}",
                request.getNamespace(), request.getKey(), request.getVersion(), savedEntry.getVersion());
            return ConfigResponse.success(savedEntry);
        } catch (Throwable t) {
            LOG.error("Failed to rollback config: namespace={}, key={}, version={}, error={}", request.getNamespace(),
                request.getKey(), request.getVersion(), t.getMessage(), t);
            return ConfigResponse.error("Failed to rollback config", t);
        }
    }

    @Override
    public void onSnapshotSave(final SnapshotWriter writer, final Closure done) {
        executor.submit(() -> {
            final String snapshotPath = writer.getPath() + File.separator + "data";
            try (FileOutputStream fos = new FileOutputStream(snapshotPath)) {
                storage.saveSnapshot(fos);
                if (writer.addFile("data")) {
                    LOG.info("Snapshot saved successfully to {}", snapshotPath);
                    done.run(Status.OK());
                } else {
                    LOG.error("Failed to add snapshot file to writer: {}", snapshotPath);
                    done.run(new Status(RaftError.EIO, "Fail to add file to writer"));
                }
            } catch (IOException e) {
                LOG.error("Failed to save snapshot to {}", snapshotPath, e);
                done.run(new Status(RaftError.EIO, "Fail to save config snapshot %s", snapshotPath));
            }
        });
    }

    @Override
    public boolean onSnapshotLoad(final SnapshotReader reader) {
        if (isLeader()) {
            LOG.warn("Leader is not supposed to load snapshot");
            return false;
        }
        if (reader.getFileMeta("data") == null) {
            LOG.error("Fail to find data file in {}", reader.getPath());
            return false;
        }
        final String snapshotPath = reader.getPath() + File.separator + "data";
        try (FileInputStream fis = new FileInputStream(snapshotPath)) {
            storage.loadSnapshot(fis);
            LOG.info("Snapshot loaded successfully from {}", snapshotPath);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to load snapshot from {}", snapshotPath, e);
            return false;
        }
    }

    @Override
    public void onError(final RaftException e) {
        LOG.error("Raft error: {}", e.getMessage(), e);
    }

    @Override
    public void onLeaderStart(final long term) {
        this.leaderTerm.set(term);
        LOG.info("Node became leader at term {}", term);
        super.onLeaderStart(term);
    }

    @Override
    public void onLeaderStop(final Status status) {
        this.leaderTerm.set(-1);
        LOG.info("Node stopped being leader, status: {}", status);
        super.onLeaderStop(status);
    }
}
