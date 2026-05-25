/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, Red Hat Inc. All rights reserved.
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
import static org.junit.Assert.assertFalse;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.events.StringEvent;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadConstantPoolParser;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

public class TestVirtualThreadsUnreferencedStreaming extends JfrStreamingTest {
    private static final int THREADS = 16;
    private static final String MARKER = "marker";

    private final Set<Long> unreferencedVirtualThreadIds = Collections.synchronizedSet(new HashSet<>()); // noEconomicSet(synchronization)
    private Path dumpedRecording;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"com.jfr.String"};
        RecordingStream stream = startMinimalStream(events, s -> {
            /*
             * This test verifies that streamed events do not retain virtual threads that are not
             * referenced by the streamed payload. Disable unrelated default thread lifecycle events
             * so incidental ThreadStart/ThreadEnd metadata does not affect the assertion.
             */
            s.disable("jdk.ThreadStart");
            s.disable("jdk.ThreadEnd");
        });

        StringEvent stringEvent = new StringEvent();
        stringEvent.message = MARKER;
        stringEvent.commit();

        AtomicInteger completedThreads = new AtomicInteger();
        List<Thread> threads = VirtualStressor.executeAsync(THREADS, () -> {
            try {
                long threadId = (Long) Thread.class.getMethod("threadId").invoke(Thread.currentThread());
                unreferencedVirtualThreadIds.add(threadId);
                completedThreads.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        waitUntilTrue(() -> completedThreads.get() == THREADS);
        VirtualStressor.join(threads);

        dumpedRecording = createTempJfrFile();
        stream.dump(dumpedRecording);
        stopStream(stream, this::validateEventsAndThreadPool, dumpedRecording);
    }

    private void validateEventsAndThreadPool(List<RecordedEvent> events) throws Throwable {
        assertEquals(1, events.size());
        assertEquals(MARKER, events.getFirst().getString("message"));

        JfrFileParser parser = new JfrFileParser(dumpedRecording);
        parser.verify();

        ThreadConstantPoolParser threadParser = (ThreadConstantPoolParser) parser.getSupportedConstantPools().get(JfrType.Thread.getId());
        for (Long threadId : unreferencedVirtualThreadIds) {
            assertFalse("Unexpected thread constant pool entry for unreferenced virtual thread " + threadId,
                            threadParser.hasFoundId(threadId));
        }
    }
}
