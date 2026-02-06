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
import java.io.OutputStream;
import java.util.List;

/**
 * Configuration storage interface for managing configuration entries.
 * Implementations should provide thread-safe operations for configuration CRUD,
 * version management, and history tracking.
 *
 * @author lei
 */
public interface ConfigStorage {

    /**
     * Put a configuration entry into storage.
     * If the entry already exists, it will be updated with a new version.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param entry the configuration entry
     */
    void putConfig(String namespace, String key, ConfigEntry entry);

    /**
     * Get the latest configuration entry.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @return the configuration entry, or null if not found
     */
    ConfigEntry getConfig(String namespace, String key);

    /**
     * Delete a configuration entry.
     * The entry will be marked as deleted but history will be preserved.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @return true if the entry was deleted, false if not found
     */
    boolean deleteConfig(String namespace, String key);

    /**
     * List all configuration entries in a namespace.
     * Only returns non-deleted entries.
     *
     * @param namespace the namespace
     * @return list of configuration entries
     */
    List<ConfigEntry> listConfigs(String namespace);

    /**
     * Get the complete history of a configuration entry.
     * Returns all versions including deleted ones.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @return list of configuration entries ordered by version (oldest first)
     */
    List<ConfigEntry> getConfigHistory(String namespace, String key);

    /**
     * Get a specific version of a configuration entry.
     *
     * @param namespace the namespace
     * @param key the configuration key
     * @param version the version number
     * @return the configuration entry, or null if not found
     */
    ConfigEntry getConfigByVersion(String namespace, String key, long version);

    /**
     * Save the storage state to an output stream for snapshot.
     *
     * @param output the output stream
     * @throws IOException if an I/O error occurs
     */
    void saveSnapshot(OutputStream output) throws IOException;

    /**
     * Load the storage state from an input stream for snapshot.
     *
     * @param input the input stream
     * @throws IOException if an I/O error occurs
     */
    void loadSnapshot(InputStream input) throws IOException;
}
