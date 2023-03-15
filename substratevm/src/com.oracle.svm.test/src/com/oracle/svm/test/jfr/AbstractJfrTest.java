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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.util.ModuleSupport;

import jdk.jfr.Configuration;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Base class for JFR unit tests. */
public abstract class AbstractJfrTest {
    protected Path jfrFile;

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || HasJfrSupport.get());
    }

    @Before
    public void beforeTest() throws Throwable {
        JfrFileParser.resetConstantPoolParsers();

        long id = new Random().nextLong(0, Long.MAX_VALUE);
        jfrFile = File.createTempFile(getClass().getName() + "-" + id, ".jfr").toPath();
        if (isDebuggingEnabled()) {
            System.out.println("Recording: " + jfrFile);
        }

        Configuration defaultConfig = Configuration.getConfiguration("default");
        startRecording(defaultConfig);
    }

    @After
    public void afterTest() throws Throwable {
        try {
            stopRecording();
            checkRecording(jfrFile);
            validateEvents(getEvents());
        } finally {
            if (!isDebuggingEnabled()) {
                Files.deleteIfExists(jfrFile);
            }
        }
    }

    protected abstract void startRecording(Configuration config) throws Throwable;

    protected abstract void stopRecording() throws Throwable;

    protected abstract String[] getTestedEvents();

    protected abstract void validateEvents(List<RecordedEvent> events) throws Throwable;

    protected void checkRecording(Path path) throws AssertionError {
        try {
            /* Check if file header and constant pools are adequate. */
            JfrFileParser.parse(path);
            /* Check if all event are there. */
            checkEvents(path);
        } catch (Exception e) {
            Assert.fail("Failed to parse recording: " + e.getMessage());
        }
    }

    protected void checkEvents(Path path) {
        HashSet<String> seenEvents = new HashSet<>();
        try (RecordingFile recordingFile = new RecordingFile(path)) {
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

    protected List<RecordedEvent> getEvents() throws IOException {
        /* Only return events that are in the list of tested events. */
        ArrayList<RecordedEvent> result = new ArrayList<>();
        for (RecordedEvent event : RecordingFile.readAllEvents(jfrFile)) {
            if (isTestedEvent(event)) {
                result.add(event);
            }
        }
        result.sort(new ChronologicalComparator());
        return result;
    }

    private boolean isTestedEvent(RecordedEvent event) {
        for (String tested : getTestedEvents()) {
            if (tested.equals(event.getEventType().getName())) {
                return true;
            }
        }
        return false;
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
