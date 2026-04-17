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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;

public class TestVirtualThreadsExecutionSample extends JfrRecordingTest {
    private static final Duration SAMPLE_PERIOD = Duration.ofMillis(10);
    private static final Duration WAIT_FOR_SAMPLES_TIMEOUT = Duration.ofSeconds(30);
    private static final String LONG_LIVED_SAMPLE_METHOD = "spinUntil";

    private final AtomicLong sampledVirtualThreadId = new AtomicLong();
    private final AtomicLong shortLivedVirtualThreadId = new AtomicLong();
    private Instant rotationTime;

    @Test
    public void testVirtualThreadExecutionSample() throws Throwable {
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread thread = startBusyVirtualThread(stop);
        Recording recording = startExecutionSampleRecording();

        waitForExecutionSample(recording, false, sampledVirtualThreadId, LONG_LIVED_SAMPLE_METHOD);

        stop.set(true);
        thread.join();
        stopRecording(recording, this::validateExecutionSamples);
    }

    @Test
    public void testVirtualThreadExecutionSampleAfterChunkRotation() throws Throwable {
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread thread = startBusyVirtualThread(stop);
        Recording recording = startExecutionSampleRecording();

        rotationTime = Instant.now();
        recording.dump(createTempJfrFile());
        waitForExecutionSample(recording, true, sampledVirtualThreadId, LONG_LIVED_SAMPLE_METHOD);

        stop.set(true);
        thread.join();
        stopRecording(recording, this::validateExecutionSamplesAfterRotation);
    }

    @Test
    public void testShortLivedVirtualThreadExecutionSampleAfterChunkRotation() throws Throwable {
        Recording recording = startExecutionSampleRecording();

        rotationTime = Instant.now();
        recording.dump(createTempJfrFile());

        AtomicBoolean stop = new AtomicBoolean(false);
        Thread thread = Thread.ofVirtual().start(() -> {
            shortLivedVirtualThreadId.set(Thread.currentThread().threadId());
            spinUntil(stop);
        });
        waitForExecutionSample(recording, true, shortLivedVirtualThreadId, LONG_LIVED_SAMPLE_METHOD);
        stop.set(true);
        thread.join();

        stopRecording(recording, this::validateShortLivedExecutionSamplesAfterRotation);
    }

    private Recording startExecutionSampleRecording() throws Throwable {
        Map<String, String> settings = new HashMap<>();
        settings.put("flush-interval", "0");

        String[] events = new String[]{JfrEvent.ExecutionSample.getName()};
        Recording recording = prepareRecording(events, getDefaultConfiguration(), settings, createTempJfrFile());
        recording.enable(JfrEvent.ExecutionSample.getName()).withPeriod(SAMPLE_PERIOD);
        recording.start();
        return recording;
    }

    private Thread startBusyVirtualThread(AtomicBoolean stop) throws Throwable {
        AtomicBoolean started = new AtomicBoolean(false);
        Thread thread = Thread.ofVirtual().start(() -> {
            sampledVirtualThreadId.set(Thread.currentThread().threadId());
            started.set(true);
            spinUntil(stop);
        });
        waitUntilTrue(started::get);
        return thread;
    }

    private void validateExecutionSamples(List<RecordedEvent> events) {
        validateExecutionSamples(events, false, sampledVirtualThreadId.get(), LONG_LIVED_SAMPLE_METHOD);
    }

    private void validateExecutionSamplesAfterRotation(List<RecordedEvent> events) {
        assertNotNull("Chunk rotation marker must be recorded.", rotationTime);
        validateExecutionSamples(events, true, sampledVirtualThreadId.get(), LONG_LIVED_SAMPLE_METHOD);
    }

    private void validateShortLivedExecutionSamplesAfterRotation(List<RecordedEvent> events) {
        assertNotNull("Chunk rotation marker must be recorded.", rotationTime);
        validateExecutionSamples(events, true, shortLivedVirtualThreadId.get(), LONG_LIVED_SAMPLE_METHOD);
    }

