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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Client request data model for configuration center operations.
 *
 * @author lei
 */
public class ConfigRequest implements Serializable {

    private static final long serialVersionUID = -7597003954824547294L;

    /**
     * Request operation types
     */
    public enum Type {
        PUT_CONFIG, GET_CONFIG, DELETE_CONFIG, LIST_CONFIGS, GET_CONFIG_HISTORY, GET_CONFIG_BY_VERSION, ROLLBACK_CONFIG
    }

    private Type                type;
    private String              namespace;
    private String              key;
    private Map<String, String> properties;
    private long                version;

    public ConfigRequest() {
        this.properties = new HashMap<>();
    }

    public ConfigRequest(Type type, String namespace, String key) {
        this();
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.key = key; // key can be null for LIST_CONFIGS operation
    }

    public ConfigRequest(Type type, String namespace, String key, Map<String, String> properties) {
        this(type, namespace, key);
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    /**
     * Create a PUT_CONFIG request
     */
    public static ConfigRequest createPutConfig(String namespace, String key, Map<String, String> properties) {
        Objects.requireNonNull(key, "key cannot be null for PUT_CONFIG");
        Objects.requireNonNull(properties, "properties cannot be null for PUT_CONFIG");
        return new ConfigRequest(Type.PUT_CONFIG, namespace, key, properties);
    }

    /**
     * Create a PUT_CONFIG request with single property
     */
    public static ConfigRequest createPutConfig(String namespace, String key, String propertyKey, String propertyValue) {
        Objects.requireNonNull(key, "key cannot be null for PUT_CONFIG");
        Objects.requireNonNull(propertyKey, "propertyKey cannot be null for PUT_CONFIG");
        Objects.requireNonNull(propertyValue, "propertyValue cannot be null for PUT_CONFIG");
        Map<String, String> properties = new HashMap<>();
        properties.put(propertyKey, propertyValue);
        return new ConfigRequest(Type.PUT_CONFIG, namespace, key, properties);
    }

    /**
     * Create a GET_CONFIG request
     */
    public static ConfigRequest createGetConfig(String namespace, String key) {
        Objects.requireNonNull(key, "key cannot be null for GET_CONFIG");
        return new ConfigRequest(Type.GET_CONFIG, namespace, key);
    }

    /**
     * Create a DELETE_CONFIG request
     */
    public static ConfigRequest createDeleteConfig(String namespace, String key) {
        Objects.requireNonNull(key, "key cannot be null for DELETE_CONFIG");
        return new ConfigRequest(Type.DELETE_CONFIG, namespace, key);
    }

    /**
     * Create a LIST_CONFIGS request
     */
    public static ConfigRequest createListConfigs(String namespace) {
        return new ConfigRequest(Type.LIST_CONFIGS, namespace, null);
    }

    /**
     * Create a GET_CONFIG_HISTORY request
     */
    public static ConfigRequest createGetConfigHistory(String namespace, String key) {
        Objects.requireNonNull(key, "key cannot be null for GET_CONFIG_HISTORY");
        return new ConfigRequest(Type.GET_CONFIG_HISTORY, namespace, key);
    }

    /**
     * Create a GET_CONFIG_BY_VERSION request
     */
    public static ConfigRequest createGetConfigByVersion(String namespace, String key, long version) {
        Objects.requireNonNull(key, "key cannot be null for GET_CONFIG_BY_VERSION");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        ConfigRequest request = new ConfigRequest(Type.GET_CONFIG_BY_VERSION, namespace, key);
        request.setVersion(version);
        return request;
    }

    /**
     * Create a ROLLBACK_CONFIG request
     */
    public static ConfigRequest createRollbackConfig(String namespace, String key, long version) {
        Objects.requireNonNull(key, "key cannot be null for ROLLBACK_CONFIG");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        ConfigRequest request = new ConfigRequest(Type.ROLLBACK_CONFIG, namespace, key);
        request.setVersion(version);
        return request;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties != null ? properties : new HashMap<>();
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Validate the request based on operation type
     */
    public void validate() {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(namespace, "namespace cannot be null");

        switch (type) {
            case PUT_CONFIG:
                Objects.requireNonNull(key, "key cannot be null for PUT_CONFIG");
                if (properties == null || properties.isEmpty()) {
                    throw new IllegalArgumentException("properties cannot be null or empty for PUT_CONFIG");
                }
                break;
            case GET_CONFIG:
            case DELETE_CONFIG:
            case GET_CONFIG_HISTORY:
                Objects.requireNonNull(key, "key cannot be null for " + type);
                break;
            case GET_CONFIG_BY_VERSION:
            case ROLLBACK_CONFIG:
                Objects.requireNonNull(key, "key cannot be null for " + type);
                if (version <= 0) {
                    throw new IllegalArgumentException("version must be positive for " + type);
                }
                break;
            case LIST_CONFIGS:
                // key can be null for LIST_CONFIGS
                break;
            default:
                throw new IllegalArgumentException("Unknown request type: " + type);
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
        ConfigRequest that = (ConfigRequest) obj;
        return version == that.version && type == that.type && Objects.equals(namespace, that.namespace)
               && Objects.equals(key, that.key) && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, namespace, key, properties, version);
    }

    @Override
    public String toString() {
        return "ConfigRequest{" + "type=" + type + ", namespace='" + namespace + '\'' + ", key='" + key + '\''
               + ", properties=" + properties + ", version=" + version + '}';
    }
}