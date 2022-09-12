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

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TestThreadSleep extends JfrTest {
    private String sleepingThreadName;
    private static final int MILLIS = 50;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.ThreadSleep"};
    }

    @Override
    public void analyzeEvents() {
        List<RecordedEvent> events;
        try {
            events = getEvents(recording, "jdk.ThreadSleep");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        boolean foundSleepEvent = false;
        for (RecordedEvent event : events) {
            if (!event.getEventType().getName().equals("jdk.ThreadSleep")) {
                continue;
            }
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread> getValue("eventThread").getJavaName();
            if (!eventThread.equals(sleepingThreadName)) {
                continue;
            }
            if (!isEqualDuration(event.getDuration(), Duration.ofMillis(MILLIS))) {
                continue;
            }
            foundSleepEvent = true;
            break;
        }
        assertTrue("Sleep event not found.", foundSleepEvent);
    }

    @Test
    public void test() throws Exception {
        sleepingThreadName = Thread.currentThread().getName();
        Thread.sleep(MILLIS);
    }
}
