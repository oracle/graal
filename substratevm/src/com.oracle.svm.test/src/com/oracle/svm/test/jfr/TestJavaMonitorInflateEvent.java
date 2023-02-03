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
import org.junit.Test;
import com.oracle.svm.core.jfr.JfrEvent;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorInflateEvent extends JfrTest {
    private static final int MILLIS = 60;

    static volatile boolean passedCheckpoint = false;
    static Thread firstThread;
    static Thread secondThread;
    static final EnterHelper enterHelper = new EnterHelper();

    @Override
    public String[] getTestedEvents() {
        return new String[]{JfrEvent.JavaMonitorInflate.getName()};
    }

    @Override
    public void validateEvents() throws Throwable {
        boolean foundCauseEnter = false;
        for (RecordedEvent event : getEvents()) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(EnterHelper.class.getName()) && firstThread.getName().equals(eventThread) &&
                            event.<String> getValue("cause").equals("Monitor Enter")) {
                foundCauseEnter = true;
            }
        }
        assertTrue("Expected monitor inflate event not found.", foundCauseEnter);
    }

    @Test
    public void test() throws Exception {
        Runnable first = () -> {
            try {
                enterHelper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable second = () -> {
            try {
                passedCheckpoint = true;
                enterHelper.doWork();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        firstThread = new Thread(first);
        secondThread = new Thread(second);

        // Generate event with "Monitor Enter" cause
        firstThread.start();

        firstThread.join();
        secondThread.join();
    }

    static class EnterHelper {
        private synchronized void doWork() throws InterruptedException {
            if (Thread.currentThread().equals(secondThread)) {
                return; // second thread doesn't need to do work.
            }
            // ensure ordering of critical section entry
            secondThread.start();

            // spin until second thread blocks
            while (!secondThread.getState().equals(Thread.State.BLOCKED) || !passedCheckpoint) {
                Thread.sleep(10);
            }
            Thread.sleep(MILLIS);
        }
    }
}
