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

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating how to use AdminClient for configuration management.
 * This example shows how to:
 * 1. Create and configure AdminClient
 * 2. Put (create/update) configuration entries
 * 3. Delete configuration entries
 * 4. List configurations in a namespace
 * 5. Rollback configurations to previous versions
 * 6. Use synchronous and asynchronous APIs
 *
 * @author lei
 */
public class AdminClientExample {

    /**
     * Example 1: Basic CRUD operations using synchronous API
     */
    public static void basicCrudExample(String serverUrl) {
        System.out.println("=== Example 1: Basic CRUD Operations ===\n");

        // Create AdminClient with default options
        AdminClientOptions options = new AdminClientOptions();
        options.setServerUrl(serverUrl);
        options.setTimeoutMs(5000);
        options.setMaxRetries(3);

        AdminClient client = new AdminClient(options);

        try {
            // 1. Put a configuration entry
            System.out.println("1. Creating configuration entry...");
            Map<String, String> properties = new HashMap<>();
            properties.put("db.host", "localhost");
            properties.put("db.port", "3306");
            properties.put("db.name", "myapp");

            ConfigResponse response = client.putConfig("production", "database", properties);
            if (response.isSuccess()) {
                System.out.println("   ✓ Configuration created successfully");
                System.out.println("   Version: " + response.getConfigEntry().getVersion());
            } else {
                System.out.println("   ✗ Failed: " + response.getErrorMsg());
            }

            // 2. Update the configuration
            System.out.println("\n2. Updating configuration entry...");
            properties.put("db.port", "3307"); // Change port
            properties.put("db.maxConnections", "100"); // Add new property

            response = client.putConfig("production", "database", properties);
            if (response.isSuccess()) {
                System.out.println("   ✓ Configuration updated successfully");
                System.out.println("   New version: " + response.getConfigEntry().getVersion());
            } else {
                System.out.println("   ✗ Failed: " + response.getErrorMsg());
            }

            // 3. List all configurations in namespace
            System.out.println("\n3. Listing all configurations in 'production' namespace...");
            response = client.listConfigs("production");
            if (response.isSuccess() && response.getConfigEntries() != null) {
                System.out.println("   ✓ Found " + response.getConfigEntries().size() + " configuration(s):");
                for (ConfigEntry entry : response.getConfigEntries()) {
                    System.out.println("     - " + entry.getKey() + " (version: " + entry.getVersion() + ")");
                    System.out.println("       Properties: " + entry.getProperties());
                }
            } else {
                System.out.println("   ✗ Failed: " + response.getErrorMsg());
            }

            // 4. Delete the configuration
            System.out.println("\n4. Deleting configuration entry...");
            response = client.deleteConfig("production", "database");
            if (response.isSuccess()) {
                System.out.println("   ✓ Configuration deleted successfully");
            } else {
                System.out.println("   ✗ Failed: " + response.getErrorMsg());
            }

        } finally {
            client.shutdown();
            System.out.println("\n✓ AdminClient shutdown\n");
        }
    }

    /**
     * Example 2: Version management and rollback
     */
    public static void versionManagementExample(String serverUrl) {
        System.out.println("=== Example 2: Version Management and Rollback ===\n");

        AdminClientOptions options = new AdminClientOptions();
        options.setServerUrl(serverUrl);

        AdminClient client = new AdminClient(options);

        try {
            String namespace = "production";
            String key = "app-config";

            // Create initial configuration (version 1)
            System.out.println("1. Creating initial configuration (version 1)...");
            Map<String, String> properties = new HashMap<>();
            properties.put("feature.newUI", "false");
            properties.put("feature.betaAPI", "false");

            ConfigResponse response = client.putConfig(namespace, key, properties);
            long version1 = response.isSuccess() ? response.getConfigEntry().getVersion() : 0;
            System.out.println("   ✓ Created version: " + version1);

            // Update to version 2
            System.out.println("\n2. Updating configuration (version 2)...");
            properties.put("feature.newUI", "true");
            response = client.putConfig(namespace, key, properties);
            long version2 = response.isSuccess() ? response.getConfigEntry().getVersion() : 0;
            System.out.println("   ✓ Updated to version: " + version2);

            // Update to version 3
            System.out.println("\n3. Updating configuration (version 3)...");
            properties.put("feature.betaAPI", "true");
            response = client.putConfig(namespace, key, properties);
            long version3 = response.isSuccess() ? response.getConfigEntry().getVersion() : 0;
            System.out.println("   ✓ Updated to version: " + version3);
            System.out.println("   Current properties: " + properties);

            // Rollback to version 1
            System.out.println("\n4. Rolling back to version " + version1 + "...");
            response = client.rollbackConfig(namespace, key, version1);
            if (response.isSuccess()) {
                System.out.println("   ✓ Rollback successful");
                System.out.println("   New version: " + response.getConfigEntry().getVersion());
                System.out.println("   Restored properties: " + response.getConfigEntry().getProperties());
            } else {
                System.out.println("   ✗ Failed: " + response.getErrorMsg());
            }

            // Cleanup
            client.deleteConfig(namespace, key);

        } finally {
            client.shutdown();
            System.out.println("\n✓ AdminClient shutdown\n");
        }
    }

