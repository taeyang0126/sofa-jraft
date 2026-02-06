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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Server response data model for configuration center operations.
 *
 * @author lei
 */
public class ConfigResponse implements Serializable {

    private static final long serialVersionUID = -6597003954824547294L;

    private boolean           success;
    private String            errorMsg;
    private ConfigEntry       configEntry;
    private List<ConfigEntry> configEntries;

    public ConfigResponse() {
        this.success = false;
        this.configEntries = new ArrayList<>();
    }

    public ConfigResponse(boolean success) {
        this();
        this.success = success;
    }

    public ConfigResponse(boolean success, String errorMsg) {
        this(success);
        this.errorMsg = errorMsg;
    }

    /**
     * Create a successful response with single config entry
     */
    public static ConfigResponse success(ConfigEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        ConfigResponse response = new ConfigResponse(true);
        response.configEntry = entry;
        return response;
    }

    /**
     * Create a successful response with multiple config entries
     */
    public static ConfigResponse success(List<ConfigEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");
        ConfigResponse response = new ConfigResponse(true);
        response.configEntries = new ArrayList<>(entries);
        return response;
    }

    /**
     * Create a successful response without data
     */
    public static ConfigResponse success() {
        return new ConfigResponse(true);
    }

    /**
     * Create an error response
     */
    public static ConfigResponse error(String errorMsg) {
        Objects.requireNonNull(errorMsg, "errorMsg cannot be null");
        return new ConfigResponse(false, errorMsg);
    }

    /**
     * Create an error response with exception
     */
    public static ConfigResponse error(String errorMsg, Throwable cause) {
        Objects.requireNonNull(errorMsg, "errorMsg cannot be null");
        String message = errorMsg;
        if (cause != null && cause.getMessage() != null) {
            message = errorMsg + ": " + cause.getMessage();
        }
        return new ConfigResponse(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public ConfigEntry getConfigEntry() {
        return configEntry;
    }

    public void setConfigEntry(ConfigEntry configEntry) {
        this.configEntry = configEntry;
    }

    public List<ConfigEntry> getConfigEntries() {
        return configEntries;
    }

    public void setConfigEntries(List<ConfigEntry> configEntries) {
        this.configEntries = configEntries != null ? configEntries : new ArrayList<>();
    }

    /**
     * Check if response has single config entry
     */
    public boolean hasConfigEntry() {
        return configEntry != null;
    }

    /**
     * Check if response has multiple config entries
     */
    public boolean hasConfigEntries() {
        return configEntries != null && !configEntries.isEmpty();
    }

    /**
     * Get the count of config entries
     */
    public int getConfigEntryCount() {
        if (configEntry != null) {
            return 1;
        }
        return configEntries != null ? configEntries.size() : 0;
    }

    /**
     * Add a config entry to the response
     */
    public void addConfigEntry(ConfigEntry entry) {
        if (entry != null) {
            if (configEntries == null) {
                configEntries = new ArrayList<>();
            }
            configEntries.add(entry);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConfigResponse that = (ConfigResponse) obj;
        return success == that.success && Objects.equals(errorMsg, that.errorMsg)
               && Objects.equals(configEntry, that.configEntry) && Objects.equals(configEntries, that.configEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorMsg, configEntry, configEntries);
    }

    @Override
    public String toString() {
        return "ConfigResponse{" + "success=" + success + ", errorMsg='" + errorMsg + '\'' + ", configEntry="
               + configEntry + ", configEntries=" + configEntries + '}';
    }
}