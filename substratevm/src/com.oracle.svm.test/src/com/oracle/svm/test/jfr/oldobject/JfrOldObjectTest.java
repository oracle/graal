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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.test.jfr.JfrRecordingTest;

import jdk.graal.compiler.word.Word;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

public abstract class JfrOldObjectTest extends JfrRecordingTest {
    @Rule public TestName testName = new TestName();

    @Before
    public void updateMemoryUsedAfterLastGC() {
        System.gc();
    }

    protected Recording startRecording() throws Throwable {
        String[] events = {JfrEvent.OldObjectSample.getName()};
        Map<String, String> settings = new HashMap<>();
        settings.put("old-objects-stack-trace", "true");
        return startRecording(events, getDefaultConfiguration(), settings);
    }

    /**
     * A normal slow-path allocation is not enough to trigger the sampling reliably because some
     * other thread may already be sampling (only one thread can sample at a time).
     */
    protected void testSampling(Object obj, int arrayLength, EventValidator validator) throws Throwable {
        if (!HasJfrSupport.get()) {
            /* Prevent that the code below is reachable on platforms that don't support JFR. */
            Assert.fail("JFR is not supported on this platform.");
            return;
        }

        Recording recording = startRecording();

        boolean success;
        long endTime = System.currentTimeMillis() + TimeUtils.secondsToMillis(5);
        do {
            success = SubstrateJVM.getOldObjectProfiler().sample(obj, Word.unsigned(1024 * 1024 * 1024), arrayLength);
        } while (!success && System.currentTimeMillis() < endTime);

        Assert.assertTrue("Timed out waiting for sampling to complete", success);

        stopRecording(recording, validator);
    }

    protected List<RecordedEvent> validateEvents(List<RecordedEvent> events, Class<?> expectedSampledType, int expectedArrayLength) {
        assertFalse(events.isEmpty());

        ArrayList<RecordedEvent> matchingEvents = new ArrayList<>();
        String expectedTypeName = expectedSampledType.getName();
        for (RecordedEvent event : events) {
            RecordedObject object = event.getValue("object");
            assertNotNull(object);

            if (object.getClass("type").getName().equals(expectedTypeName)) {
                long startTime = event.getLong("startTime");
                assertTrue(startTime > 0);

                assertEquals(0, event.getDuration().toMillis());

                String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
                assertNotNull("No event thread", eventThread);

                List<RecordedFrame> frames = event.getStackTrace().getFrames();
                assertFalse(frames.isEmpty());
                assertTrue(frames.stream().anyMatch(e -> testName.getMethodName().equals(e.getMethod().getName())));

                checkTopStackFrame(event, "testSampling");

                long allocationTime = event.getLong("allocationTime");
                assertTrue(allocationTime > 0);
                assertTrue(allocationTime <= startTime);

                assertTrue(event.getLong("objectSize") > 0);
                assertTrue(event.getLong("objectAge") > 0);
                assertTrue(event.getLong("lastKnownHeapUsage") > 0);
                assertEquals(expectedArrayLength, event.getInt("arrayElements"));

                /* GC root is not supported at the moment. */
                assertNull(event.getValue("root"));

                matchingEvents.add(event);
            }
        }

        assertFalse(matchingEvents.isEmpty());
        return matchingEvents;
    }
}