    /**
     * Example 3: Asynchronous operations
     */
    public static void asyncOperationsExample(String serverUrl) throws InterruptedException {
        System.out.println("=== Example 3: Asynchronous Operations ===\n");

        AdminClientOptions options = new AdminClientOptions();
        options.setServerUrl(serverUrl);

        AdminClient client = new AdminClient(options);

        try {
            final CountDownLatch latch = new CountDownLatch(3);

            // Async put operation
            System.out.println("1. Creating configurations asynchronously...");

            Map<String, String> dbConfig = new HashMap<>();
            dbConfig.put("host", "localhost");
            dbConfig.put("port", "5432");

            client.putConfigAsync("production", "postgres", dbConfig, new ConfigCallback() {
                @Override
                public void onSuccess(ConfigResponse response) {
                    System.out.println("   ✓ postgres config created (version: "
                                       + response.getConfigEntry().getVersion() + ")");
                    latch.countDown();
                }

                @Override
                public void onError(String errorMsg) {
                    System.out.println("   ✗ postgres config failed: " + errorMsg);
                    latch.countDown();
                }
            });

            Map<String, String> redisConfig = new HashMap<>();
            redisConfig.put("host", "localhost");
            redisConfig.put("port", "6379");

            client.putConfigAsync("production", "redis", redisConfig, new ConfigCallback() {
                @Override
                public void onSuccess(ConfigResponse response) {
                    System.out.println("   ✓ redis config created (version: " + response.getConfigEntry().getVersion()
                                       + ")");
                    latch.countDown();
                }

                @Override
                public void onError(String errorMsg) {
                    System.out.println("   ✗ redis config failed: " + errorMsg);
                    latch.countDown();
                }
            });

            Map<String, String> kafkaConfig = new HashMap<>();
            kafkaConfig.put("brokers", "localhost:9092");
            kafkaConfig.put("topic", "events");

            client.putConfigAsync("production", "kafka", kafkaConfig, new ConfigCallback() {
                @Override
                public void onSuccess(ConfigResponse response) {
                    System.out.println("   ✓ kafka config created (version: " + response.getConfigEntry().getVersion()
                                       + ")");
                    latch.countDown();
                }

                @Override
                public void onError(String errorMsg) {
                    System.out.println("   ✗ kafka config failed: " + errorMsg);
                    latch.countDown();
                }
            });

            // Wait for all async operations to complete
            if (latch.await(10, TimeUnit.SECONDS)) {
                System.out.println("\n✓ All async operations completed");
            } else {
                System.out.println("\n✗ Timeout waiting for async operations");
            }

            // Cleanup
            Thread.sleep(500); // Give time for operations to complete
            client.deleteConfig("production", "postgres");
            client.deleteConfig("production", "redis");
            client.deleteConfig("production", "kafka");

        } finally {
            client.shutdown();
            System.out.println("\n✓ AdminClient shutdown\n");
        }
    }

    /**
     * Example 4: Interactive configuration management
     */
    public static void interactiveExample(String serverUrl) {
        System.out.println("=== Example 4: Interactive Configuration Management ===\n");

        AdminClientOptions options = new AdminClientOptions();
        options.setServerUrl(serverUrl);

        AdminClient client = new AdminClient(options);
        Scanner scanner = new Scanner(System.in);

        try {
            boolean running = true;

            while (running) {
                System.out.println("\nConfiguration Management Menu:");
                System.out.println("1. Put configuration");
                System.out.println("2. Delete configuration");
                System.out.println("3. List configurations");
                System.out.println("4. Rollback configuration");
                System.out.println("5. Exit");
                System.out.print("\nSelect option: ");

                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        handlePutConfig(client, scanner);
                        break;
                    case "2":
                        handleDeleteConfig(client, scanner);
                        break;
                    case "3":
                        handleListConfigs(client, scanner);
                        break;
                    case "4":
                        handleRollbackConfig(client, scanner);
                        break;
                    case "5":
                        running = false;
                        System.out.println("Exiting...");
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            }

        } finally {
            scanner.close();
            client.shutdown();
            System.out.println("\n✓ AdminClient shutdown\n");
        }
    }

