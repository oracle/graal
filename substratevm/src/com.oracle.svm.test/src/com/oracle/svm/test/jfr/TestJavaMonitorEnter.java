/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedThread;
import org.junit.Test;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

public class TestJavaMonitorEnter extends JfrTest {
    private static final int MILLIS = 60;

    static boolean inCritical = false;
    static Thread firstThread;
    static Thread secondThread;
    static final Helper helper = new Helper();

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorEnter"};
    }

    public void validateEvents() throws Throwable {
        List<RecordedEvent> events;
        events = getEvents("jdk.JavaMonitorEnter");
        int count = 0;
        boolean found = false;
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread> getValue("eventThread").getJavaName();
            if (struct.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName()) && event.getDuration().toMillis() >= MILLIS && secondThread.getName().equals(eventThread)) {

                // verify previous owner
                assertTrue("Previous owner is wrong", struct.<RecordedThread> getValue("previousOwner").getJavaName().equals(firstThread.getName()));
                found = true;
                break;
            }
        }
        assertTrue("Expected monitor blocked event not found", found);
    }

    @Test
    public void test() throws Exception {
        Runnable first = () -> {
            try {
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable second = () -> {
            try {
                // wait until lock is held
                while (!inCritical) {
                    Thread.sleep(10);
                }
                helper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        firstThread = new Thread(first);
        secondThread = new Thread(second);
        firstThread.start();
        secondThread.start();

        firstThread.join();
        secondThread.join();
    }

    static class Helper {
        private synchronized void doWork() throws InterruptedException {
            inCritical = true;
            if (Thread.currentThread().equals(secondThread)) {
                inCritical = false;
                return; // second thread doesn't need to do work.
            }

            // spin until second thread blocks
            while (!secondThread.getState().equals(Thread.State.BLOCKED)) {
                Thread.sleep(10);
            }

            Thread.sleep(MILLIS);
            inCritical = false;
        }
    }
}
