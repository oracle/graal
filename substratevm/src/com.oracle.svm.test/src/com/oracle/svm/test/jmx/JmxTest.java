/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2025, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.test.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.rmi.ssl.SslRMIClientSocketFactory;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.jdk.management.ManagementAgentStartupHook;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.test.AddExports;

import jdk.management.jfr.FlightRecorderMXBean;

@AddExports("jdk.management.agent/jdk.internal.agent")
public class JmxTest {
    static final String PORT_PROPERTY = "com.sun.management.jmxremote.port";
    static final String RMI_PORT_PROPERTY = "com.sun.management.jmxremote.rmi.port";
    static final String AUTH_PROPERTY = "com.sun.management.jmxremote.authenticate";
    static final String CLIENT_AUTH_PROPERTY = "com.sun.management.jmxremote.ssl.need.client.auth";
    static final String ACCESS_PROPERTY = "com.sun.management.jmxremote.access.file";
    static final String PASSWORD_PROPERTY = "com.sun.management.jmxremote.password.file";
    static final String SSL_PROPERTY = "com.sun.management.jmxremote.ssl";
    static final String KEYSTORE_FILENAME = "clientkeystore";
    static final String KEYSTORE_PASSWORD = "clientpass";
    static final String KEYSTORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    static final String KEYSTORE_PROPERTY = "javax.net.ssl.keyStore";
    static final String TRUSTSTORE_FILENAME = "servertruststore";
    static final String TRUSTSTORE_PASSWORD = "servertrustpass";
    static final String TRUSTSTORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    static final String TRUSTSTORE_PROPERTY = "javax.net.ssl.trustStore";
    static final String REGISTRY_SSL_PROPERTY = "com.sun.management.jmxremote.registry.ssl";
    static final String SOCKET_FACTORY_PROPERTY = "com.sun.jndi.rmi.factory.socket";
    static final String TEST_PORT = "12345";
    static final String TEST_ROLE = "myTestRole";
    static final String TEST_ROLE_PASSWORD = "MYTESTP@SSWORD";
    static final String TRUE = "true";

    private static Path tempDirectory;

