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

package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.JfrRecordingTest;
import jdk.jfr.Recording;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class JfrOldObjectTest extends JfrRecordingTest {
    static Object leak;

    @Rule public TestName name = new TestName();

    @Before
    public void triggerUpdateOfLastKnownHeapUsage() {
        // Trigger GC before tests to get a reading of last known heap usage for the first executed
        // test.
        System.gc();
    }

    @After
    public void garbageCollectLeak() {
        // Force previous tests objects to be garbage collected.
        // The scavenge logic in the sampler should clear those from the queue.
        leak = null;
        System.gc();
        System.gc();
    }

    Recording startRecording() throws Throwable {
        final String[] events = {JfrEvent.OldObjectSample.getName()};
        Map<String, String> settings = new HashMap<>();
        settings.put("old-objects-stack-trace", "true");
        return startRecording(events, getDefaultConfiguration(), settings);
    }

    Collection<RecordedEvent> filterEventsByType(Class<?> type, List<RecordedEvent> events) {
        return filterEventsByTypeName(type.getName(), events);
    }

    Collection<RecordedEvent> filterEventsByTypeName(String typeName, List<RecordedEvent> events) {
        final List<RecordedEvent> filteredEvents = events.stream().filter(e -> typeName.equals(e.<RecordedObject> getValue("object").getClass("type").getName())).toList();
        Assert.assertFalse(filteredEvents.isEmpty());
        return filteredEvents;
    }

    void assertOldObjectEvent(RecordedEvent event) {
        final List<ValueDescriptor> fields = event.getFields();
        Assert.assertEquals(11, fields.size());
        Assert.assertEquals(0, event.getDuration().toMillis());
        Assert.assertTrue(event.getLong("lastKnownHeapUsage") > 0);
        Assert.assertTrue(event.getLong("objectAge") > 0);
        Assert.assertNull(event.getValue("root"));

        final List<RecordedFrame> frames = event.getStackTrace().getFrames();
        Assert.assertTrue(frames.size() > 0);
        Assert.assertTrue(frames.stream().anyMatch(e -> name.getMethodName().equals(e.getMethod().getName())));

        final long allocationTime = event.getLong("allocationTime");
        Assert.assertTrue(allocationTime > 0);

        final long objectSize = event.getLong("objectSize");
        Assert.assertTrue(objectSize > 0);

        final long startTime = event.getLong("startTime");
        Assert.assertTrue(startTime > 0);
        Assert.assertTrue(String.format("Allocation time (%d) should be earlier or same time as event start time (%d)", allocationTime, startTime), allocationTime <= startTime);

    }
}
