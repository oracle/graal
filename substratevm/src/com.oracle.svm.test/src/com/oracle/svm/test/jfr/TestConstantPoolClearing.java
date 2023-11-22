/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;
import com.oracle.svm.test.jfr.utils.JfrFileParser;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This test ensures that constant pool epochData is properly cleared before beginning a new chunk
 * or recording. Upon a new chunk, a constant pool epochData starts with residual entries, while the
 * serialized data buffer does not, it will fail constant pool verification in {@link JfrFileParser}
 */
public class TestConstantPoolClearing extends JfrRecordingTest {
    @Test
    public void test() throws Throwable {
        /* Turn off flushing so we can control it precisely. */
        Map<String, String> settings = new HashMap<>();
        settings.put("flush-interval", "0");

        String[] events = new String[]{"com.jfr.Class"};
        Recording recA = startRecording(events, getDefaultConfiguration(), settings);

        /* Emit an event. */
        emitEvent();

        /* Flush so that there is no more unflushed data and rotate the chunk (dump). */
        flushAllThreads();
        recA.dump(createTempJfrFile());

        /* Emit the same event again - it must not reference data from the previous chunk. */
        emitEvent();

        /* Flush so that there is no more unflushed data and rotate the chunk (stopRecording). */
        flushAllThreads();
        stopRecording(recA, TestConstantPoolClearing::validateFirstRecording);

        /* Start a new recording. */
        Recording recB = startRecording(events, getDefaultConfiguration(), settings);

        /* Emit the same event again - it must not reference data from the previous recording. */
        emitEvent();

        stopRecording(recB, TestConstantPoolClearing::validateSecondRecording);
    }

    private static void emitEvent() {
        ClassEvent eventA = new ClassEvent();
        eventA.clazz = TestConstantPoolClearing.class;
        eventA.commit();
    }

    private static void validateFirstRecording(List<RecordedEvent> events) {
        assertEquals(2, events.size());
        validateEvents(events);
    }

    private static void validateSecondRecording(List<RecordedEvent> events) {
        assertEquals(1, events.size());
        validateEvents(events);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        for (RecordedEvent event : events) {
            assertNotNull(event.getThread());
            assertNotNull(event.<RecordedClass> getValue("clazz"));
        }
    }
}
