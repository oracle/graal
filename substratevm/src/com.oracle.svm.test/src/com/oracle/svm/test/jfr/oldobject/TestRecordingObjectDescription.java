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

import com.oracle.svm.core.jfr.SubstrateJVM;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

public class TestRecordingObjectDescription extends JfrOldObjectTest {
    @Test
    public void testThreadGroupName() throws Throwable {
        Recording recording = startRecording();
        sample(new MyThreadGroup("My Thread Group"));
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(e -> assertDescription("Thread Group: My Thread Group", e)));
    }

    @Test
    public void testThreadGroupEllipsis() throws Throwable {
        final int objectDescriptionMaxSize = 100;
        final String threadGroupName = "x".repeat(2 * objectDescriptionMaxSize);

        Recording recording = startRecording();
        sample(new MyThreadGroup(threadGroupName));
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(e -> assertDescriptionLimit("xxx...", objectDescriptionMaxSize, e)));
    }

    @Test
    public void testThreadName() throws Throwable {
        Recording recording = startRecording();
        sample(new MyThread("My Thread"));
        stopRecording(recording, events -> filterEventsByType(MyThread.class, events).forEach(e -> assertDescription("Thread Name: My Thread", e)));
    }

    @Test
    public void testClassName() throws Throwable {
        Recording recording = startRecording();
        sample(String.class);
        stopRecording(recording, events -> filterEventsByType(Class.class, events).forEach(e -> assertDescription("Class Name: java.lang.String", e)));
    }

    private static void sample(Object obj) {
        // For single-shot sampling attempts, loop until sampling in successful.
        // This is needed because other internal threads could be trying to sample too,
        // e.g. JFR Periodic Tasks.
        long waitTimeSec = 5;
        long endTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(waitTimeSec);
        boolean success;
        long sleepTime;
        do {
            success = SubstrateJVM.getJfrOldObjectProfiler().sample(new WeakReference<>(obj), 1_000, Integer.MIN_VALUE);
            sleepTime = endTime - System.nanoTime();
        } while (!success && sleepTime > 0);

        if (!success) {
            throw new AssertionError("Timed out waiting for sampling to complete");
        }
    }

    private static void assertDescription(String expected, RecordedEvent event) {
        final String description = event.<RecordedObject> getValue("object").getValue("description");
        Assert.assertEquals(expected, description);
    }

    private static void assertDescriptionLimit(String expected, int expectedSize, RecordedEvent event) {
        final String description = event.<RecordedObject> getValue("object").getValue("description");
        Assert.assertEquals(expectedSize, description.length());
        Assert.assertTrue(description.contains(expected));
        Assert.assertTrue(description.contains("Thread Group: x"));
    }

    public static final class MyThreadGroup extends ThreadGroup {
        public MyThreadGroup(String name) {
            super(name);
        }
    }

    public static final class MyThread extends Thread {
        public MyThread(String name) {
            super(name);
        }
    }
}
