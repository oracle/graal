/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import com.oracle.svm.core.jfr.JfrEnabled;
import com.oracle.svm.test.jfr.utils.JFR;
import com.oracle.svm.test.jfr.utils.JFRFileParser;
import com.oracle.svm.test.jfr.utils.LocalJFR;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

/** Base class for JFR unit tests. */
public abstract class JFRTest {

    protected JFR jfr;
    protected Recording recording;

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || JfrEnabled.get());
    }

    @Before
    public void startRecording() {
        try {
            jfr = new LocalJFR();
            recording = jfr.startRecording(getClass().getName());
        } catch (Exception e) {
            Assert.fail("Fail to start recording! Cause: " + e.getMessage());
        }
    }

    @After
    public void endRecording() {
        try {
            jfr.endRecording(recording);
            try (RecordingFile recordingFile = new RecordingFile(recording.getDestination())) {
                assertNotNull(recordingFile);
                JFRFileParser.parse(recording);
            } finally {
                jfr.cleanupRecording(recording);
            }
        } catch (Exception e) {
            Assert.fail("Fail to stop recording! Cause: " + e.getMessage());
        }
    }
}
