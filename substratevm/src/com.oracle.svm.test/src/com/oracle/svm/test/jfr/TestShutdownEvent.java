/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import com.oracle.svm.core.jdk.Target_java_lang_Shutdown;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.events.ShutdownEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;

public class TestShutdownEvent extends JfrRecordingTest {
    private static final String SHUTDOWN_REQUESTED_FROM_JAVA = "Shutdown requested from Java";

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.Shutdown.getName()};
        Recording recording = startRecording(events);

        Target_java_lang_Shutdown.beforeHalt();

        stopRecording(recording, TestShutdownEvent::validateEventsWithStackTrace);
    }

    private static void validateEventsWithStackTrace(List<RecordedEvent> events) {
        assertEquals(1, events.size());

        RecordedEvent event = events.getFirst();
        assertEquals(SHUTDOWN_REQUESTED_FROM_JAVA, event.getString("reason"));

        List<RecordedFrame> frames = event.getStackTrace().getFrames();
        assertFalse(frames.isEmpty());
        RecordedFrame topFrame = frames.getFirst();
        assertEquals("java.lang.Shutdown", topFrame.getMethod().getType().getName());
        assertEquals("beforeHalt", topFrame.getMethod().getName());
    }

    @Test
    public void testWithoutStackTrace() throws Throwable {
        String[] events = new String[]{JfrEvent.Shutdown.getName()};
        Recording recording = startRecording(events);

        ShutdownEvent.emit("No remaining non-daemon Java threads", false);

        stopRecording(recording, TestShutdownEvent::validateEventsWithoutStackTrace);
    }

    private static void validateEventsWithoutStackTrace(List<RecordedEvent> events) {
        assertEquals(1, events.size());

        RecordedEvent first = events.getFirst();
        assertEquals("No remaining non-daemon Java threads", first.getString("reason"));
        assertNull(first.getStackTrace());
    }
}