    private static void handlePutConfig(AdminClient client, Scanner scanner) {
        System.out.print("Enter namespace: ");
        String namespace = scanner.nextLine().trim();

        System.out.print("Enter key: ");
        String key = scanner.nextLine().trim();

        Map<String, String> properties = new HashMap<>();
        System.out.println("Enter properties (empty line to finish):");

        while (true) {
            System.out.print("  Property key (or press Enter to finish): ");
            String propKey = scanner.nextLine().trim();
            if (propKey.isEmpty()) {
                break;
            }

            System.out.print("  Property value: ");
            String propValue = scanner.nextLine().trim();

            properties.put(propKey, propValue);
        }

        if (properties.isEmpty()) {
            System.out.println("✗ No properties entered");
            return;
        }

        ConfigResponse response = client.putConfig(namespace, key, properties);
        if (response.isSuccess()) {
            System.out.println("✓ Configuration saved successfully");
            System.out.println("  Version: " + response.getConfigEntry().getVersion());
        } else {
            System.out.println("✗ Failed: " + response.getErrorMsg());
        }
    }

    private static void handleDeleteConfig(AdminClient client, Scanner scanner) {
        System.out.print("Enter namespace: ");
        String namespace = scanner.nextLine().trim();

        System.out.print("Enter key: ");
        String key = scanner.nextLine().trim();

        ConfigResponse response = client.deleteConfig(namespace, key);
        if (response.isSuccess()) {
            System.out.println("✓ Configuration deleted successfully");
        } else {
            System.out.println("✗ Failed: " + response.getErrorMsg());
        }
    }

    private static void handleListConfigs(AdminClient client, Scanner scanner) {
        System.out.print("Enter namespace: ");
        String namespace = scanner.nextLine().trim();

        ConfigResponse response = client.listConfigs(namespace);
        if (response.isSuccess() && response.getConfigEntries() != null) {
            System.out.println("✓ Found " + response.getConfigEntries().size() + " configuration(s):");
            for (ConfigEntry entry : response.getConfigEntries()) {
                System.out.println("\n  Key: " + entry.getKey());
                System.out.println("  Version: " + entry.getVersion());
                System.out.println("  Properties:");
                entry.getProperties().forEach((k, v) -> 
                    System.out.println("    " + k + " = " + v));
            }
        } else {
            System.out.println("✗ Failed: " + response.getErrorMsg());
        }
    }

    private static void handleRollbackConfig(AdminClient client, Scanner scanner) {
        System.out.print("Enter namespace: ");
        String namespace = scanner.nextLine().trim();

        System.out.print("Enter key: ");
        String key = scanner.nextLine().trim();

        System.out.print("Enter version to rollback to: ");
        String versionStr = scanner.nextLine().trim();

        try {
            long version = Long.parseLong(versionStr);
            ConfigResponse response = client.rollbackConfig(namespace, key, version);
            if (response.isSuccess()) {
                System.out.println("✓ Configuration rolled back successfully");
                System.out.println("  New version: " + response.getConfigEntry().getVersion());
                System.out.println("  Properties: " + response.getConfigEntry().getProperties());
            } else {
                System.out.println("✗ Failed: " + response.getErrorMsg());
            }
        } catch (NumberFormatException e) {
            System.out.println("✗ Invalid version number");
        }
    }

    /**
     * Main method to run the examples.
     */
    public static void main(String[] args) throws InterruptedException {
        String serverUrl = "http://127.0.0.1:8080";

        if (args.length > 0) {
            serverUrl = args[0];
        }

        System.out.println("AdminClient Example");
        System.out.println("Server URL: " + serverUrl);
        System.out.println("============================================================\n");

        // Run examples
        basicCrudExample(serverUrl);
        Thread.sleep(1000);

        versionManagementExample(serverUrl);
        Thread.sleep(1000);

        asyncOperationsExample(serverUrl);
        Thread.sleep(1000);

        // Uncomment to run interactive example
        // interactiveExample(serverUrl);

        System.out.println("============================================================");
        System.out.println("All examples completed!");
        System.out.println("\nTo run interactive mode:");
        System.out.println("  Uncomment interactiveExample() in main method");
    }
}