    @BeforeClass
    public static void setup() throws Exception {
        assumeTrue("skipping JMX tests", !ImageInfo.inImageCode() ||
                        (VMInspectionOptions.hasJmxClientSupport() && VMInspectionOptions.hasJmxServerSupport()));

        System.setProperty(PORT_PROPERTY, TEST_PORT);
        System.setProperty(RMI_PORT_PROPERTY, TEST_PORT);
        System.setProperty(AUTH_PROPERTY, TRUE);
        System.setProperty(CLIENT_AUTH_PROPERTY, TRUE);
        System.setProperty(SSL_PROPERTY, TRUE);
        System.setProperty(REGISTRY_SSL_PROPERTY, TRUE);

        // Prepare temp directory with files required for testing authentication.
        tempDirectory = Files.createTempDirectory("jmxtest");
        Path jmxRemoteAccess = tempDirectory.resolve("jmxremote.access");
        Path jmxRemotePassword = tempDirectory.resolve("jmxremote.password");
        Path clientKeyStore = tempDirectory.resolve(KEYSTORE_FILENAME);
        Path serverTrustStore = tempDirectory.resolve(TRUSTSTORE_FILENAME);

        // Generate SSL keystore, client cert, and truststore for testing SSL connection.
        createClientKey();
        createClientCert();
        assertTrue("Failed to create " + KEYSTORE_FILENAME, Files.exists(clientKeyStore));
        System.setProperty(KEYSTORE_PROPERTY, clientKeyStore.toString());
        System.setProperty(KEYSTORE_PASSWORD_PROPERTY, KEYSTORE_PASSWORD);
        createServerTrustStore();
        assertTrue("Failed to create " + TRUSTSTORE_FILENAME, Files.exists(serverTrustStore));
        System.setProperty(TRUSTSTORE_PROPERTY, serverTrustStore.toString());
        System.setProperty(TRUSTSTORE_PASSWORD_PROPERTY, TRUSTSTORE_PASSWORD);

        // The following are dummy access and password files required for testing authentication.
        Files.writeString(jmxRemoteAccess, TEST_ROLE + " readwrite");
        System.setProperty(ACCESS_PROPERTY, jmxRemoteAccess.toString());
        Files.writeString(jmxRemotePassword, TEST_ROLE + " " + TEST_ROLE_PASSWORD);
        System.setProperty(PASSWORD_PROPERTY, jmxRemotePassword.toString());

        // Password file must have restricted access.
        Files.setPosixFilePermissions(jmxRemotePassword, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        // We need to rerun the startup hook with the correct properties set.
        ManagementAgentStartupHook startupHook = new ManagementAgentStartupHook();
        startupHook.execute(false);
    }

    @AfterClass
    public static void teardown() throws IOException {
        if (tempDirectory != null) {
            delete(tempDirectory);
        }
    }

    private static void delete(Path file) throws IOException {
        if (Files.isDirectory(file)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(file)) {
                for (Path child : children) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(file);
    }

    private static void createClientKey() throws Exception {
        runCommand(List.of("keytool", "-genkey",
                        "-keystore", KEYSTORE_FILENAME,
                        "-alias", "clientkey",
                        "-storepass", KEYSTORE_PASSWORD,
                        "-keypass", KEYSTORE_PASSWORD,
                        "-dname", "CN=test, OU=test, O=test, L=test, ST=test, C=test, EMAILADDRESS=test",
                        "-validity", "99999",
                        "-keyalg", "rsa"));
    }

    private static void createClientCert() throws Exception {
        runCommand(List.of("keytool", "-exportcert",
                        "-keystore", KEYSTORE_FILENAME,
                        "-alias", "clientkey",
                        "-storepass", KEYSTORE_PASSWORD,
                        "-file", "client.cer"));
    }

    private static void createServerTrustStore() throws Exception {
        runCommand(List.of("keytool", "-importcert",
                        "-noprompt",
                        "-file", "client.cer",
                        "-keystore", TRUSTSTORE_FILENAME,
                        "-storepass", TRUSTSTORE_PASSWORD));
    }

    private static void runCommand(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder().command(command);
        pb.directory(tempDirectory.toFile());
        Process process = pb.start();
        process.waitFor();
        if (process.exitValue() > 0) {
            String processError = String.join(" \\ ", FileUtils.readAllLines(process.getErrorStream()));
            String processOutput = String.join(" \\ ", FileUtils.readAllLines(process.getInputStream()));
            throw new IOException("Keytool execution error: " + processError + ", output: " + processOutput + ", command: " + command);
        }
    }

    private static MBeanServerConnection getLocalMBeanServerConnectionStatic() throws IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + "localhost" + ":" + TEST_PORT + "/jmxrmi");
        Map<String, Object> env = new HashMap<>();
        String[] credentials = {TEST_ROLE, TEST_ROLE_PASSWORD};
        env.put(JMXConnector.CREDENTIALS, credentials);
        // Include below if protecting registry with SSL
        env.put(SOCKET_FACTORY_PROPERTY, new SslRMIClientSocketFactory());
        JMXConnector connector = JMXConnectorFactory.connect(jmxUrl, env);
        return connector.getMBeanServerConnection();
    }

    @Test
    public void testConnection() throws Exception {
        // This simply tests that we can establish a connection between client and server
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        assertNotNull("Connection should not be null", mbsc);
        assertFalse("Connection default domain should not be empty", mbsc.getDefaultDomain().isEmpty());
    }

    @Test
    public void testRegistration() throws Exception {
        // This tests whether we can register a bean with the server locally and access it remotely
        // from the client via the connection
        ObjectName objectName = new ObjectName("com.jmx.test.basic:type=basic,name=simple");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.getDefaultDomain();
        server.registerMBean(new Simple(), objectName);
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        assertTrue("Expected bean is not registered.", mbsc.isRegistered(objectName));

    }

