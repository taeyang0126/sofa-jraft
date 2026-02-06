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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of ConfigStorage.
 * Thread-safe implementation using ReadWriteLock for concurrent access.
 * Data persistence is handled by SOFAJRaft's log mechanism.
 *
 * @author lei
 */
public class MemoryConfigStorage implements ConfigStorage {

    private static final Logger                               LOG = LoggerFactory.getLogger(MemoryConfigStorage.class);

    // namespace -> key -> list of versions (ordered by version)
    private final Map<String, Map<String, List<ConfigEntry>>> storage;
    // namespace -> key -> latest version entry
    private final Map<String, Map<String, ConfigEntry>>       latestVersionCache;
    private final ReadWriteLock                               lock;

    public MemoryConfigStorage() {
        this.storage = new ConcurrentHashMap<>();
        this.latestVersionCache = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }

    @Override
    public void putConfig(String namespace, String key, ConfigEntry entry) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(entry, "entry cannot be null");

        lock.writeLock().lock();
        try {
            // Get or create namespace storage
            Map<String, List<ConfigEntry>> namespaceStorage = storage.computeIfAbsent(namespace,
                k -> new ConcurrentHashMap<>());
            Map<String, ConfigEntry> namespaceCache = latestVersionCache.computeIfAbsent(namespace,
                k -> new ConcurrentHashMap<>());

            // Get or create version history for this key
            List<ConfigEntry> history = namespaceStorage.computeIfAbsent(key, k -> new ArrayList<>());

            // Ensure entry has correct namespace and key
            entry.setNamespace(namespace);
            entry.setKey(key);

            // If this is an update, increment version based on latest
            if (!history.isEmpty()) {
                ConfigEntry latest = history.get(history.size() - 1);
                entry.setVersion(latest.getVersion() + 1);
                entry.setCreateTime(latest.getCreateTime());
            } else {
                entry.setVersion(1L);
                if (entry.getCreateTime() == 0) {
                    entry.setCreateTime(System.currentTimeMillis());
                }
            }

            // Update timestamp
            entry.setUpdateTime(System.currentTimeMillis());

            // Add to history
            history.add(entry);

            // Update cache
            namespaceCache.put(key, entry);

            LOG.debug("Put config: namespace={}, key={}, version={}", namespace, key, entry.getVersion());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public ConfigEntry getConfig(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        lock.readLock().lock();
        try {
            Map<String, ConfigEntry> namespaceCache = latestVersionCache.get(namespace);
            if (namespaceCache == null) {
                return null;
            }

            ConfigEntry entry = namespaceCache.get(key);
            // Return null if entry is deleted
            if (entry != null && entry.isDeleted()) {
                return null;
            }

            return entry;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean deleteConfig(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        lock.writeLock().lock();
        try {
            Map<String, List<ConfigEntry>> namespaceStorage = storage.get(namespace);
            if (namespaceStorage == null) {
                return false;
            }

            List<ConfigEntry> history = namespaceStorage.get(key);
            if (history == null || history.isEmpty()) {
                return false;
            }

            ConfigEntry latest = history.get(history.size() - 1);
            if (latest.isDeleted()) {
                return false;
            }

            // Create a new version marked as deleted
            ConfigEntry deletedEntry = new ConfigEntry(latest);
            deletedEntry.setDeleted(true);
            deletedEntry.setUpdateTime(System.currentTimeMillis());

            // Add to history
            history.add(deletedEntry);

            // Update cache
            Map<String, ConfigEntry> namespaceCache = latestVersionCache.get(namespace);
            if (namespaceCache != null) {
                namespaceCache.put(key, deletedEntry);
            }

            LOG.debug("Delete config: namespace={}, key={}, version={}", namespace, key, deletedEntry.getVersion());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ConfigEntry> listConfigs(String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");

        lock.readLock().lock();
        try {
            Map<String, ConfigEntry> namespaceCache = latestVersionCache.get(namespace);
            if (namespaceCache == null) {
                return new ArrayList<>();
            }

            // Return only non-deleted entries
            return namespaceCache.values().stream().filter(entry -> !entry.isDeleted()).collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ConfigEntry> getConfigHistory(String namespace, String key) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        lock.readLock().lock();
        try {
            Map<String, List<ConfigEntry>> namespaceStorage = storage.get(namespace);
            if (namespaceStorage == null) {
                return new ArrayList<>();
            }

            List<ConfigEntry> history = namespaceStorage.get(key);
            if (history == null) {
                return new ArrayList<>();
            }

            // Return a copy to prevent external modification
            return new ArrayList<>(history);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public ConfigEntry getConfigByVersion(String namespace, String key, long version) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(key, "key cannot be null");

        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }

        lock.readLock().lock();
        try {
            Map<String, List<ConfigEntry>> namespaceStorage = storage.get(namespace);
            if (namespaceStorage == null) {
                return null;
            }

            List<ConfigEntry> history = namespaceStorage.get(key);
            if (history == null) {
                return null;
            }

            // Find the entry with the specified version
            for (ConfigEntry entry : history) {
                if (entry.getVersion() == version) {
                    return entry;
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void saveSnapshot(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output cannot be null");

        lock.readLock().lock();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(output);
            oos.writeObject(storage);
            oos.flush();
            LOG.info("Snapshot saved successfully");
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadSnapshot(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input cannot be null");

        lock.writeLock().lock();
        try {
            ObjectInputStream ois = new ObjectInputStream(input);
            Map<String, Map<String, List<ConfigEntry>>> loadedStorage = (Map<String, Map<String, List<ConfigEntry>>>) ois
                .readObject();

            // Clear existing data
            storage.clear();
            latestVersionCache.clear();

            // Load new data
            storage.putAll(loadedStorage);

            // Rebuild cache
            for (Map.Entry<String, Map<String, List<ConfigEntry>>> namespaceEntry : storage.entrySet()) {
                String namespace = namespaceEntry.getKey();
                Map<String, ConfigEntry> namespaceCache = latestVersionCache.computeIfAbsent(namespace,
                    k -> new ConcurrentHashMap<>());

                for (Map.Entry<String, List<ConfigEntry>> keyEntry : namespaceEntry.getValue().entrySet()) {
                    String key = keyEntry.getKey();
                    List<ConfigEntry> history = keyEntry.getValue();
                    if (!history.isEmpty()) {
                        namespaceCache.put(key, history.get(history.size() - 1));
                    }
                }
            }

            LOG.info("Snapshot loaded successfully");
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load snapshot", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
