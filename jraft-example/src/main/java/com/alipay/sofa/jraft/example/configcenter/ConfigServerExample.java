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
 * Example demonstrating how to start a configuration center server.
 * This example shows how to:
 * 1. Configure server options
 * 2. Start a single server node
 * 3. Start a cluster of server nodes
 * 4. Handle graceful shutdown
 *
 * @author lei
 */
public class ConfigServerExample {

    /**
     * Start a single configuration server node.
     * This is useful for development and testing.
     *
     * Usage:
     * java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample single
     */
    public static void startSingleNode() {
        System.out.println("=== Starting Single Node Configuration Server ===\n");

        // Configure server options
        ConfigServerOptions opts = new ConfigServerOptions(
            "/tmp/config_server_single",           // dataPath - where to store Raft logs and snapshots
            "config_center",                       // groupId - Raft group identifier
            "127.0.0.1:8081",                      // serverId - this server's address
            "127.0.0.1:8081",                      // initialServerList - cluster members
            8080                                   // port - HTTP server port
        );

        // Create and start server
        ConfigServer server = new ConfigServer();
        if (server.start(opts)) {
            System.out.println("✓ Configuration server started successfully!");
            System.out.println("  - Server ID: " + opts.getServerId());
            System.out.println("  - HTTP Port: " + opts.getPort());
            System.out.println("  - Data Path: " + opts.getDataPath());
            System.out.println("  - Group ID: " + opts.getGroupId());
            System.out.println("\nServer is ready to accept requests at: http://127.0.0.1:8080");
            System.out.println("Press Ctrl+C to shutdown\n");

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n=== Shutting down server ===");
                server.shutdown();
                System.out.println("✓ Server shutdown complete");
            }));

            // Keep main thread alive
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            System.err.println("✗ Failed to start configuration server");
            System.exit(1);
        }
    }

    /**
     * Start a cluster of three configuration server nodes.
     * This demonstrates high availability setup.
     *
     * Usage:
     * Terminal 1: java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 1
     * Terminal 2: java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 2
     * Terminal 3: java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 3
     */
    public static void startClusterNode(int nodeId) {
        if (nodeId < 1 || nodeId > 3) {
            System.err.println("Node ID must be 1, 2, or 3");
            System.exit(1);
        }

        System.out.println("=== Starting Configuration Server Node " + nodeId + " ===\n");

        // Define cluster configuration
        String[] serverIds = {
            "127.0.0.1:8081",
            "127.0.0.1:8082",
            "127.0.0.1:8083"
        };

        int[] httpPorts = { 8080, 8180, 8280 };

        String initialServerList = String.join(",", serverIds);
        String serverId = serverIds[nodeId - 1];
        int httpPort = httpPorts[nodeId - 1];
        String dataPath = "/tmp/config_server_node" + nodeId;

        // Configure server options
        ConfigServerOptions opts = new ConfigServerOptions(
            dataPath,
            "config_center",
            serverId,
            initialServerList,
            httpPort
        );

        // Create and start server
        ConfigServer server = new ConfigServer();
        if (server.start(opts)) {
            System.out.println("✓ Configuration server node " + nodeId + " started successfully!");
            System.out.println("  - Server ID: " + serverId);
            System.out.println("  - HTTP Port: " + httpPort);
            System.out.println("  - Data Path: " + dataPath);
            System.out.println("  - Cluster: " + initialServerList);
            System.out.println("\nNode " + nodeId + " is ready at: http://127.0.0.1:" + httpPort);
            System.out.println("Press Ctrl+C to shutdown\n");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n=== Shutting down node " + nodeId + " ===");
                server.shutdown();
                System.out.println("✓ Node " + nodeId + " shutdown complete");
            }));

            // Keep main thread alive
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            System.err.println("✗ Failed to start configuration server node " + nodeId);
            System.exit(1);
        }
    }

    /**
     * Main method to run the example.
     *
     * Usage:
     * - Single node: java ConfigServerExample single
     * - Cluster node: java ConfigServerExample cluster <nodeId>
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String mode = args[0];

        switch (mode) {
            case "single":
                startSingleNode();
                break;

            case "cluster":
                if (args.length < 2) {
                    System.err.println("Error: Node ID required for cluster mode");
                    printUsage();
                    System.exit(1);
                }
                try {
                    int nodeId = Integer.parseInt(args[1]);
                    startClusterNode(nodeId);
                } catch (NumberFormatException e) {
                    System.err.println("Error: Invalid node ID: " + args[1]);
                    System.exit(1);
                }
                break;

            default:
                System.err.println("Error: Unknown mode: " + mode);
                printUsage();
                System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Configuration Server Example");
        System.out.println("\nUsage:");
        System.out.println("  Single node mode:");
        System.out.println("    java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample single");
        System.out.println("\n  Cluster mode (start 3 nodes in separate terminals):");
        System.out.println("    java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 1");
        System.out.println("    java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 2");
        System.out.println("    java com.alipay.sofa.jraft.example.configcenter.ConfigServerExample cluster 3");
        System.out.println("\nExamples:");
        System.out.println("  # Start a single node for development");
        System.out.println("  $ java ConfigServerExample single");
        System.out.println("\n  # Start a 3-node cluster for production");
        System.out.println("  Terminal 1: $ java ConfigServerExample cluster 1");
        System.out.println("  Terminal 2: $ java ConfigServerExample cluster 2");
        System.out.println("  Terminal 3: $ java ConfigServerExample cluster 3");
    }
}
