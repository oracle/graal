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

import com.oracle.svm.core.jfr.JfrEvent;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.Test;

/**
 * This tests built in native JFR events that are sent periodically upon every chunk. The events
 * ThreadCPULoad and ThreadAllocationStatistics are not tested here since they already have their
 * own individual tests.
 */
public class TestEveryChunkNativePeriodicEvents extends JfrRecordingTest {

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{
                        JfrEvent.JavaThreadStatistics.getName(),
                        JfrEvent.PhysicalMemory.getName(),
                        JfrEvent.ClassLoadingStatistics.getName(),
        };
        Recording recording = startRecording(events);

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean foundJavaThreadStatistics = false;
        boolean foundPhysicalMemory = false;
        boolean foundClassLoadingStatistics = false;
        for (RecordedEvent e : events) {
            if (e.getEventType().getName().equals(JfrEvent.JavaThreadStatistics.getName())) {
                foundJavaThreadStatistics = true;
                assertTrue(e.getLong("activeCount") > 1);
                assertTrue(e.getLong("daemonCount") > 0);
                assertTrue(e.getLong("accumulatedCount") > 1);
                assertTrue(e.getLong("peakCount") > 1);
            } else if (e.getEventType().getName().equals(JfrEvent.PhysicalMemory.getName())) {
                foundPhysicalMemory = true;
                assertTrue(e.getLong("totalSize") > 0);
                assertTrue(e.getLong("usedSize") > 0 || e.getLong("usedSize") == -1);

            } else if (e.getEventType().getName().equals(JfrEvent.ClassLoadingStatistics.getName())) {
                foundClassLoadingStatistics = true;
                assertTrue(e.getLong("loadedClassCount") > 0);
            }
        }
        assertTrue(foundJavaThreadStatistics && foundPhysicalMemory && foundClassLoadingStatistics);
    }

}
