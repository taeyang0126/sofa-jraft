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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating how to use AppClient for reading configurations.
 * This example shows how to:
 * 1. Create and configure AppClient
 * 2. Read configurations with consistency guarantees (ReadIndex)
 * 3. Query configuration history and specific versions
 * 4. Monitor configuration changes with polling
 * 5. Handle configuration change events
 *
 * @author lei
 */
public class AppClientExample {

    /**
     * Example 1: Basic configuration reading with consistency
     */
    public static void basicReadExample(String serverUrl) {
        System.out.println("=== Example 1: Basic Configuration Reading ===\n");

        // First, create some test data using AdminClient
        AdminClientOptions adminOptions = new AdminClientOptions();
        adminOptions.setServerUrl(serverUrl);
        AdminClient adminClient = new AdminClient(adminOptions);

        try {
            // Create test configuration
            Map<String, String> properties = new HashMap<>();
            properties.put("db.host", "localhost");
            properties.put("db.port", "5432");
            properties.put("db.name", "myapp");
            properties.put("db.user", "admin");

            ConfigResponse response = adminClient.putConfig("production", "database", properties);
            if (response.isSuccess()) {
                System.out.println("✓ Test configuration created\n");
            }
        } finally {
            // Don't shutdown yet, we'll use it for cleanup
        }

        // Now use AppClient to read the configuration
        AppClientOptions appOptions = new AppClientOptions();
        appOptions.setServerUrl(serverUrl);
        appOptions.setTimeoutMs(5000);

        AppClient appClient = new AppClient(appOptions);

        try {
            // Read configuration with consistency guarantee (ReadIndex)
            System.out.println("1. Reading configuration with consistency guarantee...");
            ConfigResponse response = appClient.getConfig("production", "database");

            if (response.isSuccess() && response.getConfigEntry() != null) {
                ConfigEntry entry = response.getConfigEntry();
                System.out.println("   ✓ Configuration retrieved successfully");
                System.out.println("   Namespace: " + entry.getNamespace());
                System.out.println("   Key: " + entry.getKey());
                System.out.println("   Version: " + entry.getVersion());
                System.out.println("   Properties:");
                entry.getProperties().forEach((k, v) -> 
                    System.out.println("     " + k + " = " + v));
            } else {
                System.out.println("   ✗ Failed: " + response.getErrorMsg());
            }

            // Try reading non-existent configuration
            System.out.println("\n2. Reading non-existent configuration...");
            response = appClient.getConfig("production", "nonexistent");
            if (!response.isSuccess()) {
                System.out.println("   ✓ Correctly returned error: " + response.getErrorMsg());
            }

        } finally {
            appClient.shutdown();
            adminClient.deleteConfig("production", "database");
            adminClient.shutdown();
            System.out.println("\n✓ Clients shutdown\n");
        }
    }

