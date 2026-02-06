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

import java.util.Objects;

import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Status;

/**
 * Configuration closure for handling asynchronous configuration operations.
 * Used to pass request and receive response through Raft state machine.
 *
 * @author lei
 */
public class ConfigClosure implements Closure {

    private final ConfigRequest  request;
    private ConfigResponse       response;
    private final ConfigCallback callback;

    public ConfigClosure(ConfigRequest request, ConfigCallback callback) {
        this.request = Objects.requireNonNull(request, "request cannot be null");
        this.callback = callback;
    }

    public ConfigRequest getRequest() {
        return request;
    }

    public ConfigResponse getResponse() {
        return response;
    }

    public void setResponse(ConfigResponse response) {
        this.response = response;
    }

    @Override
    public void run(Status status) {
        if (callback != null) {
            if (status.isOk()) {
                callback.onSuccess(response);
            } else {
                callback.onError(status.getErrorMsg());
            }
        }
    }
}
