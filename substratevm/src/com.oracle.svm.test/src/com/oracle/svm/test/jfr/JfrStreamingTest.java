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

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.test.jfr.events.EndStreamEvent;

import jdk.jfr.consumer.RecordingStream;

abstract class StreamingTest extends JfrTest {
    protected static final int TIMEOUT_MILLIS = 3 * 1000;
    protected Path dumpLocation;
    protected AtomicInteger emittedEvents = new AtomicInteger(0);
    private RecordingStream rs;
    private volatile boolean streamEndedSuccessfully = false;

    protected RecordingStream createStream() {
        rs = new RecordingStream();
        rs.enable("com.jfr.EndStream");
        // close stream once we get the signal
        rs.onEvent("com.jfr.EndStream", e -> {
            rs.close();
            streamEndedSuccessfully = true;
        });
        return rs;
    }

    protected void closeStream() throws InterruptedException {
        // We require a signal to close the stream, because if we close the stream immediately after
        // dumping, the dump may not have had time to finish.
        EndStreamEvent endStreamEvent = new EndStreamEvent();
        endStreamEvent.commit();
        rs.awaitTermination(Duration.ofMillis(TIMEOUT_MILLIS));
        assertTrue("unable to find stream end event signal in stream", streamEndedSuccessfully);
    }
}
