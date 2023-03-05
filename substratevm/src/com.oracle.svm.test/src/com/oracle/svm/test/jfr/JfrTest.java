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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.test.jfr.utils.Jfr;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.LocalJfr;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ModuleSupport;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Base class for JFR unit tests. */
public abstract class JfrTest {
    private Jfr jfr;
    private Recording recording;

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || HasJfrSupport.get());
    }

    @Before
    public void startRecording() throws Throwable {
        jfr = new LocalJfr();
        recording = jfr.createRecording(getClass().getName());
        enableEvents();
        jfr.startRecording(recording);
    }

    @After
    public void endRecording() throws Throwable {
        try {
            jfr.endRecording(recording);
            checkRecording();
            validateEvents();
        } finally {
            jfr.cleanupRecording(recording);
        }
    }

    protected abstract String[] getTestedEvents();

    private void enableEvents() {
        /* Additionally, enable all events that the test case wants to test explicitly. */
        String[] events = getTestedEvents();
        for (String event : events) {
            recording.enable(event);
        }
    }

    public void validateEvents() throws Throwable {
    }

    private void checkEvents() {
        HashSet<String> seenEvents = new HashSet<>();
        try (RecordingFile recordingFile = new RecordingFile(recording.getDestination())) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                String eventName = event.getEventType().getName();
                seenEvents.add(eventName);
            }
        } catch (Exception e) {
            Assert.fail("Failed to read events: " + e.getMessage());
        }

        for (String name : getTestedEvents()) {
            if (!seenEvents.contains(name)) {
                Assert.fail("Event: " + name + " not found in recording!");
            }
        }
    }

    private void checkRecording() throws AssertionError {
        try {
            /* Check if file header and constant pools are adequate. */
            JfrFileParser.parse(recording);
            /* Check if all event are there. */
            checkEvents();
        } catch (Exception e) {
            Assert.fail("Failed to parse recording: " + e.getMessage());
        }
    }

    private static class ChronologicalComparator implements Comparator<RecordedEvent> {
        @Override
        public int compare(RecordedEvent e1, RecordedEvent e2) {
            return e1.getEndTime().compareTo(e2.getEndTime());
        }
    }

    private Path makeCopy(String testName) throws IOException { // from jdk 19
        Path p = recording.getDestination();
        if (p == null) {
            File directory = new File(".");
            p = new File(directory.getAbsolutePath(), "recording-" + recording.getId() + "-" + testName + ".jfr").toPath();
            recording.dump(p);
        }
        return p;
    }

    protected List<RecordedEvent> getEvents() throws IOException {
        Path p = makeCopy(ClassUtil.getUnqualifiedName(getClass()));
        return getEvents0(p);
    }

    protected List<RecordedEvent> getEvents(Path p) throws IOException {
        List<RecordedEvent> events = getEvents0(p);
        Files.deleteIfExists(p);
        return events;
    }

    private List<RecordedEvent> getEvents0(Path p) throws IOException {
        List<RecordedEvent> events = RecordingFile.readAllEvents(p);
        Collections.sort(events, new ChronologicalComparator());
        /* Remove events that are not in the list of tested events. */
        events.removeIf(event -> (Arrays.stream(getTestedEvents()).noneMatch(testedEvent -> (testedEvent.equals(event.getEventType().getName())))));
        return events;
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
}

class JfrTestFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /*
         * Use of org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPEC in
         * com.oracle.svm.test.jfr.utils.poolparsers.ClassConstantPoolParser.parse
         */
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, JfrTestFeature.class, false, "jdk.internal.vm.compiler", "org.graalvm.compiler.serviceprovider");
    }
}