    private void waitForExecutionSample(Recording recording, boolean afterRotationOnly, AtomicLong expectedThreadId, String expectedMethodName) throws Throwable {
        long deadline = System.nanoTime() + WAIT_FOR_SAMPLES_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (expectedThreadId.get() > 0) {
                Path dump = createTempJfrFile();
                recording.dump(dump);
                List<RecordedEvent> events = getEvents(dump, new String[]{JfrEvent.ExecutionSample.getName()}, true);
                if (hasResolvedExecutionSample(events, afterRotationOnly, expectedThreadId.get(), expectedMethodName)) {
                    return;
                }
            }
            Thread.sleep(SAMPLE_PERIOD.toMillis());
        }
        assertTrue("Timed out waiting for a resolved ExecutionSample event for the virtual thread.", false);
    }

    private boolean hasResolvedExecutionSample(List<RecordedEvent> events, boolean afterRotationOnly, long expectedThreadId, String expectedMethodName) {
        for (RecordedEvent event : events) {
            if (afterRotationOnly && !event.getEndTime().isAfter(rotationTime)) {
                continue;
            }
            if (!containsFrame(event, expectedMethodName)) {
                continue;
            }
            RecordedThread sampledThread = event.getThread("sampledThread");
            if (sampledThread != null && sampledThread.getJavaThreadId() == expectedThreadId) {
                return true;
            }
        }
        return false;
    }

    private void validateExecutionSamples(List<RecordedEvent> events, boolean afterRotationOnly, long expectedThreadId, String expectedMethodName) {
        assertTrue(expectedThreadId > 0);

        int matchingEvents = 0;
        int correctlyResolvedEvents = 0;
        int missingSampledThreadEvents = 0;
        int wrongSampledThreadEvents = 0;
        String firstMissingSampledThreadRawValue = null;
        for (RecordedEvent event : events) {
            if (afterRotationOnly && !event.getEndTime().isAfter(rotationTime)) {
                continue;
            }

            if (!containsFrame(event, expectedMethodName)) {
                continue;
            }

            RecordedThread sampledThread = event.getThread("sampledThread");
            matchingEvents++;
            if (sampledThread == null) {
                missingSampledThreadEvents++;
                if (firstMissingSampledThreadRawValue == null) {
                    Object rawSampledThread = event.getValue("sampledThread");
                    firstMissingSampledThreadRawValue = rawSampledThread == null ? "null" : rawSampledThread.getClass().getName() + ":" + rawSampledThread;
                }
            } else if (sampledThread.getJavaThreadId() == expectedThreadId) {
                correctlyResolvedEvents++;
            } else {
                wrongSampledThreadEvents++;
            }
        }

        String expectation = afterRotationOnly ? "post-rotation " : "";
        assertTrue("Expected at least one " + expectation + "ExecutionSample event for the virtual thread.", matchingEvents > 0);
        assertTrue("ExecutionSample resolved " + correctlyResolvedEvents + "/" + matchingEvents + " matching events, with " +
                        missingSampledThreadEvents + " missing sampledThread values and " + wrongSampledThreadEvents + " wrong sampledThread values. " +
                        "First missing raw sampledThread value: " + firstMissingSampledThreadRawValue,
                        correctlyResolvedEvents == matchingEvents);
    }

    private static boolean containsFrame(RecordedEvent event, String expectedMethodName) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        assertNotNull("ExecutionSample is missing a stack trace.", stackTrace);

        for (RecordedFrame frame : stackTrace.getFrames()) {
            if (frame.getMethod().getType().getName().equals(TestVirtualThreadsExecutionSample.class.getName()) &&
                            frame.getMethod().getName().equals(expectedMethodName)) {
                return true;
            }
        }
        return false;
    }

    private static void spinUntil(AtomicBoolean stop) {
        long value = 0;
        while (!stop.get()) {
            value++;
            if ((value & 0xFF) == 0) {
                Thread.onSpinWait();
            }
        }
        assertTrue(value > 0);
    }
}
