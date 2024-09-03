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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

/**
 * This tests checks that virtual thread IDs are recorded correctly in events that are streamed.
 * Reflection is used for JDK 19+ APIs for compatibility with JDK 17. Once JDK 17 support ends, the
 * reflection can be replaced.
 */
public class TestVirtualThreadsJfrStreaming extends JfrStreamingTest {
    private static final int THREADS = 5;
    private static final int EXPECTED_EVENTS = THREADS;

    private final MonitorWaitHelper helper = new MonitorWaitHelper();
    private final AtomicInteger emittedEventsPerType = new AtomicInteger(0);
    private final Set<Long> expectedThreads = Collections.synchronizedSet(new HashSet<>());

    @Before
    public void checkJavaVersion() {
        assumeTrue("skipping JFR virtual thread tests", JavaVersionUtil.JAVA_SPEC >= 19);
    }

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorWait.getName()};
        RecordingStream stream = startStream(events);

        stream.onEvent(JfrEvent.JavaMonitorWait.getName(), event -> {
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(MonitorWaitHelper.class.getName())) {
                RecordedThread recordedThread = event.getThread("eventThread");
                assertNotNull("Virtual thread data is missing.", recordedThread);
                Long thread = recordedThread.getJavaThreadId();
                expectedThreads.remove(thread);
            }
        });

        Runnable eventEmitter = () -> {
            helper.doEvent();
            try {
                expectedThreads.add((Long) Thread.class.getMethod("threadId").invoke(Thread.currentThread()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            emittedEventsPerType.incrementAndGet();
        };

        VirtualStressor.execute(THREADS, eventEmitter);
        waitUntilTrue(() -> emittedEventsPerType.get() == EXPECTED_EVENTS);
        waitUntilTrue(expectedThreads::isEmpty);
        stopStream(stream, null);
    }
}
