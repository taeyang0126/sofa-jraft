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
 * Error codes for configuration center operations.
 *
 * @author lei
 */
public final class ConfigErrorCode {

    // Configuration operation errors
    public static final String CONFIG_NOT_FOUND         = "CONFIG_NOT_FOUND";
    public static final String CONFIG_ALREADY_EXISTS    = "CONFIG_ALREADY_EXISTS";
    public static final String CONFIG_INVALID_PARAMETER = "CONFIG_INVALID_PARAMETER";

    // Namespace operation errors
    public static final String NAMESPACE_NOT_FOUND      = "NAMESPACE_NOT_FOUND";
    public static final String NAMESPACE_ALREADY_EXISTS = "NAMESPACE_ALREADY_EXISTS";

    // Version operation errors
    public static final String VERSION_NOT_FOUND        = "VERSION_NOT_FOUND";
    public static final String VERSION_CONFLICT         = "VERSION_CONFLICT";

    // Connection and timeout errors
    public static final String CONFIG_TIMEOUT           = "CONFIG_TIMEOUT";
    public static final String CONFIG_CONNECTION_FAILED = "CONFIG_CONNECTION_FAILED";

    // Raft operation errors
    public static final String RAFT_NOT_LEADER          = "RAFT_NOT_LEADER";
    public static final String RAFT_OPERATION_FAILED    = "RAFT_OPERATION_FAILED";

    // Serialization errors
    public static final String SERIALIZATION_ERROR      = "SERIALIZATION_ERROR";
    public static final String DESERIALIZATION_ERROR    = "DESERIALIZATION_ERROR";

    // Internal errors
    public static final String INTERNAL_ERROR           = "INTERNAL_ERROR";

    private ConfigErrorCode() {
        // Utility class, prevent instantiation
    }
}
