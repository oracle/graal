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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.After;

import com.oracle.svm.test.jfr.events.EndStreamEvent;
import com.oracle.svm.test.jfr.events.StartStreamEvent;

import jdk.jfr.Configuration;
import jdk.jfr.consumer.RecordingStream;

public abstract class JfrStreamingTest extends AbstractJfrTest {
    private static final int JFR_MAX_SIZE = 100 * 1024 * 1024;
    private final Map<RecordingStream, JfrStreamState> streamStates = Collections.synchronizedMap(new IdentityHashMap<>());

    @After
    public void cleanupStreams() {
        /* Close all streams in case that one remained open due to some error. */
        for (Entry<RecordingStream, JfrStreamState> entry : streamStates.entrySet()) {
            entry.getKey().close();
        }
    }

    protected RecordingStream startStream(String[] events) throws Throwable {
        Configuration config = getDefaultConfiguration();
        RecordingStream stream = new RecordingStream(config);
        streamStates.put(stream, new JfrStreamState(events));

        stream.setMaxSize(JFR_MAX_SIZE);

        stream.enable("com.jfr.StartStream");
        stream.onEvent("com.jfr.StartStream", _ -> streamStates.get(stream).started = true);

        stream.enable("com.jfr.EndStream");
        stream.onEvent("com.jfr.EndStream", _ -> {
            stream.close();
            streamStates.get(stream).endedSuccessfully = true;
        });
        enableEvents(stream, events);
        startStream(stream);
        return stream;
    }

    protected void stopStream(RecordingStream stream, EventValidator validator) throws Throwable {
        stopStream(stream, validator, true);
    }

    protected void stopStream(RecordingStream stream, EventValidator validator, boolean validateTestedEventsOnly) throws Throwable {
        Path jfrFile = createTempJfrFile();
        stream.dump(jfrFile);
        closeStream(stream);

        JfrStreamState state = streamStates.get(stream);
        checkRecording(validator, jfrFile, state, validateTestedEventsOnly);
    }

    private void startStream(RecordingStream stream) throws InterruptedException {
        stream.startAsync();
        JfrStreamState state = streamStates.get(stream);
        /* Wait until the started thread can handle events. */
        waitUntilTrue(() -> {
            if (!state.started) {
                StartStreamEvent event = new StartStreamEvent();
                event.commit();
                return false;
            }
            return true;
        });
    }

    private void closeStream(RecordingStream stream) throws InterruptedException {
        /*
         * We require a signal to close the stream, because if we close the stream immediately after
         * dumping, the dump may not have had time to finish.
         */
        EndStreamEvent event = new EndStreamEvent();
        event.commit();
        stream.awaitTermination(Duration.ofSeconds(10));
        assertTrue("unable to find stream end event signal in stream", streamStates.get(stream).endedSuccessfully);
    }

    private static void enableEvents(RecordingStream stream, String[] events) {
        /* Additionally, enable all events that the test case wants to test explicitly. */
        for (String event : events) {
            stream.enable(event).withThreshold(Duration.ZERO);
        }
    }

    private static class JfrStreamState extends JfrRecordingState {
        volatile boolean started = false;
        volatile boolean endedSuccessfully = false;

        JfrStreamState(String[] testedEvents) {
            super(testedEvents);
        }
    }
}
