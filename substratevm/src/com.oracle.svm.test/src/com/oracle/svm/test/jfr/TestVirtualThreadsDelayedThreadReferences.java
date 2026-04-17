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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestVirtualThreadsDelayedThreadReferences extends JfrRecordingTest {
    private static final String CONTENDER_NAME = "TestVirtualThreadsDelayedThreadReferences-contender";
    private static final String WAITER_NAME = "TestVirtualThreadsDelayedThreadReferences-waiter";
    private static final String CHILD_NAME = "TestVirtualThreadsDelayedThreadReferences-child";

    private Instant rotationTime;

    @Test
    public void testMonitorPreviousOwnerAfterChunkRotation() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorEnter.getName()};
        Recording recording = startRecording(events);

        MonitorEnterHelper helper = new MonitorEnterHelper();
        AtomicBoolean ownerHoldingMonitor = new AtomicBoolean(false);
        AtomicBoolean releaseOwner = new AtomicBoolean(false);
        AtomicLong expectedPreviousOwnerId = new AtomicLong();

        Thread owner = Thread.ofVirtual().start(() -> {
            synchronized (helper) {
                expectedPreviousOwnerId.set(Thread.currentThread().threadId());
                ownerHoldingMonitor.set(true);
                while (!releaseOwner.get()) {
                    Thread.onSpinWait();
                }
            }
        });

        waitUntilTrue(ownerHoldingMonitor::get);
        rotationTime = Instant.now();
        recording.dump(createTempJfrFile());

        Thread contender = new Thread(() -> {
            synchronized (helper) {
                // no-op
            }
        }, CONTENDER_NAME);
        contender.start();

        waitUntilTrue(() -> contender.getState() == Thread.State.BLOCKED);
        releaseOwner.set(true);

        owner.join();
        contender.join();
        stopRecording(recording, events0 -> validateMonitorPreviousOwner(events0, expectedPreviousOwnerId.get()));
    }

    @Test
    public void testMonitorNotifierAfterChunkRotation() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorWait.getName()};
        Recording recording = startRecording(events);

        WaitNotifyHelper helper = new WaitNotifyHelper();
        AtomicLong expectedNotifierId = new AtomicLong();

        Thread waiter = new Thread(() -> {
            try {
                helper.awaitSignal();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, WAITER_NAME);
        waiter.start();

        waitUntilTrue(() -> waiter.getState() == Thread.State.WAITING);
        rotationTime = Instant.now();
        recording.dump(createTempJfrFile());

        Thread notifier = Thread.ofVirtual().start(() -> {
            expectedNotifierId.set(Thread.currentThread().threadId());
            helper.signal();
        });

        notifier.join();
        waiter.join();
        stopRecording(recording, events0 -> validateMonitorNotifier(events0, expectedNotifierId.get()));
    }

    @Test
    public void testThreadStartParentAfterChunkRotation() throws Throwable {
        String[] events = new String[]{JfrEvent.ThreadStart.getName()};
        Recording recording = startRecording(events);

        rotationTime = Instant.now();
        recording.dump(createTempJfrFile());

        AtomicLong expectedParentThreadId = new AtomicLong();
        Thread parent = Thread.ofVirtual().start(() -> {
            expectedParentThreadId.set(Thread.currentThread().threadId());
            Thread child = new Thread(() -> LockSupport.parkNanos(1), CHILD_NAME);
            child.start();
            try {
                child.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        parent.join();
        stopRecording(recording, events0 -> validateThreadStartParent(events0, expectedParentThreadId.get()));
    }

    @Test
    public void testExecuteVmOperationCallerAfterChunkRotation() throws Throwable {
        String[] events = new String[]{JfrEvent.ExecuteVMOperation.getName()};
        Recording recording = startRecording(events);

        AtomicLong expectedCallerThreadId = new AtomicLong();
        AtomicBoolean callerReady = new AtomicBoolean(false);
        AtomicBoolean invokeVmOperation = new AtomicBoolean(false);
        Thread thread = Thread.ofVirtual().start(() -> {
            expectedCallerThreadId.set(Thread.currentThread().threadId());
            callerReady.set(true);
            while (!invokeVmOperation.get()) {
                Thread.onSpinWait();
            }
            System.gc();
        });

        waitUntilTrue(callerReady::get);
        rotationTime = Instant.now();
        recording.dump(createTempJfrFile());
        invokeVmOperation.set(true);
        thread.join();
        stopRecording(recording, events0 -> validateVmOperationCaller(events0, expectedCallerThreadId.get()));
    }

    private void validateMonitorPreviousOwner(List<RecordedEvent> events, long expectedPreviousOwnerId) {
        assertTrue(expectedPreviousOwnerId > 0);

        int matchingEvents = 0;
        for (RecordedEvent event : events) {
            if (!event.getEndTime().isAfter(rotationTime)) {
                continue;
            }

            if (!event.<RecordedClass> getValue("monitorClass").getName().equals(MonitorEnterHelper.class.getName())) {
                continue;
            }

            RecordedThread eventThread = event.getThread("eventThread");
            if (eventThread == null || !CONTENDER_NAME.equals(eventThread.getJavaName())) {
                continue;
            }

            RecordedThread previousOwner = event.getThread("previousOwner");
            assertNotNull("Virtual monitor previousOwner is missing after chunk rotation.", previousOwner);
            assertEquals(expectedPreviousOwnerId, previousOwner.getJavaThreadId());
            matchingEvents++;
        }

        assertTrue("Expected at least one JavaMonitorEnter event with a resolved virtual previousOwner after chunk rotation.", matchingEvents > 0);
    }

    private void validateMonitorNotifier(List<RecordedEvent> events, long expectedNotifierId) {
        assertTrue(expectedNotifierId > 0);

        int matchingEvents = 0;
        for (RecordedEvent event : events) {
            if (!event.getEndTime().isAfter(rotationTime)) {
                continue;
            }

            if (!event.<RecordedClass> getValue("monitorClass").getName().equals(WaitNotifyHelper.class.getName())) {
                continue;
            }

            RecordedThread eventThread = event.getThread("eventThread");
            if (eventThread == null || !WAITER_NAME.equals(eventThread.getJavaName())) {
                continue;
            }

            RecordedThread notifier = event.getThread("notifier");
            assertNotNull("Virtual monitor notifier is missing after chunk rotation.", notifier);
            assertEquals(expectedNotifierId, notifier.getJavaThreadId());
            matchingEvents++;
        }

        assertTrue("Expected at least one JavaMonitorWait event with a resolved virtual notifier after chunk rotation.", matchingEvents > 0);
    }

    private void validateThreadStartParent(List<RecordedEvent> events, long expectedParentThreadId) {
        assertTrue(expectedParentThreadId > 0);

        int matchingEvents = 0;
        for (RecordedEvent event : events) {
            if (!event.getEndTime().isAfter(rotationTime)) {
                continue;
            }

            RecordedThread startedThread = event.getThread("thread");
            if (startedThread == null || !CHILD_NAME.equals(startedThread.getJavaName())) {
                continue;
            }

            RecordedThread parentThread = event.getThread("parentThread");
            assertNotNull("Virtual ThreadStart parentThread is missing after chunk rotation.", parentThread);
            assertEquals(expectedParentThreadId, parentThread.getJavaThreadId());
            matchingEvents++;
        }

        assertTrue("Expected at least one ThreadStart event with a resolved virtual parentThread after chunk rotation.", matchingEvents > 0);
    }

    private void validateVmOperationCaller(List<RecordedEvent> events, long expectedCallerThreadId) {
        assertTrue(expectedCallerThreadId > 0);

        int matchingEvents = 0;
        for (RecordedEvent event : events) {
            if (!event.getEndTime().isAfter(rotationTime)) {
                continue;
            }

            RecordedThread caller = event.getThread("caller");
            if (caller != null && caller.getJavaThreadId() == expectedCallerThreadId) {
                matchingEvents++;
            }
        }

        assertTrue("Expected at least one ExecuteVMOperation event with a resolved virtual caller after chunk rotation.", matchingEvents > 0);
    }

    private static final class MonitorEnterHelper {
    }

    private static final class WaitNotifyHelper {
        private boolean signalled;

        synchronized void awaitSignal() throws InterruptedException {
            while (!signalled) {
                wait();
            }
        }

        synchronized void signal() {
            signalled = true;
            notifyAll();
        }
    }
}