    /**
     * Example 2: Version history and querying specific versions
     */
    public static void versionHistoryExample(String serverUrl) {
        System.out.println("=== Example 2: Version History and Specific Versions ===\n");

        // Setup test data
        AdminClientOptions adminOptions = new AdminClientOptions();
        adminOptions.setServerUrl(serverUrl);
        AdminClient adminClient = new AdminClient(adminOptions);

        try {
            String namespace = "production";
            String key = "feature-flags";

            // Create version 1
            System.out.println("Creating configuration versions...");
            Map<String, String> v1 = new HashMap<>();
            v1.put("feature.newUI", "false");
            v1.put("feature.betaAPI", "false");
            ConfigResponse response = adminClient.putConfig(namespace, key, v1);
            long version1 = response.getConfigEntry().getVersion();
            System.out.println("  ✓ Version " + version1 + " created");

            // Create version 2
            Map<String, String> v2 = new HashMap<>();
            v2.put("feature.newUI", "true");
            v2.put("feature.betaAPI", "false");
            response = adminClient.putConfig(namespace, key, v2);
            long version2 = response.getConfigEntry().getVersion();
            System.out.println("  ✓ Version " + version2 + " created");

            // Create version 3
            Map<String, String> v3 = new HashMap<>();
            v3.put("feature.newUI", "true");
            v3.put("feature.betaAPI", "true");
            response = adminClient.putConfig(namespace, key, v3);
            long version3 = response.getConfigEntry().getVersion();
            System.out.println("  ✓ Version " + version3 + " created\n");

            // Now use AppClient to query versions
            AppClientOptions appOptions = new AppClientOptions();
            appOptions.setServerUrl(serverUrl);
            AppClient appClient = new AppClient(appOptions);

            try {
                // Get current version
                System.out.println("1. Reading current version...");
                response = appClient.getConfig(namespace, key);
                if (response.isSuccess()) {
                    System.out.println("   ✓ Current version: " + response.getConfigEntry().getVersion());
                    System.out.println("   Properties: " + response.getConfigEntry().getProperties());
                }

                // Get specific version
                System.out.println("\n2. Reading specific version (" + version1 + ")...");
                response = appClient.getConfigByVersion(namespace, key, version1);
                if (response.isSuccess()) {
                    System.out.println("   ✓ Version " + version1 + " retrieved");
                    System.out.println("   Properties: " + response.getConfigEntry().getProperties());
                }

                // Get configuration history
                System.out.println("\n3. Reading configuration history...");
                response = appClient.getConfigHistory(namespace, key);
                if (response.isSuccess() && response.getConfigEntries() != null) {
                    System.out.println("   ✓ Found " + response.getConfigEntries().size() + " version(s):");
                    for (ConfigEntry entry : response.getConfigEntries()) {
                        System.out.println("     Version " + entry.getVersion() + ": " + entry.getProperties());
                    }
                }

            } finally {
                appClient.shutdown();
            }

        } finally {
            adminClient.deleteConfig("production", "feature-flags");
            adminClient.shutdown();
            System.out.println("\n✓ Clients shutdown\n");
        }
    }

    /**
     * Example 3: Configuration change monitoring with polling
     */
    public static void pollingExample(String serverUrl) throws InterruptedException {
        System.out.println("=== Example 3: Configuration Change Monitoring ===\n");

        // Setup test data
        AdminClientOptions adminOptions = new AdminClientOptions();
        adminOptions.setServerUrl(serverUrl);
        AdminClient adminClient = new AdminClient(adminOptions);

        AppClientOptions appOptions = new AppClientOptions();
        appOptions.setServerUrl(serverUrl);
        appOptions.setPollingIntervalMs(2000); // Poll every 2 seconds
        AppClient appClient = new AppClient(appOptions);

        try {
            String namespace = "production";
            String key = "app-config";

            // Create initial configuration
            System.out.println("1. Creating initial configuration...");
            Map<String, String> properties = new HashMap<>();
            properties.put("log.level", "INFO");
            properties.put("cache.enabled", "true");

            ConfigResponse response = adminClient.putConfig(namespace, key, properties);
            if (response.isSuccess()) {
                System.out.println("   ✓ Initial configuration created\n");
            }

            // Start polling with change listener
            System.out.println("2. Starting configuration polling...");
            final CountDownLatch changeLatch = new CountDownLatch(2); // Wait for 2 changes

            appClient.startPolling(namespace, key, new ConfigChangeListener() {
                @Override
                public void onConfigChanged(String ns, String k, ConfigEntry oldEntry, ConfigEntry newEntry) {
                    System.out.println("\n   ⚡ Configuration changed!");
                    System.out.println("   Namespace: " + ns + ", Key: " + k);

                    if (oldEntry == null) {
                        System.out.println("   Event: Configuration created");
                        System.out.println("   New properties: " + newEntry.getProperties());
                    } else if (newEntry == null) {
                        System.out.println("   Event: Configuration deleted");
                        System.out.println("   Old properties: " + oldEntry.getProperties());
                    } else {
                        System.out.println("   Event: Configuration updated");
                        System.out.println("   Old version: " + oldEntry.getVersion());
                        System.out.println("   New version: " + newEntry.getVersion());
                        System.out.println("   Old properties: " + oldEntry.getProperties());
                        System.out.println("   New properties: " + newEntry.getProperties());
                    }

                    changeLatch.countDown();
                }

                @Override
                public void onError(String ns, String k, String errorMsg) {
                    System.out.println("\n   ✗ Error monitoring " + ns + ":" + k + " - " + errorMsg);
                }
            });

            System.out.println("   ✓ Polling started (interval: 2s)\n");

            // Wait a bit for initial poll
            Thread.sleep(3000);

            // Make first change
            System.out.println("3. Making first configuration change...");
            properties.put("log.level", "DEBUG");
            adminClient.putConfig(namespace, key, properties);
            System.out.println("   ✓ Configuration updated\n");

            // Wait for change to be detected
            Thread.sleep(3000);

            // Make second change
            System.out.println("4. Making second configuration change...");
            properties.put("cache.enabled", "false");
            properties.put("cache.ttl", "3600");
            adminClient.putConfig(namespace, key, properties);
            System.out.println("   ✓ Configuration updated\n");

            // Wait for changes to be detected
            if (changeLatch.await(10, TimeUnit.SECONDS)) {
                System.out.println("5. All changes detected successfully!\n");
            } else {
                System.out.println("5. Timeout waiting for changes\n");
            }

            // Stop polling
            System.out.println("6. Stopping polling...");
            appClient.stopPolling(namespace, key);
            System.out.println("   ✓ Polling stopped\n");

        } finally {
            appClient.shutdown();
            adminClient.deleteConfig("production", "app-config");
            adminClient.shutdown();
            System.out.println("✓ Clients shutdown\n");
        }
    }

