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

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;

public class TestThreadParkEvents extends JfrRecordingTest {
    private static final long MILLIS = 100;
    private static final long NANOS = MILLIS * 1_000_000;

    private final Blocker blocker = new Blocker();
    private volatile boolean passedCheckpoint = false;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ThreadPark.getName()};
        Recording recording = startRecording(events);

        LockSupport.parkNanos(NANOS);
        LockSupport.parkNanos(blocker, NANOS);
        LockSupport.parkUntil(System.currentTimeMillis() + MILLIS);

        Runnable waiter = () -> {
            passedCheckpoint = true;
            LockSupport.park();
        };

        Thread waiterThread = new Thread(waiter);
        waiterThread.start();
        while (!passedCheckpoint || !waiterThread.getState().equals(Thread.State.WAITING)) {
            Thread.yield();
        }

        LockSupport.unpark(waiterThread);
        waiterThread.join();

        stopRecording(recording, TestThreadParkEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean parkNanosFound = false;
        boolean parkNanosFoundBlocker = false;
        boolean parkUntilFound = false;
        boolean parkUnparkFound = false;

        for (RecordedEvent event : events) {
            long timeout = event.<Long> getValue("timeout");
            long until = event.<Long> getValue("until");

            if (timeout == NANOS) {
                RecordedClass parkedClass = event.getValue("parkedClass");
                if (parkedClass == null) {
                    parkNanosFound = true;
                } else if (parkedClass.getName().equals(Blocker.class.getName())) {
                    parkNanosFoundBlocker = true;
                }
            } else if (timeout < 0) {
                if (until < 0) {
                    parkUnparkFound = true;
                } else if (until > 0) {
                    parkUntilFound = true;
                }
            }
        }

        assertTrue(parkNanosFound);
        assertTrue(parkNanosFoundBlocker);
        assertTrue(parkUntilFound);
        assertTrue(parkUnparkFound);
    }

    private static class Blocker {
    }
}
