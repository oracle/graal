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

package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.util.TimeUtils;
import org.graalvm.word.WordFactory;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestOldObjectSampleEvent extends JfrOldObjectTest {

    @Test
    public void testObjectLeak() throws Throwable {
        int arrayLength = Integer.MIN_VALUE;
        testSampling(new TinyObject(43), arrayLength, events -> validateEvents(events, TinyObject.class, arrayLength));
    }

    @Test
    public void testSmallArrayLeak() throws Throwable {
        int arrayLength = 3;
        testSampling(new TinyObject[arrayLength], arrayLength, events -> validateEvents(events, TinyObject.class.arrayType(), arrayLength));
    }

    @Test
    public void testLargeArrayLeak() throws Throwable {
        int arrayLength = 1024 * 1024;
        testSampling(new TinyObject[arrayLength], arrayLength, events -> validateEvents(events, TinyObject.class.arrayType(), arrayLength));
    }

    @Test
    public void testConstantData() throws Throwable {
        if (!HasJfrSupport.get()) {
            /* Prevent the code below from being reachable on platforms that don't support JFR. */
            fail("JFR is not supported on this platform.");
            return;
        }
        String threadName = "test_name";
        long tid;
        int arrayLength = 1024 * 1024;
        Object obj = new TinyObject[arrayLength];
        Recording recording = startRecording();

        final boolean[] success = new boolean[1];
        Runnable runnable = () -> {
            long endTime = System.currentTimeMillis() + TimeUtils.secondsToMillis(5);
            do {
                success[0] = SubstrateJVM.getOldObjectProfiler().sample(obj, WordFactory.unsigned(1024 * 1024 * 1024), arrayLength);
            } while (!success[0] && System.currentTimeMillis() < endTime);
        };

        Thread worker = new Thread(runnable);
        worker.setName(threadName);
        tid = worker.threadId();
        worker.start();

        worker.join();
        assertTrue("Timed out waiting for sampling to complete", success[0]);

        // Force a chunk rotation.
        recording.dump(createTempJfrFile());

        stopRecording(recording, events -> this.validateConstants(events, TinyObject.class.arrayType(), threadName, tid));
    }

    private List<RecordedEvent> validateConstants(List<RecordedEvent> events, Class<?> expectedSampledType, String expectedName, long expectedId) {
        assertTrue(events.size() > 0);

        ArrayList<RecordedEvent> matchingEvents = new ArrayList<>();
        String expectedTypeName = expectedSampledType.getName();
        for (RecordedEvent event : events) {
            RecordedObject object = event.getValue("object");
            assertNotNull(object);

            if (object.getClass("type").getName().equals(expectedTypeName)) {
                // Check thread data
                RecordedThread eventThread = event.getThread("eventThread");
                assertNotNull("No thread data found.", eventThread);
                assertTrue("Event thread name is incorrect.", eventThread.getJavaName().equals(expectedName));
                assertTrue("Event thread ID is incorrect.", eventThread.getId() == expectedId);

                // Check stacktrace data
                List<RecordedFrame> frames = event.getStackTrace().getFrames();
                assertTrue("No stack trace data found.", frames.size() > 0);
                assertTrue("Stack frames are incorrect.", frames.stream().anyMatch(e -> e.getMethod().getName().contains(testName.getMethodName())));

                matchingEvents.add(event);
            }
        }

        assertTrue(matchingEvents.size() > 0);
        return matchingEvents;
    }
}
