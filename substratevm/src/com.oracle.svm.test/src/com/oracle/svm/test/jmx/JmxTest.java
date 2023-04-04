/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.jdk.management.ManagementAgentStartupHook;
import com.oracle.svm.test.AddExports;

import jdk.management.jfr.FlightRecorderMXBean;

@AddExports("jdk.management.agent/jdk.internal.agent")
public class JmxTest {
    static final String PORT_PROPERTY = "com.sun.management.jmxremote.port";
    static final String AUTH_PROPERTY = "com.sun.management.jmxremote.authenticate";
    static final String SSL_PROPERTY = "com.sun.management.jmxremote.ssl";
    static final String TEST_PORT = "12345";
    static final String FALSE = "false";

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JMX tests", !ImageInfo.inImageCode() ||
                        (VMInspectionOptions.hasJmxClientSupport() && VMInspectionOptions.hasJmxServerSupport()));

        System.setProperty(PORT_PROPERTY, TEST_PORT);
        System.setProperty(AUTH_PROPERTY, FALSE);
        System.setProperty(SSL_PROPERTY, FALSE);
        try {
            // We need to rerun the startup hook with the correct properties set.
            ManagementAgentStartupHook startupHook = new ManagementAgentStartupHook();
            startupHook.execute(false);
        } catch (Exception e) {
            Assert.fail("Failed to start server Cause: " + e.getMessage());
        }
    }

    private static MBeanServerConnection getLocalMBeanServerConnectionStatic() {
        try {
            JMXServiceURL jmxUrl = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + "localhost" + ":" + TEST_PORT + "/jmxrmi");
            Map<String, Object> env = new HashMap<>();

            JMXConnector connector = JMXConnectorFactory.connect(jmxUrl, env);
            return connector.getMBeanServerConnection();
        } catch (IOException e) {
            Assert.fail("Failed to establish connection Cause: " + e.getMessage());
        }
        return null;
    }

    @Test
    public void testConnection() throws Exception {
        // This simply tests that we can establish a connection between client and server
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        assertTrue("Connection should not be null", mbsc != null);
        assertTrue("Connection default domain should not be empty", !mbsc.getDefaultDomain().isEmpty());
    }

    @Test
    public void testRegistration() throws Exception {
        // This tests whether we can register a bean with the server locally and access it remotely
        // from the client via the connection
        ObjectName objectName = new ObjectName("com.jmx.test.basic:type=basic,name=simple");
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        try {
            server.getDefaultDomain();
            server.registerMBean(new Simple(), objectName);

        } catch (Exception e) {
            Assert.fail("Failed to register bean. Cause: " + e.getMessage());
        }
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        assertTrue("Expected bean is not registered.", mbsc.isRegistered(objectName));

    }

    @Test
    public void testRuntimeMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        RuntimeMXBean runtimeMXBean = null;
        try {
            runtimeMXBean = ManagementFactory.getPlatformMXBean(mbsc, RuntimeMXBean.class);
        } catch (IOException e) {
            Assert.fail("Failed to get RuntimeMXBean. : " + e.getMessage());
        }

        assertTrue("PID should be positive.", runtimeMXBean.getPid() > 0);
        assertTrue("Class Path should not be null: ", runtimeMXBean.getClassPath() != null);
        assertTrue("Start time should be positive", runtimeMXBean.getStartTime() > 0);
    }

    @Test
    public void testRuntimeMXBeanDirect() throws MalformedObjectNameException {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=Runtime");
        try {
            assertTrue("Uptime should be positive. ", (long) mbsc.getAttribute(objectName, "Pid") > 0);
            assertTrue("Class Path should not be null: ", mbsc.getAttribute(objectName, "ClassPath") != null);
            assertTrue("Start time should be positive", (long) mbsc.getAttribute(objectName, "StartTime") > 0);
        } catch (Exception e) {
            Assert.fail("Remote invocations failed : " + e.getMessage());
        }
    }

    @Test
    public void testClassLoadingMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();

        ClassLoadingMXBean classLoadingMXBean = null;
        try {
            classLoadingMXBean = ManagementFactory.getPlatformMXBean(mbsc, ClassLoadingMXBean.class);
        } catch (IOException e) {
            Assert.fail("Failed to get ClassLoadingMXBean. : " + e.getMessage());
        }
        if (ImageInfo.inImageRuntimeCode()) {
            assertTrue("Loaded Class count should be 0 (hardcoded at 0): ", classLoadingMXBean.getLoadedClassCount() == 0);
        } else {
            assertTrue("If in java mode, number of loaded classes should be positive: ", classLoadingMXBean.getLoadedClassCount() > 0);
        }
    }

    @Test
    public void testThreadMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ThreadMXBean threadMXBean = null;
        try {
            threadMXBean = ManagementFactory.getPlatformMXBean(mbsc, ThreadMXBean.class);
        } catch (IOException e) {
            Assert.fail("Failed to get ThreadMXBean. : " + e.getMessage());
        }
        int count = threadMXBean.getPeakThreadCount();
        assertTrue("Peak thread count should be positive ", count > 0);
        threadMXBean.resetPeakThreadCount();
        assertTrue("Peak thread count should be positive.", threadMXBean.getPeakThreadCount() > 0);
        if (!ImageInfo.inImageRuntimeCode()) {
            assertTrue("Current thread user time should be positive in java mode", threadMXBean.getCurrentThreadUserTime() >= 0);
        }
    }

    @Test
    public void testThreadMXBeanDirect() throws MalformedObjectNameException {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=Threading");
        try {
            mbsc.invoke(objectName, "resetPeakThreadCount", null, null);
            assertTrue("Peak thread count should be positive ", (int) mbsc.getAttribute(objectName, "PeakThreadCount") > 0);
        } catch (Exception e) {
            Assert.fail("Remote invocations failed : " + e.getMessage());
        }
    }

    @Test
    public void testMemoryMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        MemoryMXBean memoryMXBean = null;
        try {
            memoryMXBean = ManagementFactory.getPlatformMXBean(mbsc, MemoryMXBean.class);

        } catch (Exception e) {
            Assert.fail("Failed to get MemoryMXBean. : " + e.getMessage());
        }
        MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();
        assertTrue("Memory usage should be positive: ", memoryUsage.getUsed() > 0);

    }

    @Test
    public void testMemoryMXBeanDirect() throws MalformedObjectNameException {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        try {
            mbsc.invoke(objectName, "gc", null, null);
        } catch (Exception e) {
            Assert.fail("Remote invocations failed : " + e.getMessage());
        }
    }

    @Test
    public void testGarbageCollectorMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = null;
        try {
            garbageCollectorMXBeans = ManagementFactory.getPlatformMXBeans(mbsc, GarbageCollectorMXBean.class);

        } catch (Exception e) {
            Assert.fail("Failed to get GarbageCollectorMXBean. : " + e.getMessage());
        }
        for (GarbageCollectorMXBean gcBean : garbageCollectorMXBeans) {
            assertTrue("GC object name should not be null", gcBean.getObjectName() != null);
            assertTrue("Number of GC should not be negative", gcBean.getCollectionCount() >= 0);
        }
    }

    @Test
    public void testOperatingSystemMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();

        OperatingSystemMXBean operatingSystemMXBean = null;
        try {
            operatingSystemMXBean = ManagementFactory.getPlatformMXBean(mbsc, OperatingSystemMXBean.class);

        } catch (Exception e) {
            Assert.fail("Failed to get OperatingSystemMXBean. : " + e.getMessage());
        }
        assertTrue("OS version can't be null. ", operatingSystemMXBean.getVersion() != null);
    }

    @Test
    public void testOperatingSystemMXBeanDirect() throws MalformedObjectNameException {
        // Basic test to make sure reflective accesses are set up correctly.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("java.lang:type=OperatingSystem");
        try {
            assertTrue("OS version can't be null. ", mbsc.getAttribute(objectName, "Version") != null);
        } catch (Exception e) {
            Assert.fail("Remote invokations failed : " + e.getMessage());
        }
    }

    @Test
    public void testMemoryManagerMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        List<MemoryManagerMXBean> memoryManagerMXBeans = null;
        try {
            memoryManagerMXBeans = ManagementFactory.getPlatformMXBeans(mbsc, MemoryManagerMXBean.class);

        } catch (Exception e) {
            Assert.fail("Failed to get MemoryManagerMXBean. : " + e.getMessage());
        }
        for (MemoryManagerMXBean memoryManagerMXBean : memoryManagerMXBeans) {
            assertTrue("Memory pool names should not be null. ", memoryManagerMXBean.getMemoryPoolNames() != null);
        }
    }

    @Test
    public void testMemoryPoolMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();

        List<MemoryPoolMXBean> memoryPoolMXBeans = null;
        try {
            memoryPoolMXBeans = ManagementFactory.getPlatformMXBeans(mbsc, MemoryPoolMXBean.class);

        } catch (Exception e) {
            Assert.fail("Failed to get MemoryPoolMXBean. : " + e.getMessage());
        }
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            assertTrue("Memory Pool name should not be null ", memoryPoolMXBean.getName() != null);
        }
    }

    @Test
    public void testFlightRecorderMXBeanProxy() {
        // This test checks to make sure we are able to get the MXBean and do simple things with it.
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();

        FlightRecorderMXBean flightRecorderMXBean = null;
        try {
            flightRecorderMXBean = ManagementFactory.getPlatformMXBean(mbsc, FlightRecorderMXBean.class);

        } catch (Exception e) {
            Assert.fail("Failed to get FlightRecorderMXBean. : " + e.getMessage());
        }
        flightRecorderMXBean.newRecording();
        assertTrue("Flight recordings should be available because we just created one.", !flightRecorderMXBean.getRecordings().isEmpty());
    }

    @Test
    public void testFlightRecorderMXBeanDirect() throws MalformedObjectNameException {
        MBeanServerConnection mbsc = getLocalMBeanServerConnectionStatic();
        ObjectName objectName = new ObjectName("jdk.management.jfr:type=FlightRecorder");
        try {
            long recording = (long) mbsc.invoke(objectName, "newRecording", null, null);
            mbsc.invoke(objectName, "startRecording", new Object[]{recording}, new String[]{"long"});
            mbsc.invoke(objectName, "stopRecording", new Object[]{recording}, new String[]{"long"});
        } catch (Exception e) {
            Assert.fail("Remote invokations failed : " + e.getMessage());
        }
    }
}
