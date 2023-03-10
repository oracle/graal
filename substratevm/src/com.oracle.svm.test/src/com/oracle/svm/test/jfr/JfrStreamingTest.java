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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.test.jfr.events.EndStreamEvent;
import com.oracle.svm.test.jfr.events.StartStreamEvent;

import jdk.jfr.Configuration;
import jdk.jfr.consumer.RecordingStream;

abstract class JfrStreamingTest extends AbstractJfrTest {
    public static final int JFR_MAX_SIZE = 100 * 1024 * 1024;

    protected final AtomicInteger emittedEventsPerType = new AtomicInteger(0);
    protected RecordingStream stream;
    private volatile boolean streamStarted = false;
    private volatile boolean streamEndedSuccessfully = false;

    @Override
    public void startRecording(Configuration config) throws Throwable {
        stream = new RecordingStream(config);
        stream.setMaxSize(JFR_MAX_SIZE);

        stream.enable("com.jfr.StartStream");
        stream.onEvent("com.jfr.StartStream", e -> {
            streamStarted = true;
        });

        stream.enable("com.jfr.EndStream");
        stream.onEvent("com.jfr.EndStream", e -> {
            stream.close();
            streamEndedSuccessfully = true;
        });
        enableEvents();
        startStream();
    }

    @Override
    public void stopRecording() throws Throwable {
        stream.dump(jfrFile);
        closeStream();
    }

    private void startStream() throws InterruptedException {
        stream.startAsync();
        /* Wait until the started thread can handle events. */
        waitUntilTrue(() -> {
            if (!streamStarted) {
                StartStreamEvent event = new StartStreamEvent();
                event.commit();
                return false;
            }
            return true;
        });
    }

    private void closeStream() throws InterruptedException {
        /*
         * We require a signal to close the stream, because if we close the stream immediately after
         * dumping, the dump may not have had time to finish.
         */
        EndStreamEvent event = new EndStreamEvent();
        event.commit();
        stream.awaitTermination(Duration.ofSeconds(10));
        assertTrue("unable to find stream end event signal in stream", streamEndedSuccessfully);
    }

    private void enableEvents() {
        /* Additionally, enable all events that the test case wants to test explicitly. */
        String[] events = getTestedEvents();
        for (String event : events) {
            stream.enable(event);
        }
    }

    static class MonitorWaitHelper {
        public synchronized void doEvent() {
            try {
                this.wait(0, 1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
