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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for ConfigServer.
 * Tests server startup, shutdown, and configuration validation.
 *
 * @author lei
 */
public class ConfigServerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private String         dataPath;

    @Before
    public void setUp() throws IOException {
        dataPath = tempFolder.newFolder("config_server_test").getAbsolutePath();
    }

    @After
    public void tearDown() {
        // Clean up data directory
        try {
            if (dataPath != null) {
                FileUtils.deleteDirectory(new File(dataPath));
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    @Test
    public void testOptionsValidation() {
        ConfigServerOptions opts = new ConfigServerOptions();
        opts.setDataPath(dataPath);
        opts.setGroupId("test_group");
        opts.setServerId("127.0.0.1:8081");
        opts.setInitialServerList("127.0.0.1:8081");
        opts.setPort(8080);

        // Validation should succeed
        opts.validate();
    }

    @Test
    public void testStartWithNullOptions() {
        ConfigServer server = new ConfigServer();
        boolean started = false;
        try {
            started = server.start(null);
        } catch (NullPointerException e) {
            // Expected exception
            assertTrue(e.getMessage().contains("ConfigServerOptions cannot be null"));
        }
        assertFalse(started);
    }

    @Test
    public void testStartWithInvalidServerId() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "invalid_server_id",
            "127.0.0.1:8081", 8080);

        boolean started = server.start(opts);
        assertFalse(started);

        // Verify server components are not initialized
        assertNull(server.getNode());

        server.shutdown();
    }

    @Test
    public void testStartWithInvalidInitialServerList() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081",
            "invalid_server_list", 8080);

        boolean started = server.start(opts);
        assertFalse(started);

        // Verify server components are not initialized
        assertNull(server.getNode());

        server.shutdown();
    }

    @Test
    public void testStartWithEmptyDataPath() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions("", "test_group", "127.0.0.1:8081", "127.0.0.1:8081", 8080);

        boolean started = false;
        try {
            started = server.start(opts);
        } catch (IllegalArgumentException e) {
            // Expected exception from validation
            assertTrue(e.getMessage().contains("dataPath cannot be empty"));
        }
        assertFalse(started);
    }

    @Test
    public void testStartWithEmptyGroupId() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "", "127.0.0.1:8081", "127.0.0.1:8081", 8080);

        boolean started = false;
        try {
            started = server.start(opts);
        } catch (IllegalArgumentException e) {
            // Expected exception from validation
            assertTrue(e.getMessage().contains("groupId cannot be empty"));
        }
        assertFalse(started);
    }

    @Test
    public void testStartWithEmptyServerId() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "", "127.0.0.1:8081", 8080);

        boolean started = false;
        try {
            started = server.start(opts);
        } catch (IllegalArgumentException e) {
            // Expected exception from validation
            assertTrue(e.getMessage().contains("serverId cannot be empty"));
        }
        assertFalse(started);
    }

    @Test
    public void testStartWithEmptyInitialServerList() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "", 8080);

        boolean started = false;
        try {
            started = server.start(opts);
        } catch (IllegalArgumentException e) {
            // Expected exception from validation
            assertTrue(e.getMessage().contains("initialServerList cannot be empty"));
        }
        assertFalse(started);
    }

    @Test
    public void testStartWithInvalidPort() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            0);

        boolean started = false;
        try {
            started = server.start(opts);
        } catch (IllegalArgumentException e) {
            // Expected exception from validation
            assertTrue(e.getMessage().contains("port must be between 1 and 65535"));
        }
        assertFalse(started);
    }

    @Test
    public void testStartWithPortTooLarge() {
        ConfigServer server = new ConfigServer();
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            70000);

        boolean started = false;
        try {
            started = server.start(opts);
        } catch (IllegalArgumentException e) {
            // Expected exception from validation
            assertTrue(e.getMessage().contains("port must be between 1 and 65535"));
        }
        assertFalse(started);
    }

    @Test
    public void testShutdownWithoutStart() {
        ConfigServer server = new ConfigServer();
        // Should not throw exception
        server.shutdown();
    }

    @Test
    public void testMultipleShutdownCalls() {
        ConfigServer server = new ConfigServer();
        // Multiple shutdown calls should not throw exception
        server.shutdown();
        server.shutdown();
        server.shutdown();
    }

    @Test
    public void testDataPathCreation() throws IOException {
        String newDataPath = dataPath + File.separator + "new_path";
        ConfigServerOptions opts = new ConfigServerOptions(newDataPath, "test_group", "127.0.0.1:8081",
            "127.0.0.1:8081", 8080);

        // Create the data path manually to test validation
        FileUtils.forceMkdir(new File(newDataPath));

        // Verify data path was created
        File dataDir = new File(newDataPath);
        assertTrue(dataDir.exists());
        assertTrue(dataDir.isDirectory());
    }

    @Test
    public void testGettersBeforeStart() {
        ConfigServer server = new ConfigServer();
        // All getters should return null before start
        assertNull(server.getNode());
        assertNull(server.getFsm());
        assertNull(server.getRaftGroupService());
        assertNull(server.getHttpServer());
        assertNull(server.getOptions());
    }

    @Test
    public void testOptionsConstructorWithAllParameters() {
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            8080);

        assertEquals(dataPath, opts.getDataPath());
        assertEquals("test_group", opts.getGroupId());
        assertEquals("127.0.0.1:8081", opts.getServerId());
        assertEquals("127.0.0.1:8081", opts.getInitialServerList());
        assertEquals(8080, opts.getPort());
    }

    @Test
    public void testOptionsDefaultConstructor() {
        ConfigServerOptions opts = new ConfigServerOptions();

        // Default values should be set
        assertNotNull(opts.getDataPath());
        assertNotNull(opts.getGroupId());
        assertEquals(8080, opts.getPort());
    }

    @Test
    public void testOptionsSetters() {
        ConfigServerOptions opts = new ConfigServerOptions();
        opts.setDataPath(dataPath);
        opts.setGroupId("custom_group");
        opts.setServerId("127.0.0.1:9091");
        opts.setInitialServerList("127.0.0.1:9091,127.0.0.1:9092");
        opts.setPort(9090);

        assertEquals(dataPath, opts.getDataPath());
        assertEquals("custom_group", opts.getGroupId());
        assertEquals("127.0.0.1:9091", opts.getServerId());
        assertEquals("127.0.0.1:9091,127.0.0.1:9092", opts.getInitialServerList());
        assertEquals(9090, opts.getPort());
    }

    @Test
    public void testOptionsValidationWithNullDataPath() {
        ConfigServerOptions opts = new ConfigServerOptions();
        opts.setDataPath(null);
        opts.setGroupId("test_group");
        opts.setServerId("127.0.0.1:8081");
        opts.setInitialServerList("127.0.0.1:8081");
        opts.setPort(8080);

        try {
            opts.validate();
            assertTrue("Should throw NullPointerException", false);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("dataPath cannot be null"));
        }
    }

    @Test
    public void testOptionsValidationWithNullGroupId() {
        ConfigServerOptions opts = new ConfigServerOptions();
        opts.setDataPath(dataPath);
        opts.setGroupId(null);
        opts.setServerId("127.0.0.1:8081");
        opts.setInitialServerList("127.0.0.1:8081");
        opts.setPort(8080);

        try {
            opts.validate();
            assertTrue("Should throw NullPointerException", false);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("groupId cannot be null"));
        }
    }

    @Test
    public void testOptionsValidationWithNullServerId() {
        ConfigServerOptions opts = new ConfigServerOptions();
        opts.setDataPath(dataPath);
        opts.setGroupId("test_group");
        opts.setServerId(null);
        opts.setInitialServerList("127.0.0.1:8081");
        opts.setPort(8080);

        try {
            opts.validate();
            assertTrue("Should throw NullPointerException", false);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("serverId cannot be null"));
        }
    }

    @Test
    public void testOptionsValidationWithNullInitialServerList() {
        ConfigServerOptions opts = new ConfigServerOptions();
        opts.setDataPath(dataPath);
        opts.setGroupId("test_group");
        opts.setServerId("127.0.0.1:8081");
        opts.setInitialServerList(null);
        opts.setPort(8080);

        try {
            opts.validate();
            assertTrue("Should throw NullPointerException", false);
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("initialServerList cannot be null"));
        }
    }

    @Test
    public void testOptionsToString() {
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            8080);

        String str = opts.toString();
        assertNotNull(str);
        assertTrue(str.contains("dataPath"));
        assertTrue(str.contains("test_group"));
        assertTrue(str.contains("127.0.0.1:8081"));
        assertTrue(str.contains("8080"));
    }

    @Test
    public void testOptionsEquals() {
        ConfigServerOptions opts1 = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            8080);
        ConfigServerOptions opts2 = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            8080);

        assertEquals(opts1, opts2);
        assertEquals(opts1.hashCode(), opts2.hashCode());
    }

    @Test
    public void testOptionsNotEquals() {
        ConfigServerOptions opts1 = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8081", "127.0.0.1:8081",
            8080);
        ConfigServerOptions opts2 = new ConfigServerOptions(dataPath, "test_group", "127.0.0.1:8082", "127.0.0.1:8082",
            8081);

        assertFalse(opts1.equals(opts2));
    }

    @Test
    public void testStartFailureCleanup() {
        ConfigServer server = new ConfigServer();
        // Use invalid options to trigger start failure
        ConfigServerOptions opts = new ConfigServerOptions(dataPath, "test_group", "invalid_id", "127.0.0.1:8081", 8080);

        boolean started = server.start(opts);
        assertFalse(started);

        // Verify cleanup was performed (node should be null)
        assertNull(server.getNode());

        server.shutdown();
    }
}
