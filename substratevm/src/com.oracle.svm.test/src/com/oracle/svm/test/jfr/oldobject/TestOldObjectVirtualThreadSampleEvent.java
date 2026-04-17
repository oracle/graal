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

package com.oracle.svm.test.jfr.oldobject;

import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.word.impl.Word;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.util.TimeUtils;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

public class TestOldObjectVirtualThreadSampleEvent extends JfrOldObjectTest {
    private static final String VIRTUAL_THREAD_NAME = "TestOldObjectVirtualThreadSampleEvent";

    @Test
    public void test() throws Throwable {
        if (!HasJfrSupport.get()) {
            /* Prevent that the code below is reachable on platforms that don't support JFR. */
            Assert.fail("JFR is not supported on this platform.");
            return;
        }

        int arrayLength = Integer.MIN_VALUE;
        TinyObject obj = new TinyObject(44);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Recording recording = startRecording();
        Thread thread = Thread.ofVirtual().name(VIRTUAL_THREAD_NAME).start(() -> sampleInVirtualThread(obj, arrayLength, failure));
        thread.join();

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw throwable;
        }

        recording.dump(createTempJfrFile());
        stopRecording(recording, events -> validateVirtualThreadEvents(events, TinyObject.class, arrayLength));
    }

    private static void validateVirtualThreadEvents(List<RecordedEvent> events, Class<?> expectedSampledType, @SuppressWarnings("unused") int expectedArrayLength) {
        if (events.isEmpty()) {
            throw new AssertionError("Expected at least one OldObjectSample event.");
        }

        int matchingEvents = 0;
        String expectedTypeName = expectedSampledType.getName();
        for (RecordedEvent event : events) {
            RecordedObject object = event.getValue("object");
            assertNotNull(object);

            RecordedClass sampledType = object.getClass("type");
            if (sampledType != null && expectedTypeName.equals(sampledType.getName())) {
                RecordedThread eventThread = event.getValue("eventThread");
                assertNotNull("No event thread", eventThread);

                matchingEvents++;
            }
        }

        if (matchingEvents == 0) {
            throw new AssertionError("Expected at least one OldObjectSample event for " + expectedTypeName + ".");
        }
    }

    @SuppressWarnings("unused")
    private static void sampleInVirtualThread(TinyObject obj, int arrayLength, AtomicReference<Throwable> failure) {
        if (!HasJfrSupport.get()) {
            /* Prevent that the code below is reachable on platforms that don't support JFR. */
            Assert.fail("JFR is not supported on this platform.");
            return;
        }

        boolean success;
        long endTime = System.currentTimeMillis() + TimeUtils.secondsToMillis(5);
        do {
            success = SubstrateJVM.getOldObjectProfiler().sample(obj, Word.unsigned(1024 * 1024 * 1024), arrayLength);
        } while (!success && System.currentTimeMillis() < endTime);

        if (!success) {
            failure.set(new AssertionError("Timed out waiting for sampling to complete"));
        }
    }
}
