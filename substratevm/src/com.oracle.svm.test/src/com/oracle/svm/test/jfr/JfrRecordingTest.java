/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.After;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

/** Base class for JFR unit tests. */
public abstract class JfrRecordingTest extends AbstractJfrTest {
    private final Map<Recording, JfrRecordingState> recordingStates = Collections.synchronizedMap(new IdentityHashMap<>());

    @After
    public void cleanupRecordings() {
        /* Close all recordings in case that one remained open due to some error. */
        for (Entry<Recording, JfrRecordingState> entry : recordingStates.entrySet()) {
            entry.getKey().close();
        }
    }

    protected Recording startRecording(String[] events) throws Throwable {
        /* Enable a lot of events by default to increase the test coverage. */
        Configuration config = getDefaultConfiguration();
        return startRecording(events, config, null, createTempJfrFile());
    }

    protected Recording startRecording(String[] events, Configuration config) throws Throwable {
        return startRecording(events, config, null, createTempJfrFile());
    }

    protected Recording startRecording(String[] events, Configuration config, Map<String, String> settings) throws Throwable {
        return startRecording(events, config, settings, createTempJfrFile());
    }

    protected Recording startRecording(String[] events, Configuration config, Map<String, String> settings, Path path) throws Throwable {
        Recording recording = prepareRecording(events, config, settings, path);
        recording.start();
        return recording;
    }

    protected Recording prepareRecording(String[] events, Configuration config, Map<String, String> settings, Path path) throws IOException {
        Recording recording = createRecording(config);
        recordingStates.put(recording, new JfrRecordingState(events));

        recording.setDestination(path);
        if (settings != null) {
            recording.setSettings(settings);
        }
        enableEvents(recording, events);
        return recording;
    }

    private static Recording createRecording(Configuration config) {
        if (config == null) {
            return new Recording();
        } else {
            return new Recording(config);
        }
    }

    private static void enableEvents(Recording recording, String[] events) {
        /* Additionally, enable all events that the test case wants to test explicitly. */
        for (String event : events) {
            recording.enable(event).withThreshold(Duration.ZERO);
        }
    }

    public void stopRecording(Recording recording, EventValidator validator) throws Throwable {
        stopRecording(recording, validator, true);
    }

    public void stopRecording(Recording recording, EventValidator validator, boolean validateTestedEventsOnly) throws Throwable {
        recording.stop();
        recording.close();

        JfrRecordingState state = recordingStates.get(recording);
        checkRecording(validator, recording.getDestination(), state, validateTestedEventsOnly);
    }
}
