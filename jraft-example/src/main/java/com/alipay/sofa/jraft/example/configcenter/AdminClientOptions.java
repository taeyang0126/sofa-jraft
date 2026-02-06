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
 * Admin client configuration options.
 *
 * @author lei
 */
public class AdminClientOptions implements Serializable {

    private static final long serialVersionUID = -4597003954824547294L;

    private String            serverUrl;
    private int               timeoutMs;
    private int               maxRetries;

    public AdminClientOptions() {
        // Default values
        this.timeoutMs = 5000;
        this.maxRetries = 3;
    }

    public AdminClientOptions(String serverUrl) {
        this();
        this.serverUrl = Objects.requireNonNull(serverUrl, "serverUrl cannot be null");
    }

    public AdminClientOptions(String serverUrl, int timeoutMs, int maxRetries) {
        this.serverUrl = Objects.requireNonNull(serverUrl, "serverUrl cannot be null");
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
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

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
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
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries cannot be negative");
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
        AdminClientOptions that = (AdminClientOptions) obj;
        return timeoutMs == that.timeoutMs && maxRetries == that.maxRetries
               && Objects.equals(serverUrl, that.serverUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverUrl, timeoutMs, maxRetries);
    }

    @Override
    public String toString() {
        return "AdminClientOptions{" + "serverUrl='" + serverUrl + '\'' + ", timeoutMs=" + timeoutMs + ", maxRetries="
               + maxRetries + '}';
    }
}