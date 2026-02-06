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

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.RaftGroupService;
import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.option.NodeOptions;

/**
 * Configuration center server that manages distributed configuration using Raft consensus.
 * Integrates Raft node and HTTP server to provide configuration management services.
 *
 * @author lei
 */
public class ConfigServer {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigServer.class);

    private RaftGroupService    raftGroupService;
    private Node                node;
    private ConfigStateMachine  fsm;
    private HttpServer          httpServer;
    private ConfigServerOptions options;

    /**
     * Start the configuration server with given options
     *
     * @param opts server options
     * @return true if started successfully, false otherwise
     */
    public boolean start(ConfigServerOptions opts) {
        Objects.requireNonNull(opts, "ConfigServerOptions cannot be null");

        try {
            // Validate options
            opts.validate();
            this.options = opts;

            LOG.info("Starting ConfigServer with options: {}", opts);

            // Initialize Raft data path
            final String dataPath = opts.getDataPath();
            FileUtils.forceMkdir(new File(dataPath));

            // Parse server ID
            final PeerId serverId = new PeerId();
            if (!serverId.parse(opts.getServerId())) {
                LOG.error("Failed to parse serverId: {}", opts.getServerId());
                return false;
            }

            // Parse initial configuration
            final Configuration initConf = new Configuration();
            if (!initConf.parse(opts.getInitialServerList())) {
                LOG.error("Failed to parse initialServerList: {}", opts.getInitialServerList());
                return false;
            }

            // Create node options
            final NodeOptions nodeOptions = new NodeOptions();

            // Set election timeout to 1s
            nodeOptions.setElectionTimeoutMs(1000);

            // Enable CLI service for cluster management
            nodeOptions.setDisableCli(false);

            // Do snapshot every 30s
            nodeOptions.setSnapshotIntervalSecs(30);

            // Set initial cluster configuration
            nodeOptions.setInitialConf(initConf);

            // Initialize state machine
            this.fsm = new ConfigStateMachine();
            nodeOptions.setFsm(this.fsm);

            // Set storage paths
            nodeOptions.setLogUri(dataPath + File.separator + "log");
            nodeOptions.setRaftMetaUri(dataPath + File.separator + "raft_meta");
            nodeOptions.setSnapshotUri(dataPath + File.separator + "snapshot");

            // Initialize Raft group service
            this.raftGroupService = new RaftGroupService(opts.getGroupId(), serverId, nodeOptions);

            // Start Raft node
            this.node = this.raftGroupService.start();

            if (this.node == null) {
                LOG.error("Failed to start Raft node");
                return false;
            }

            LOG.info("Raft node started successfully: {}", serverId);

            // Start HTTP server
            this.httpServer = new HttpServer(this.fsm, this.node, opts.getPort());
            this.httpServer.start();

            LOG.info("ConfigServer started successfully on port {}", opts.getPort());
            return true;

        } catch (IOException e) {
            LOG.error("Failed to start ConfigServer due to IO error", e);
            return false;
        } catch (Exception e) {
            LOG.error("Failed to start ConfigServer", e);
            return false;
        }
    }

    /**
     * Shutdown the configuration server
     */
    public void shutdown() {
        LOG.info("Shutting down ConfigServer");

        try {
            // Stop HTTP server first
            if (this.httpServer != null) {
                this.httpServer.stop();
                LOG.info("HTTP server stopped");
            }

            // Shutdown Raft group service
            if (this.raftGroupService != null) {
                this.raftGroupService.shutdown();
                LOG.info("Raft group service shutdown");
            }

            LOG.info("ConfigServer shutdown completed");

        } catch (Exception e) {
            LOG.error("Error during ConfigServer shutdown", e);
        }
    }

    /**
     * Get the Raft node
     *
     * @return Raft node
     */
    public Node getNode() {
        return this.node;
    }

    /**
     * Get the state machine
     *
     * @return configuration state machine
     */
    public ConfigStateMachine getFsm() {
        return this.fsm;
    }

    /**
     * Get the Raft group service
     *
     * @return Raft group service
     */
    public RaftGroupService getRaftGroupService() {
        return this.raftGroupService;
    }

    /**
     * Get the HTTP server
     *
     * @return HTTP server
     */
    public HttpServer getHttpServer() {
        return this.httpServer;
    }

    /**
     * Get server options
     *
     * @return server options
     */
    public ConfigServerOptions getOptions() {
        return this.options;
    }

    /**
     * Main method to start configuration server from command line
     *
     * @param args command line arguments: dataPath groupId serverId initialServerList port
     */
    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Usage: java com.alipay.sofa.jraft.example.configcenter.ConfigServer " +
                "{dataPath} {groupId} {serverId} {initialServerList} {port}");
            System.out.println("Example: java com.alipay.sofa.jraft.example.configcenter.ConfigServer " +
                "/tmp/config_server1 config_center 127.0.0.1:8081 127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083 8080");
            System.exit(1);
        }

        final String dataPath = args[0];
        final String groupId = args[1];
        final String serverId = args[2];
        final String initialServerList = args[3];
        final int port = Integer.parseInt(args[4]);

        final ConfigServerOptions opts = new ConfigServerOptions(dataPath, groupId, serverId, initialServerList, port);
        final ConfigServer server = new ConfigServer();

        if (server.start(opts)) {
            System.out.println("ConfigServer started successfully on port " + port);
            System.out.println("Server ID: " + serverId);
            System.out.println("Group ID: " + groupId);
            System.out.println("Initial cluster: " + initialServerList);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down ConfigServer...");
                server.shutdown();
            }));
            
            // Keep the main thread alive
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Main thread interrupted");
            }
        } else {
            System.err.println("Failed to start ConfigServer");
            System.exit(1);
        }
    }
}