    /**
     * Example 4: Multiple configuration monitoring
     */
    public static void multiplePollingExample(String serverUrl) throws InterruptedException {
        System.out.println("=== Example 4: Multiple Configuration Monitoring ===\n");

        AdminClientOptions adminOptions = new AdminClientOptions();
        adminOptions.setServerUrl(serverUrl);
        AdminClient adminClient = new AdminClient(adminOptions);

        AppClientOptions appOptions = new AppClientOptions();
        appOptions.setServerUrl(serverUrl);
        appOptions.setPollingIntervalMs(2000);
        AppClient appClient = new AppClient(appOptions);

        try {
            // Create multiple configurations
            System.out.println("1. Creating multiple configurations...");

            Map<String, String> dbConfig = new HashMap<>();
            dbConfig.put("host", "localhost");
            dbConfig.put("port", "5432");
            adminClient.putConfig("production", "database", dbConfig);

            Map<String, String> cacheConfig = new HashMap<>();
            cacheConfig.put("host", "localhost");
            cacheConfig.put("port", "6379");
            adminClient.putConfig("production", "cache", cacheConfig);

            System.out.println("   ✓ Configurations created\n");

            // Monitor multiple configurations
            System.out.println("2. Starting monitoring for multiple configurations...");
            final CountDownLatch changeLatch = new CountDownLatch(2);

            ConfigChangeListener listener = new ConfigChangeListener() {
                @Override
                public void onConfigChanged(String ns, String k, ConfigEntry oldEntry, ConfigEntry newEntry) {
                    System.out.println("\n   ⚡ Change detected: " + ns + ":" + k);
                    if (newEntry != null) {
                        System.out.println("   Version: " + newEntry.getVersion());
                        System.out.println("   Properties: " + newEntry.getProperties());
                    }
                    changeLatch.countDown();
                }

                @Override
                public void onError(String ns, String k, String errorMsg) {
                    System.out.println("\n   ✗ Error: " + ns + ":" + k + " - " + errorMsg);
                }
            };

            appClient.startPolling("production", "database", listener);
            appClient.startPolling("production", "cache", listener);
            System.out.println("   ✓ Monitoring started for 2 configurations\n");

            // Wait for initial polls
            Thread.sleep(3000);

            // Update both configurations
            System.out.println("3. Updating configurations...");
            dbConfig.put("port", "5433");
            adminClient.putConfig("production", "database", dbConfig);

            cacheConfig.put("port", "6380");
            adminClient.putConfig("production", "cache", cacheConfig);
            System.out.println("   ✓ Configurations updated\n");

            // Wait for changes
            if (changeLatch.await(10, TimeUnit.SECONDS)) {
                System.out.println("4. All changes detected!\n");
            } else {
                System.out.println("4. Timeout waiting for changes\n");
            }

            // Stop all polling
            System.out.println("5. Stopping all polling...");
            appClient.stopAllPolling();
            System.out.println("   ✓ All polling stopped\n");

        } finally {
            appClient.shutdown();
            adminClient.deleteConfig("production", "database");
            adminClient.deleteConfig("production", "cache");
            adminClient.shutdown();
            System.out.println("✓ Clients shutdown\n");
        }
    }

