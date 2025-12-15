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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.monitor.MonitorInflationCause;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorInflateEvent extends JfrRecordingTest {
    private Thread firstThread;
    private Thread secondThread;
    private String expectedClassName;
    private volatile boolean passedCheckpoint;

    @Test
    public void testObject() throws Throwable {
        test(new MonitorTester());
    }

    @Test
    public void testArray() throws Throwable {
        test(new MonitorTester[5]);
    }

    @Test
    public void testClass() throws Throwable {
        test(MonitorTester.class, DynamicHubCompanion.class.getName());
    }

    @Test
    public void testString() throws Throwable {
        test("ABC");
    }

    private void test(Object obj) throws Throwable {
        test(obj, obj.getClass().getName());
    }

    private void test(Object obj, String className) throws Throwable {
        firstThread = new Thread(() -> {
            try {
                synchronized (obj) {
                    secondThread.start();
                    /* Spin until second thread blocks. */
                    while (!secondThread.getState().equals(Thread.State.BLOCKED) || !passedCheckpoint) {
                        Thread.sleep(10);
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        secondThread = new Thread(() -> {
            passedCheckpoint = true;
            synchronized (obj) {
                GraalDirectives.blackhole(obj);
            }
        });

        expectedClassName = className;

        /* Now that the data is prepared, start the JFR recording. */
        String[] events = new String[]{JfrEvent.JavaMonitorInflate.getName()};
        Recording recording = startRecording(events);

        /*
         * Generate event with "Monitor Enter" cause. Start the first thread so that it can then
         * start the second thread.
         */
        firstThread.start();

        firstThread.join();
        secondThread.join();

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        boolean foundCauseEnter = false;
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            String monitorClass = event.<RecordedClass> getValue("monitorClass").getName();
            String cause = event.getValue("cause");

            if (monitorClass.equals(expectedClassName) && cause.equals(MonitorInflationCause.MONITOR_ENTER.getText()) &&
                            (eventThread.equals(firstThread.getName()) || eventThread.equals(secondThread.getName()))) {
                foundCauseEnter = true;
            }

            checkTopStackFrame(event, "monitorEnter", "createMonitorAndAddToMap", "getOrCreateMonitorFromObject");
        }
        assertTrue("Expected monitor inflate event not found.", foundCauseEnter);
    }

    private static final class MonitorTester {
    }
}
