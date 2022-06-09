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

import java.util.HashSet;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.test.jfr.utils.Jfr;
import com.oracle.svm.test.jfr.utils.JfrFileParser;
import com.oracle.svm.test.jfr.utils.LocalJfr;
import com.oracle.svm.util.ModuleSupport;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Base class for JFR unit tests. */
public abstract class JfrTest {
    protected Jfr jfr;
    protected Recording recording;

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || HasJfrSupport.get());
    }

    @Before
    public void startRecording() {
        try {
            jfr = new LocalJfr();
            recording = jfr.createRecording(getClass().getName());

            String[] events = getTestedEvents();
            enableEvents(events);

            jfr.startRecording(recording);
        } catch (Exception e) {
            Assert.fail("Fail to start recording! Cause: " + e.getMessage());
        }
    }

    @After
    public void endRecording() {
        try {
            jfr.endRecording(recording);
        } catch (Exception e) {
            Assert.fail("Fail to stop recording! Cause: " + e.getMessage());
        }

        try {
            checkRecording();
        } finally {
            try {
                jfr.cleanupRecording(recording);
            } catch (Exception e) {
                Assert.fail("Fail to cleanup recording! Cause: " + e.getMessage());
            }
        }
    }

    protected void enableEvents(String[] events) {
        if (events != null) {
            for (String event : events) {
                recording.enable(event);
            }
        }
    }

    public abstract String[] getTestedEvents();

    protected void checkEvents() {
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
                Assert.fail("Event: " + name + " not found in recording");
            }
        }
    }

    protected void checkRecording() throws AssertionError {
        try {
            JfrFileParser.parse(recording);
        } catch (Exception e) {
            Assert.fail("Failed to parse recording: " + e.getMessage());
        }
    }
}

class JFRTestFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /*
         * Use of org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPEC in
         * com.oracle.svm.test.jfr.utils.poolparsers.ClassConstantPoolParser.parse
         */
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, JFRTestFeature.class, false, "jdk.internal.vm.compiler", "org.graalvm.compiler.serviceprovider");
    }
}