    @Test
    public void testRuntimeMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getPlatformMXBean(mbsc, RuntimeMXBean.class);
        assertTrue("PID should be positive.", runtimeMXBean.getPid() > 0);
        assertNotNull("Class Path should not be null: ", runtimeMXBean.getClassPath());
        assertTrue("Start time should be positive", runtimeMXBean.getStartTime() > 0);
    }

    @Test
    public void testRuntimeMXBeanDirect() throws Exception {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=Runtime");
        assertTrue("Uptime should be positive. ", (long) mbsc.getAttribute(objectName, "Pid") > 0);
        assertNotNull("Class Path should not be null: ", mbsc.getAttribute(objectName, "ClassPath"));
        assertTrue("Start time should be positive", (long) mbsc.getAttribute(objectName, "StartTime") > 0);
    }

    @Test
    public void testClassLoadingMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getPlatformMXBean(mbsc, ClassLoadingMXBean.class);
        if (ImageInfo.inImageRuntimeCode()) {
            assertEquals("Loaded Class count should be 0 (hardcoded at 0): ", 0, classLoadingMXBean.getLoadedClassCount());
        } else {
            assertTrue("If in java mode, number of loaded classes should be positive: ", classLoadingMXBean.getLoadedClassCount() > 0);
        }
    }

    @Test
    public void testThreadMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ThreadMXBean threadMXBean = ManagementFactory.getPlatformMXBean(mbsc, ThreadMXBean.class);
        int count = threadMXBean.getPeakThreadCount();
        assertTrue("Peak thread count should be positive ", count > 0);
        threadMXBean.resetPeakThreadCount();
        assertTrue("Peak thread count should be positive.", threadMXBean.getPeakThreadCount() > 0);
        if (!ImageInfo.inImageRuntimeCode()) {
            assertTrue("Current thread user time should be positive in java mode", threadMXBean.getCurrentThreadUserTime() >= 0);
        }
    }

    @Test
    public void testThreadMXBeanDirect() throws Exception {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=Threading");
        mbsc.invoke(objectName, "resetPeakThreadCount", null, null);
        assertTrue("Peak thread count should be positive ", (int) mbsc.getAttribute(objectName, "PeakThreadCount") > 0);
    }

    @Test
    public void testMemoryMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        MemoryMXBean memoryMXBean = ManagementFactory.getPlatformMXBean(mbsc, MemoryMXBean.class);
        MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();
        assertTrue("Memory usage should be positive: ", memoryUsage.getUsed() > 0);
    }

    @Test
    public void testMemoryMXBeanDirect() throws Exception {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        mbsc.invoke(objectName, "gc", null, null);
    }

    @Test
    public void testGarbageCollectorMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getPlatformMXBeans(mbsc, GarbageCollectorMXBean.class);
        for (GarbageCollectorMXBean gcBean : garbageCollectorMXBeans) {
            assertNotNull("GC object name should not be null", gcBean.getObjectName());
            assertTrue("Number of GC should not be negative", gcBean.getCollectionCount() >= 0);
        }
    }

    @Test
    public void testOperatingSystemMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getPlatformMXBean(mbsc, OperatingSystemMXBean.class);
        assertNotNull("OS version can't be null. ", operatingSystemMXBean.getVersion());
    }

    @Test
    public void testOperatingSystemMXBeanDirect() throws Exception {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=OperatingSystem");
        assertNotNull("OS version can't be null. ", mbsc.getAttribute(objectName, "Version"));
    }

    @Test
    public void testMemoryManagerMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        List<MemoryManagerMXBean> memoryManagerMXBeans = ManagementFactory.getPlatformMXBeans(mbsc, MemoryManagerMXBean.class);
        for (MemoryManagerMXBean memoryManagerMXBean : memoryManagerMXBeans) {
            assertNotNull("Memory pool names should not be null. ", memoryManagerMXBean.getMemoryPoolNames());
        }
    }

    @Test
    public void testMemoryPoolMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getPlatformMXBeans(mbsc, MemoryPoolMXBean.class);
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            assertNotNull("Memory Pool name should not be null ", memoryPoolMXBean.getName());
        }
    }

    @Test
    public void testFlightRecorderMXBeanProxy() throws IOException {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        FlightRecorderMXBean flightRecorderMXBean = ManagementFactory.getPlatformMXBean(mbsc, FlightRecorderMXBean.class);
        flightRecorderMXBean.newRecording();
        assertFalse("Flight recordings should be available because we just created one.", flightRecorderMXBean.getRecordings().isEmpty());
    }

    @Test
    public void testFlightRecorderMXBeanDirect() throws Exception {
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("jdk.management.jfr:type=FlightRecorder");
        long recording = (long) mbsc.invoke(objectName, "newRecording", null, null);
        mbsc.invoke(objectName, "startRecording", new Object[]{recording}, new String[]{"long"});
        mbsc.invoke(objectName, "stopRecording", new Object[]{recording}, new String[]{"long"});
    }
}
