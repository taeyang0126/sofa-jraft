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
import java.util.Objects;

/**
 * Configuration server options.
 *
 * @author lei
 */
public class ConfigServerOptions implements Serializable {

    private static final long serialVersionUID = -5597003954824547294L;

    private String            dataPath;
    private String            groupId;
    private String            serverId;
    private String            initialServerList;
    private int               port;

    public ConfigServerOptions() {
        // Default values
        this.dataPath = "/tmp/config_center";
        this.groupId = "config_center";
        this.port = 8080;
    }

    public ConfigServerOptions(String dataPath, String groupId, String serverId, String initialServerList, int port) {
        this.dataPath = Objects.requireNonNull(dataPath, "dataPath cannot be null");
        this.groupId = Objects.requireNonNull(groupId, "groupId cannot be null");
        this.serverId = Objects.requireNonNull(serverId, "serverId cannot be null");
        this.initialServerList = Objects.requireNonNull(initialServerList, "initialServerList cannot be null");
        this.port = port;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getInitialServerList() {
        return initialServerList;
    }

    public void setInitialServerList(String initialServerList) {
        this.initialServerList = initialServerList;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Validate the options
     */
    public void validate() {
        Objects.requireNonNull(dataPath, "dataPath cannot be null");
        Objects.requireNonNull(groupId, "groupId cannot be null");
        Objects.requireNonNull(serverId, "serverId cannot be null");
        Objects.requireNonNull(initialServerList, "initialServerList cannot be null");

        if (dataPath.trim().isEmpty()) {
            throw new IllegalArgumentException("dataPath cannot be empty");
        }
        if (groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("groupId cannot be empty");
        }
        if (serverId.trim().isEmpty()) {
            throw new IllegalArgumentException("serverId cannot be empty");
        }
        if (initialServerList.trim().isEmpty()) {
            throw new IllegalArgumentException("initialServerList cannot be empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
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
        ConfigServerOptions that = (ConfigServerOptions) obj;
        return port == that.port && Objects.equals(dataPath, that.dataPath) && Objects.equals(groupId, that.groupId)
               && Objects.equals(serverId, that.serverId) && Objects.equals(initialServerList, that.initialServerList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataPath, groupId, serverId, initialServerList, port);
    }

    @Override
    public String toString() {
        return "ConfigServerOptions{" + "dataPath='" + dataPath + '\'' + ", groupId='" + groupId + '\''
               + ", serverId='" + serverId + '\'' + ", initialServerList='" + initialServerList + '\'' + ", port="
               + port + '}';
    }
}