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
 * Exception thrown when a configuration operation times out.
 *
 * @author lei
 */
public class ConfigTimeoutException extends ConfigException {

    private static final long serialVersionUID = -3597003954824547294L;

    public ConfigTimeoutException(String operation, long timeoutMs) {
        super(ConfigErrorCode.CONFIG_TIMEOUT, String.format("Operation '%s' timed out after %d ms", operation,
            timeoutMs));
    }

    public ConfigTimeoutException(String operation, long timeoutMs, Throwable cause) {
        super(ConfigErrorCode.CONFIG_TIMEOUT, String.format("Operation '%s' timed out after %d ms", operation,
            timeoutMs), cause);
    }
}
