/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * This test checks that virtual threads are recorded correctly in JFR events after a chunk
 * rotation. Specifically, this catches the case where vthreads are not properly re-registered with
 * the thread constant pool after a chunk rotation. Reflection is used for JDK 19+ APIs for
 * compatibility with JDK 17. Once JDK 17 support ends, the reflection can be replaced.
 */
public class TestVirtualThreadsChunkRotation extends JfrRecordingTest {
    private static final int THREADS = 3;
    private static final int EXPECTED_EVENTS = THREADS;
    private final AtomicInteger emittedEventsPerType = new AtomicInteger(0);
    private final Set<Long> expectedThreads = Collections.synchronizedSet(new HashSet<>());
    private final MonitorWaitHelper helper = new MonitorWaitHelper();

    private volatile boolean proceed;

    @Before
    public void checkJavaVersion() {
        assumeTrue("skipping JFR virtual thread tests", JavaVersionUtil.JAVA_SPEC >= 19);
    }

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorWait.getName()};
        Recording recording = startRecording(events);

        Runnable eventEmitter = () -> {
            // Busy wait so that they do not unmount.
            while (true) {
                if (proceed) {
                    break;
                }
            }
            helper.doEvent();
            try {
                expectedThreads.add((Long) Thread.class.getMethod("threadId").invoke(Thread.currentThread()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            emittedEventsPerType.incrementAndGet();
        };

        // Start vthreads which should also register them with the thread constant pool.
        List<Thread> threads = VirtualStressor.executeAsync(THREADS, eventEmitter);
        // Force a chunk rotation.
        recording.dump(createTempJfrFile());
        // Once, the chunk rotation is over, notify the vthreads they can emit events.
        proceed = true;
        VirtualStressor.join(threads);
        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        int count = 0;
        for (RecordedEvent event : events) {
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(MonitorWaitHelper.class.getName())) {
                RecordedThread recordedThread = event.getThread("eventThread");
                assertNotNull("Virtual thread data is missing.", recordedThread);
                Long thread = recordedThread.getJavaThreadId();
                expectedThreads.remove(thread);
                count++;
            }
        }
        assertEquals(EXPECTED_EVENTS, count);
        assertTrue(expectedThreads.isEmpty());
    }
}
