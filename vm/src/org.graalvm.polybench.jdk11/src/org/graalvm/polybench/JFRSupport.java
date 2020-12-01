/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import java.util.stream.Collectors;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

final class JFRSupport {

    private JFRSupport() {
    }

    static boolean isAvailable() {
        try {
            return FlightRecorder.isAvailable();
        } catch (LinkageError e) {
            // Thrown on the JDK-11 CE native-image without JFR support.
            return false;
        }
    }

    static Object startRecording(String enabledEvent) {
        Recording recording = new Recording();
        recording.enable(enabledEvent);
        recording.setDumpOnExit(false);
        recording.start();
        return recording;
    }

    static Object snapshotRecording(Object recording) {
        return ((Recording) recording).copy(true);
    }

    static void disposeRecording(Object recording, boolean stop) {
        if (recording != null) {
            Recording r = (Recording) recording;
            if (stop) {
                r.stop();
            }
            r.close();
        }
    }

    static long computeCumulativeTime(Object recording, String eventName, String fieldName) throws IOException {
        Path file = Files.createTempFile("recording", ".jfr");
        try {
            // Copy a JFR events snapshot into a temp file.
            ((Recording) recording).dump(file);
            // Calculate a cumulative Truffle compilation time from all events in the snapshot.
            return processRecordings(file, eventName, fieldName);
        } finally {
            Files.delete(file);
        }
    }

    private static long processRecordings(Path jfrFile, String eventName, String fieldName) throws IOException {
        Function<RecordedEvent, Duration> mapper = fieldName == null ? (event) -> event.getDuration() : (event) -> Duration.ofMillis(event.getLong(fieldName));
        return RecordingFile.readAllEvents(jfrFile).stream()
                        .filter((event) -> {
                            return eventName.equals(event.getEventType().getName());
                        })
                        .map(mapper)
                        .collect(Collectors.reducing(Duration.ofNanos(0), (a, b) -> a.plus(b)))
                        .toMillis();
    }
}
