/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.OS;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestJdkContainerEvents extends JfrRecordingTest {
    private static final long PERIOD_MS = 10;

    @Test
    public void test() throws Throwable {
        Assume.assumeTrue("Container support is limited to Linux", OS.LINUX.isCurrent());
        String[] events = new String[]{"jdk.ContainerCPUThrottling", "jdk.ContainerCPUUsage",
                        "jdk.ContainerConfiguration", "jdk.ContainerIOUsage", "jdk.ContainerMemoryUsage"};

        Recording recording = prepareRecording(events, getDefaultConfiguration(), null, createTempJfrFile());
        recording.enable("jdk.ContainerCPUThrottling").withPeriod(Duration.ofMillis(PERIOD_MS));
        recording.enable("jdk.ContainerCPUUsage").withPeriod(Duration.ofMillis(PERIOD_MS));
        recording.enable("jdk.ContainerIOUsage").withPeriod(Duration.ofMillis(PERIOD_MS));
        recording.enable("jdk.ContainerMemoryUsage").withPeriod(Duration.ofMillis(PERIOD_MS));

        recording.start();

        // Sleep so the periodic events can be emitted.
        Thread.sleep(PERIOD_MS * 5);

        stopRecording(recording, TestJdkContainerEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean foundContainerCPUThrottling = false;
        boolean foundContainerCPUUsage = false;
        boolean foundContainerIOUsage = false;
        boolean foundContainerMemoryUsage = false;
        boolean foundContainerConfiguration = false;
        for (RecordedEvent e : events) {
            String name = e.getEventType().getName();
            if (name.equals("jdk.ContainerCPUThrottling")) {
                foundContainerCPUThrottling = true;
            } else if (name.equals("jdk.ContainerCPUUsage") &&
                            e.getLong("cpuTime") > 0 &&
                            e.getLong("cpuSystemTime") > 0 &&
                            e.getLong("cpuUserTime") > 0) {
                foundContainerCPUUsage = true;
            } else if (name.equals("jdk.ContainerIOUsage")) {
                foundContainerIOUsage = true;
            } else if (name.equals("jdk.ContainerMemoryUsage") &&
                            e.getLong("memoryUsage") > 0 &&
                            e.getLong("swapMemoryUsage") > 0) {
                foundContainerMemoryUsage = true;
            } else if (name.equals("jdk.ContainerConfiguration") &&
                            e.getLong("effectiveCpuCount") > 0) {
                /*
                 * It's also worth checking hostTotalMemory and hostTotalSwapMemory are > 0 or -1
                 * after GR-52453 is integrated
                 */
                foundContainerConfiguration = true;
            }
        }

        assertTrue(foundContainerCPUThrottling);
        assertTrue(foundContainerCPUUsage);
        assertTrue(foundContainerIOUsage);
        assertTrue(foundContainerMemoryUsage);
        assertTrue(foundContainerConfiguration);
    }
}
