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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.alipay.remoting.serialization.SerializerManager;
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter.LocalFileMeta;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;

/**
 * Unit tests for ConfigStateMachine.
 *
 * @author lei
 */
public class ConfigStateMachineTest {

    @Rule
    public TemporaryFolder     tempFolder = new TemporaryFolder();

    private ConfigStateMachine stateMachine;
    private ConfigStorage      storage;

    @Before
    public void setUp() {
        storage = new MemoryConfigStorage();
        stateMachine = new ConfigStateMachine(storage);
    }

    @After
    public void tearDown() {
        stateMachine = null;
        storage = null;
    }

    @Test
    public void testIsLeader() {
        assertFalse(stateMachine.isLeader());

        stateMachine.onLeaderStart(1L);
        assertTrue(stateMachine.isLeader());

        stateMachine.onLeaderStop(Status.OK());
        assertFalse(stateMachine.isLeader());
    }

    @Test
    public void testGetStorage() {
        assertNotNull(stateMachine.getStorage());
        assertEquals(storage, stateMachine.getStorage());
    }

    @Test
    public void testOnApplyPutConfig() throws Exception {
        // Create a PUT_CONFIG request
        Map<String, String> properties = new HashMap<>();
        properties.put("url", "jdbc:mysql://localhost:3306/test");
        properties.put("username", "root");
        ConfigRequest request = ConfigRequest.createPutConfig("default", "db.config", properties);

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator
        Iterator iter = createMockIterator(data, null);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Verify the config was stored
        ConfigEntry entry = storage.getConfig("default", "db.config");
        assertNotNull(entry);
        assertEquals("default", entry.getNamespace());
        assertEquals("db.config", entry.getKey());
        assertEquals("jdbc:mysql://localhost:3306/test", entry.getProperty("url"));
        assertEquals("root", entry.getProperty("username"));
        assertEquals(1L, entry.getVersion());
    }

    @Test
    public void testOnApplyPutConfigWithClosure() throws Exception {
        // Create a PUT_CONFIG request
        Map<String, String> properties = new HashMap<>();
        properties.put("timeout", "5000");
        ConfigRequest request = ConfigRequest.createPutConfig("default", "app.config", properties);

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("app.config", response.getConfigEntry().getKey());
        assertEquals("5000", response.getConfigEntry().getProperty("timeout"));
    }

    @Test
    public void testOnApplyDeleteConfig() throws Exception {
        // First, put a config
        Map<String, String> properties = new HashMap<>();
        properties.put("value", "test");
        ConfigEntry entry = new ConfigEntry("default", "test.key", properties);
        storage.putConfig("default", "test.key", entry);

        // Create a DELETE_CONFIG request
        ConfigRequest request = ConfigRequest.createDeleteConfig("default", "test.key");

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator
        Iterator iter = createMockIterator(data, null);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Verify the config was deleted
        ConfigEntry deletedEntry = storage.getConfig("default", "test.key");
        assertNull(deletedEntry);
    }

    @Test
    public void testOnApplyGetConfig() throws Exception {
        // First, put a config
        Map<String, String> properties = new HashMap<>();
        properties.put("value", "test");
        ConfigEntry entry = new ConfigEntry("default", "test.key", properties);
        storage.putConfig("default", "test.key", entry);

        // Create a GET_CONFIG request
        ConfigRequest request = ConfigRequest.createGetConfig("default", "test.key");

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals("test.key", response.getConfigEntry().getKey());
        assertEquals("test", response.getConfigEntry().getProperty("value"));
    }

    @Test
    public void testOnApplyRollbackConfig() throws Exception {
        // Put initial config
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "v1");
        ConfigEntry entry1 = new ConfigEntry("default", "test.key", properties1);
        storage.putConfig("default", "test.key", entry1);

