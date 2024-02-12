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

import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.test.jfr.utils.JfrFileParser;

import jdk.jfr.Configuration;
import jdk.jfr.Unsigned;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Base class for JFR unit tests. */
public abstract class AbstractJfrTest {
    private final ArrayList<Path> jfrFiles = new ArrayList<>();

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || HasJfrSupport.get());
    }

    @After
    public void deleteTemporaryFiles() throws Throwable {
        if (!isDebuggingEnabled()) {
            for (Path f : jfrFiles) {
                Files.deleteIfExists(f);
            }
        }
    }

    protected Path createTempJfrFile() throws IOException {
        long id = new Random().nextLong(0, Long.MAX_VALUE);
        Path result = File.createTempFile(getClass().getName() + "-" + id, ".jfr").toPath();
        jfrFiles.add(result);
        if (isDebuggingEnabled()) {
            System.out.println("JFR file: " + result);
        }
        return result;
    }

    protected static Configuration getDefaultConfiguration() throws Throwable {
        return Configuration.getConfiguration("default");
    }

    protected static void checkRecording(EventValidator validator, Path path, JfrRecordingState state, boolean validateTestedEventsOnly) throws Throwable {
        JfrFileParser parser = new JfrFileParser(path);
        parser.verify();

        List<RecordedEvent> events = getEvents(path, state.testedEvents, validateTestedEventsOnly);
        checkEvents(events, state.testedEvents);
        if (validator != null) {
            validator.validate(events);
        }
    }

    protected static List<RecordedEvent> getEvents(Path path, String[] testedEvents, boolean validateTestedEventsOnly) throws IOException {
        /* Only return events that are in the list of tested events. */
        ArrayList<RecordedEvent> result = new ArrayList<>();
        for (RecordedEvent event : RecordingFile.readAllEvents(path)) {
            if (!validateTestedEventsOnly || isTestedEvent(event, testedEvents)) {
                result.add(event);
            }
        }
        result.sort(new ChronologicalComparator());
        return result;
    }

    private static boolean isTestedEvent(RecordedEvent event, String[] testedEvents) {
        for (String tested : testedEvents) {
            if (tested.equals(event.getEventType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static void checkEvents(List<RecordedEvent> events, String[] testedEventTypes) {
        HashSet<String> seenEventTypes = new HashSet<>();
        for (RecordedEvent event : events) {
            String eventName = event.getEventType().getName();
            seenEventTypes.add(eventName);
        }

        for (String testedEvent : testedEventTypes) {
            if (!seenEventTypes.contains(testedEvent)) {
                Assert.fail("Event: " + testedEvent + " not found in recording!");
            }
        }
    }

    protected static void flushAllThreads() {
        if (HasJfrSupport.get()) {
            SubstrateJVM.get().flush();
        }
    }

    protected static void waitUntilTrue(BooleanSupplier supplier) throws InterruptedException {
        long timeout = TimeUtils.secondsToNanos(30);
        long startTime = System.nanoTime();
        while (!supplier.getAsBoolean()) {
            long elapsedNanos = System.nanoTime() - startTime;
            if (elapsedNanos > timeout) {
                Assert.fail("Timed out after: " + TimeUtils.divideNanosToMillis(timeout) + "ms.");
            }
            Thread.sleep(10);
        }
    }

    private static boolean isDebuggingEnabled() {
        String debugRecording = System.getenv("DEBUG_RECORDING");
        return debugRecording != null && !"false".equals(debugRecording);
    }

    private static class ChronologicalComparator implements Comparator<RecordedEvent> {
        @Override
        public int compare(RecordedEvent e1, RecordedEvent e2) {
            return e1.getEndTime().compareTo(e2.getEndTime());
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

    static class JfrRecordingState {
        String[] testedEvents;

        JfrRecordingState(String[] testedEvents) {
            this.testedEvents = testedEvents;
        }
    }

    @FunctionalInterface
    protected interface EventValidator {
        void validate(List<RecordedEvent> events) throws Throwable;
    }
}

class JfrTestFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /* Needed so that the tests can call RecordedObject.getLong(). */
        RuntimeProxyCreation.register(Unsigned.class);
    }
}
