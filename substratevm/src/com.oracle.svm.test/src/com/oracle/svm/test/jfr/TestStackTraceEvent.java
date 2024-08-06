/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.jfr.consumer.RecordedThread;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.test.jfr.events.StackTraceEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;

/**
 * Test if event ({@link StackTraceEvent}) with stacktrace payload is working.
 */
public class TestStackTraceEvent extends JfrRecordingTest {

    private static final JfrSeenMethod junitTest = new JfrSeenMethod("test", "()V", 1);
    private static final JfrSeenMethod svmJunitMain = new JfrSeenMethod("main", "([Ljava/lang/String;)V", 9);
    private static final JfrSeenMethod javaMainRun = new JfrSeenMethod("doRun", "(ILorg/graalvm/nativeimage/c/type/CCharPointerPointer;)I", 10);

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{StackTraceEvent.class.getName()};
        Recording recording = startRecording(events);

        /*
         * Create and commit an event. This will trigger
         * com.oracle.svm.core.jfr.JfrStackTraceRepository.getStackTraceId(int) call and stack walk.
         */
        StackTraceEvent event = new StackTraceEvent();
        event.commit();

        stopRecording(recording, TestStackTraceEvent::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertEquals(1, events.size());
        RecordedEvent event = events.getFirst();

        long sampledThreadId = event.<RecordedThread> getValue("eventThread").getJavaThreadId();
        assertTrue(sampledThreadId > 0);

        RecordedStackTrace stackTrace = event.getStackTrace();
        assertNotNull(stackTrace);

        List<RecordedFrame> frames = stackTrace.getFrames();
        assertFalse(frames.isEmpty());

        Set<JfrSeenMethod> seenMethod = new HashSet<>();
        for (RecordedFrame frame : frames) {
            RecordedMethod method = frame.getMethod();
            assertNotNull(method);

            String methodName = method.getName();
            assertNotNull(methodName);
            assertFalse(methodName.isEmpty());

            String methodDescriptor = method.getDescriptor();
            assertNotNull(methodDescriptor);
            assertFalse(methodDescriptor.isEmpty());

            int methodModifiers = method.getModifiers();
            assertTrue(methodModifiers >= 0);

            seenMethod.add(new JfrSeenMethod(methodName, methodDescriptor, methodModifiers));
        }

        Assert.assertTrue(seenMethod.contains(junitTest));
        Assert.assertTrue(seenMethod.contains(javaMainRun));
        Assert.assertTrue(seenMethod.contains(svmJunitMain));
    }

    private record JfrSeenMethod(String name, String descriptor, int modifier) {

    }
}