        // Update config
        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "v2");
        ConfigEntry entry2 = new ConfigEntry("default", "test.key", properties2);
        storage.putConfig("default", "test.key", entry2);

        // Verify current version is 2
        ConfigEntry current = storage.getConfig("default", "test.key");
        assertEquals(2L, current.getVersion());
        assertEquals("v2", current.getProperty("value"));

        // Create a ROLLBACK_CONFIG request to version 1
        ConfigRequest request = ConfigRequest.createRollbackConfig("default", "test.key", 1L);

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator
        Iterator iter = createMockIterator(data, null);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Verify the config was rolled back
        ConfigEntry rolledBack = storage.getConfig("default", "test.key");
        assertNotNull(rolledBack);
        assertEquals(3L, rolledBack.getVersion()); // New version created
        assertEquals("v1", rolledBack.getProperty("value")); // Value from version 1
    }

    @Test
    public void testOnSnapshotSaveAndLoad() throws Exception {
        // Put some configs
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("url", "jdbc:mysql://localhost:3306/test");
        ConfigEntry entry1 = new ConfigEntry("default", "db.config", properties1);
        storage.putConfig("default", "db.config", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("timeout", "5000");
        ConfigEntry entry2 = new ConfigEntry("app", "app.config", properties2);
        storage.putConfig("app", "app.config", entry2);

        // Create snapshot directory
        File snapshotDir = tempFolder.newFolder("snapshot");

        // Mock SnapshotWriter
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(writer.addFile(anyString())).thenReturn(true);

        // Save snapshot
        final CountDownLatch saveLatch = new CountDownLatch(1);
        final AtomicReference<Status> saveStatus = new AtomicReference<>();
        stateMachine.onSnapshotSave(writer, new Closure() {
            @Override
            public void run(Status status) {
                saveStatus.set(status);
                saveLatch.countDown();
            }
        });

        // Wait for save to complete
        assertTrue(saveLatch.await(5, TimeUnit.SECONDS));
        assertTrue(saveStatus.get().isOk());

        // Verify snapshot file was created
        File snapshotFile = new File(snapshotDir, "data");
        assertTrue(snapshotFile.exists());

        // Create a new storage and state machine
        ConfigStorage newStorage = new MemoryConfigStorage();
        ConfigStateMachine newStateMachine = new ConfigStateMachine(newStorage);

        // Mock SnapshotReader
        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(reader.getFileMeta(anyString())).thenReturn(LocalFileMeta.newBuilder().build());

        // Load snapshot
        boolean loaded = newStateMachine.onSnapshotLoad(reader);
        assertTrue(loaded);

        // Verify configs were restored
        ConfigEntry restoredEntry1 = newStorage.getConfig("default", "db.config");
        assertNotNull(restoredEntry1);
        assertEquals("jdbc:mysql://localhost:3306/test", restoredEntry1.getProperty("url"));

        ConfigEntry restoredEntry2 = newStorage.getConfig("app", "app.config");
        assertNotNull(restoredEntry2);
        assertEquals("5000", restoredEntry2.getProperty("timeout"));
    }

    @Test
    public void testOnSnapshotLoadAsLeader() throws Exception {
        // Set as leader
        stateMachine.onLeaderStart(1L);

        // Create snapshot directory
        File snapshotDir = tempFolder.newFolder("snapshot");

        // Mock SnapshotReader
        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(reader.getFileMeta(anyString())).thenReturn(LocalFileMeta.newBuilder().build());

        // Try to load snapshot as leader (should fail)
        boolean loaded = stateMachine.onSnapshotLoad(reader);
        assertFalse(loaded);
    }

    @Test
    public void testOnSnapshotLoadMissingFile() throws Exception {
        // Create snapshot directory
        File snapshotDir = tempFolder.newFolder("snapshot");

        // Mock SnapshotReader with no file
        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(reader.getFileMeta(anyString())).thenReturn(null);

        // Try to load snapshot (should fail)
        boolean loaded = stateMachine.onSnapshotLoad(reader);
        assertFalse(loaded);
    }

    @Test
    public void testOnApplyInvalidRequest() throws Exception {
        // Create an invalid request (missing required fields)
        ConfigRequest request = new ConfigRequest();
        request.setType(ConfigRequest.Type.PUT_CONFIG);
        request.setNamespace("default");
        // Missing key and properties

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response is an error
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMsg());
    }

    @Test
    public void testOnApplyListConfigs() throws Exception {
        // Put some configs in the same namespace
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "test1");
        ConfigEntry entry1 = new ConfigEntry("default", "key1", properties1);
        storage.putConfig("default", "key1", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "test2");
        ConfigEntry entry2 = new ConfigEntry("default", "key2", properties2);
        storage.putConfig("default", "key2", entry2);

        // Create a LIST_CONFIGS request
        ConfigRequest request = ConfigRequest.createListConfigs("default");

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntries());
        assertEquals(2, response.getConfigEntries().size());
    }

    @Test
    public void testOnApplyGetConfigHistory() throws Exception {
        // Put and update a config to create history
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "v1");
        ConfigEntry entry1 = new ConfigEntry("default", "test.key", properties1);
        storage.putConfig("default", "test.key", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "v2");
        ConfigEntry entry2 = new ConfigEntry("default", "test.key", properties2);
        storage.putConfig("default", "test.key", entry2);

        // Create a GET_CONFIG_HISTORY request
        ConfigRequest request = ConfigRequest.createGetConfigHistory("default", "test.key");

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntries());
        assertEquals(2, response.getConfigEntries().size());
    }

    @Test
    public void testOnApplyGetConfigByVersion() throws Exception {
        // Put and update a config to create versions
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "v1");
        ConfigEntry entry1 = new ConfigEntry("default", "test.key", properties1);
        storage.putConfig("default", "test.key", entry1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "v2");
        ConfigEntry entry2 = new ConfigEntry("default", "test.key", properties2);
        storage.putConfig("default", "test.key", entry2);

        // Create a GET_CONFIG_BY_VERSION request for version 1
        ConfigRequest request = ConfigRequest.createGetConfigByVersion("default", "test.key", 1L);

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getConfigEntry());
        assertEquals(1L, response.getConfigEntry().getVersion());
        assertEquals("v1", response.getConfigEntry().getProperty("value"));
    }

    @Test
    public void testOnApplyMultipleEntries() throws Exception {
        // Create multiple PUT_CONFIG requests
        Map<String, String> properties1 = new HashMap<>();
        properties1.put("value", "test1");
        ConfigRequest request1 = ConfigRequest.createPutConfig("default", "key1", properties1);
        byte[] data1 = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request1);

        Map<String, String> properties2 = new HashMap<>();
        properties2.put("value", "test2");
        ConfigRequest request2 = ConfigRequest.createPutConfig("default", "key2", properties2);
        byte[] data2 = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request2);

        // Create a mock iterator that returns multiple entries
        Iterator iter = mock(Iterator.class);
        when(iter.hasNext()).thenReturn(true, true, false);
        when(iter.getData()).thenReturn(ByteBuffer.wrap(data1), ByteBuffer.wrap(data2));
        when(iter.done()).thenReturn(null);
        when(iter.getIndex()).thenReturn(1L, 2L);

        // Apply the log entries
        stateMachine.onApply(iter);

        // Verify both configs were stored
        ConfigEntry entry1 = storage.getConfig("default", "key1");
        assertNotNull(entry1);
        assertEquals("test1", entry1.getProperty("value"));

        ConfigEntry entry2 = storage.getConfig("default", "key2");
        assertNotNull(entry2);
        assertEquals("test2", entry2.getProperty("value"));

        // Verify next() was called twice
        verify(iter, org.mockito.Mockito.times(2)).next();
    }

    @Test
    public void testOnSnapshotSaveEmptyStorage() throws Exception {
        // Create a new empty storage
        ConfigStorage emptyStorage = new MemoryConfigStorage();
        ConfigStateMachine emptyStateMachine = new ConfigStateMachine(emptyStorage);

        // Create snapshot directory
        File snapshotDir = tempFolder.newFolder("empty_snapshot");

        // Mock SnapshotWriter
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(writer.addFile(anyString())).thenReturn(true);

        // Save snapshot
        final CountDownLatch saveLatch = new CountDownLatch(1);
        final AtomicReference<Status> saveStatus = new AtomicReference<>();
        emptyStateMachine.onSnapshotSave(writer, new Closure() {
            @Override
            public void run(Status status) {
                saveStatus.set(status);
                saveLatch.countDown();
            }
        });

        // Wait for save to complete
        assertTrue(saveLatch.await(5, TimeUnit.SECONDS));
        assertTrue(saveStatus.get().isOk());

        // Verify snapshot file was created
        File snapshotFile = new File(snapshotDir, "data");
        assertTrue(snapshotFile.exists());
    }

    @Test
    public void testOnSnapshotSaveWriterAddFileFails() throws Exception {
        // Put a config
        Map<String, String> properties = new HashMap<>();
        properties.put("value", "test");
        ConfigEntry entry = new ConfigEntry("default", "test.key", properties);
        storage.putConfig("default", "test.key", entry);

        // Create snapshot directory
        File snapshotDir = tempFolder.newFolder("fail_snapshot");

        // Mock SnapshotWriter that fails to add file
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(writer.addFile(anyString())).thenReturn(false);

        // Save snapshot
        final CountDownLatch saveLatch = new CountDownLatch(1);
        final AtomicReference<Status> saveStatus = new AtomicReference<>();
        stateMachine.onSnapshotSave(writer, new Closure() {
            @Override
            public void run(Status status) {
                saveStatus.set(status);
                saveLatch.countDown();
            }
        });

        // Wait for save to complete
        assertTrue(saveLatch.await(5, TimeUnit.SECONDS));
        assertFalse(saveStatus.get().isOk());
    }

    @Test
    public void testOnSnapshotLoadEmptySnapshot() throws Exception {
        // Create a new storage and state machine
        ConfigStorage newStorage = new MemoryConfigStorage();
        ConfigStateMachine newStateMachine = new ConfigStateMachine(newStorage);

        // Create an empty snapshot
        File snapshotDir = tempFolder.newFolder("empty_load_snapshot");
        File snapshotFile = new File(snapshotDir, "data");

        // Save an empty snapshot
        ConfigStorage emptyStorage = new MemoryConfigStorage();
        try (FileOutputStream fos = new FileOutputStream(snapshotFile)) {
            emptyStorage.saveSnapshot(fos);
        }

        // Mock SnapshotReader
        SnapshotReader reader = mock(SnapshotReader.class);
        when(reader.getPath()).thenReturn(snapshotDir.getAbsolutePath());
        when(reader.getFileMeta(anyString())).thenReturn(LocalFileMeta.newBuilder().build());

        // Load snapshot
        boolean loaded = newStateMachine.onSnapshotLoad(reader);
        assertTrue(loaded);

        // Verify storage is still empty
        List<ConfigEntry> configs = newStorage.listConfigs("default");
        assertEquals(0, configs.size());
    }

    @Test
    public void testOnApplyGetConfigNotFound() throws Exception {
        // Create a GET_CONFIG request for non-existent config
        ConfigRequest request = ConfigRequest.createGetConfig("default", "nonexistent.key");

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response is an error
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMsg().contains("Config not found"));
    }

    @Test
    public void testOnApplyDeleteConfigNotFound() throws Exception {
        // Create a DELETE_CONFIG request for non-existent config
        ConfigRequest request = ConfigRequest.createDeleteConfig("default", "nonexistent.key");

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response is an error
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMsg().contains("Config not found"));
    }

    @Test
    public void testOnApplyRollbackConfigVersionNotFound() throws Exception {
        // Put a config
        Map<String, String> properties = new HashMap<>();
        properties.put("value", "v1");
        ConfigEntry entry = new ConfigEntry("default", "test.key", properties);
        storage.putConfig("default", "test.key", entry);

        // Create a ROLLBACK_CONFIG request to non-existent version
        ConfigRequest request = ConfigRequest.createRollbackConfig("default", "test.key", 999L);

        // Serialize the request
        byte[] data = SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(request);

        // Create a closure to capture the response
        final AtomicReference<ConfigResponse> responseRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConfigClosure closure = new ConfigClosure(request, new ConfigCallback() {
            @Override
            public void onSuccess(ConfigResponse response) {
                responseRef.set(response);
                latch.countDown();
            }

            @Override
            public void onError(String errorMsg) {
                latch.countDown();
            }
        });

        // Create a mock iterator with closure
        Iterator iter = createMockIterator(data, closure);

        // Apply the log entry
        stateMachine.onApply(iter);

        // Wait for callback
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Verify the response is an error
        ConfigResponse response = responseRef.get();
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertTrue(response.getErrorMsg().contains("Config version not found"));
    }

    /**
     * Create a mock iterator for testing
     */
    private Iterator createMockIterator(byte[] data, Closure closure) {
        Iterator iter = mock(Iterator.class);
        when(iter.hasNext()).thenReturn(true, false);
        when(iter.getData()).thenReturn(ByteBuffer.wrap(data));
        when(iter.done()).thenReturn(closure);
        when(iter.getIndex()).thenReturn(1L);
        return iter;
    }
}
