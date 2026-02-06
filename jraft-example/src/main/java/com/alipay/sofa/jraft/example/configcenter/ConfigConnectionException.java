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

/**
 * Exception thrown when a connection to the configuration center fails.
 *
 * @author lei
 */
public class ConfigConnectionException extends ConfigException {

    private static final long serialVersionUID = -4597003954824547294L;

    public ConfigConnectionException(String serverUrl) {
        super(ConfigErrorCode.CONFIG_CONNECTION_FAILED, String.format("Failed to connect to configuration center: %s",
            serverUrl));
    }

    public ConfigConnectionException(String serverUrl, Throwable cause) {
        super(ConfigErrorCode.CONFIG_CONNECTION_FAILED, String.format("Failed to connect to configuration center: %s",
            serverUrl), cause);
    }
}