    /**
     * Example 5: Simulating a real application using configuration
     */
    public static void realWorldExample(String serverUrl) throws InterruptedException {
        System.out.println("=== Example 5: Real-World Application Scenario ===\n");

        AdminClientOptions adminOptions = new AdminClientOptions();
        adminOptions.setServerUrl(serverUrl);
        AdminClient adminClient = new AdminClient(adminOptions);

        AppClientOptions appOptions = new AppClientOptions();
        appOptions.setServerUrl(serverUrl);
        appOptions.setPollingIntervalMs(3000);
        AppClient appClient = new AppClient(appOptions);

        try {
            // Setup initial application configuration
            System.out.println("1. Setting up application configuration...");
            Map<String, String> appConfig = new HashMap<>();
            appConfig.put("max.connections", "100");
            appConfig.put("timeout.seconds", "30");
            appConfig.put("retry.attempts", "3");
            appConfig.put("feature.newAlgorithm", "false");

            adminClient.putConfig("production", "app-settings", appConfig);
            System.out.println("   ✓ Initial configuration set\n");

            // Application reads configuration on startup
            System.out.println("2. Application starting up...");
            ConfigResponse response = appClient.getConfig("production", "app-settings");
            if (response.isSuccess()) {
                ConfigEntry config = response.getConfigEntry();
                System.out.println("   ✓ Configuration loaded:");
                System.out.println("     Max connections: " + config.getProperty("max.connections"));
                System.out.println("     Timeout: " + config.getProperty("timeout.seconds") + "s");
                System.out.println("     Retry attempts: " + config.getProperty("retry.attempts"));
                System.out.println("     New algorithm: " + config.getProperty("feature.newAlgorithm"));
            }

            // Start monitoring for configuration changes
            System.out.println("\n3. Starting configuration monitoring...");
            appClient.startPolling("production", "app-settings", new ConfigChangeListener() {
                @Override
                public void onConfigChanged(String ns, String k, ConfigEntry oldEntry, ConfigEntry newEntry) {
                    System.out.println("\n   ⚡ Configuration updated! Reloading application settings...");
                    if (newEntry != null) {
                        System.out.println("   New settings:");
                        newEntry.getProperties().forEach((key, value) -> 
                            System.out.println("     " + key + " = " + value));
                        System.out.println("   ✓ Application settings reloaded");
                    }
                }

                @Override
                public void onError(String ns, String k, String errorMsg) {
                    System.out.println("\n   ✗ Failed to reload configuration: " + errorMsg);
                }
            });
            System.out.println("   ✓ Monitoring active\n");

            // Simulate application running
            System.out.println("4. Application running...");
            Thread.sleep(4000);

            // Operations team updates configuration
            System.out.println("\n5. Operations team updating configuration...");
            appConfig.put("max.connections", "200"); // Increase capacity
            appConfig.put("feature.newAlgorithm", "true"); // Enable new feature
            adminClient.putConfig("production", "app-settings", appConfig);
            System.out.println("   ✓ Configuration updated by ops team\n");

            // Wait for application to detect and reload
            System.out.println("6. Waiting for application to detect changes...");
            Thread.sleep(5000);

            // Verify new configuration is active
            System.out.println("\n7. Verifying active Associate...");
            response = appClient.getConfig("production", "app-settings");
            if (response.isSuccess()) {
                ConfigEntry config = response.getConfigEntry();
                System.out.println("   ✓ Current active configuration:");
                System.out.println("     Max connections: " + config.getProperty("max.connections"));
                System.out.println("     New algorithm: " + config.getProperty("feature.newAlgorithm"));
            }

            System.out.println("\n8. Application continues running with new configuration...\n");

        } finally {
            appClient.shutdown();
            adminClient.deleteConfig("production", "app-settings");
            adminClient.shutdown();
            System.out.println("✓ Application shutdown complete\n");
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

        System.out.println("AppClient Example");
        System.out.println("Server URL: " + serverUrl);
        System.out.println("============================================================\n");

        // Run examples
        basicReadExample(serverUrl);
        Thread.sleep(1000);

        versionHistoryExample(serverUrl);
        Thread.sleep(1000);

        pollingExample(serverUrl);
        Thread.sleep(1000);

        multiplePollingExample(serverUrl);
        Thread.sleep(1000);

        realWorldExample(serverUrl);

        System.out.println("============================================================");
        System.out.println("All examples completed!");
    }
}
