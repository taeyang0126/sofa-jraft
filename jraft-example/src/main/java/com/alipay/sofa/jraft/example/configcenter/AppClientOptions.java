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
 * Application client configuration options.
 *
 * @author lei
 */
public class AppClientOptions implements Serializable {

    private static final long serialVersionUID = -3597003954824547294L;

    private String            serverUrl;
    private int               timeoutMs;
    private long              pollingIntervalMs;

    public AppClientOptions() {
        // Default values
        this.timeoutMs = 5000;
        this.pollingIntervalMs = 30000; // 30 seconds polling interval
    }

    public AppClientOptions(String serverUrl) {
        this();
        this.serverUrl = Objects.requireNonNull(serverUrl, "serverUrl cannot be null");
    }

    public AppClientOptions(String serverUrl, int timeoutMs, long pollingIntervalMs) {
        this.serverUrl = Objects.requireNonNull(serverUrl, "serverUrl cannot be null");
        this.timeoutMs = timeoutMs;
        this.pollingIntervalMs = pollingIntervalMs;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public void setPollingIntervalMs(long pollingIntervalMs) {
        this.pollingIntervalMs = pollingIntervalMs;
    }

    /**
     * Validate the options
     */
    public void validate() {
        Objects.requireNonNull(serverUrl, "serverUrl cannot be null");
        if (serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("serverUrl cannot be empty");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        if (pollingIntervalMs <= 0) {
            throw new IllegalArgumentException("pollingIntervalMs must be positive");
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
        AppClientOptions that = (AppClientOptions) obj;
        return timeoutMs == that.timeoutMs && pollingIntervalMs == that.pollingIntervalMs
               && Objects.equals(serverUrl, that.serverUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverUrl, timeoutMs, pollingIntervalMs);
    }

    @Override
    public String toString() {
        return "AppClientOptions{" + "serverUrl='" + serverUrl + '\'' + ", timeoutMs=" + timeoutMs
               + ", pollingIntervalMs=" + pollingIntervalMs + '}';
    }
}