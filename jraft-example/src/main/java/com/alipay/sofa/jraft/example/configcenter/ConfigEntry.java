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
import java.util.Set;

/**
 * Configuration entry data model containing configuration values and metadata.
 * A configuration entry can contain multiple properties.
 *
 * @author lei
 */
public class ConfigEntry implements Serializable {

    private static final long   serialVersionUID = -8597003954824547294L;

    private String              namespace;
    private String              key;
    private Map<String, String> properties;
    private long                version;
    private long                createTime;
    private long                updateTime;
    private boolean             deleted;

    public ConfigEntry() {
        this.properties = new HashMap<>();
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
        this.version = 1L;
        this.deleted = false;
    }

    public ConfigEntry(String namespace, String key) {
        this();
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.key = Objects.requireNonNull(key, "key cannot be null");
    }

    public ConfigEntry(String namespace, String key, Map<String, String> properties) {
        this(namespace, key);
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    /**
     * Copy constructor for creating new version
     */
    public ConfigEntry(ConfigEntry other) {
        this.namespace = other.namespace;
        this.key = other.key;
        this.properties = new HashMap<>(other.properties);
        this.version = other.version + 1;
        this.createTime = other.createTime;
        this.updateTime = System.currentTimeMillis();
        this.deleted = false;
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

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Get a specific property value
     */
    public String getProperty(String propertyKey) {
        return properties.get(propertyKey);
    }

    /**
     * Set a specific property value
     */
    public void setProperty(String propertyKey, String propertyValue) {
        Objects.requireNonNull(propertyKey, "propertyKey cannot be null");
        if (propertyValue == null) {
            properties.remove(propertyKey);
        } else {
            properties.put(propertyKey, propertyValue);
        }
    }

    /**
     * Get all property keys
     */
    public Set<String> getPropertyKeys() {
        return properties.keySet();
    }

    /**
     * Check if the entry has any properties
     */
    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    /**
     * Get the unique identifier for this config entry
     */
    public String getUniqueId() {
        return namespace + ":" + key;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConfigEntry that = (ConfigEntry) obj;
        return version == that.version && createTime == that.createTime && updateTime == that.updateTime
               && deleted == that.deleted && Objects.equals(namespace, that.namespace) && Objects.equals(key, that.key)
               && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key, properties, version, createTime, updateTime, deleted);
    }

    @Override
    public String toString() {
        return "ConfigEntry{" + "namespace='" + namespace + '\'' + ", key='" + key + '\'' + ", properties="
               + properties + ", version=" + version + ", createTime=" + createTime + ", updateTime=" + updateTime
               + ", deleted=" + deleted + '}';
    }
}