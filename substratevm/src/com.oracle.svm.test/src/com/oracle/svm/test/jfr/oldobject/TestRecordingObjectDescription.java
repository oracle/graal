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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

public class TestRecordingObjectDescription extends JfrOldObjectTest {
    @Test
    public void testThreadGroupName() throws Throwable {
        String name = "My Thread Group";
        String expectedDescription = "Thread Group: " + name;
        testDescription(new MyThreadGroup(name), expectedDescription);
    }

    @Test
    public void testThreadGroupEllipsis() throws Throwable {
        String name = "abcdef".repeat(20);
        String expectedDescription = "Thread Group: " + name.substring(0, 83) + "...";
        testDescription(new MyThreadGroup(name), expectedDescription);
    }

    @Test
    public void testThreadName() throws Throwable {
        String name = "My Thread";
        String expectedDescription = "Thread Name: My Thread";
        testDescription(new MyThread(name), expectedDescription);
    }

    @Test
    public void testClassName() throws Throwable {
        String expectedDescription = "Class Name: " + String.class.getName();
        testDescription(String.class, expectedDescription);
    }

    private void testDescription(Object obj, String expectedDescription) throws Throwable {
        testSampling(obj, Integer.MIN_VALUE, events -> validateEvents(events, obj.getClass(), expectedDescription));
    }

    private void validateEvents(List<RecordedEvent> events, Class<?> expectedSampledType, String expectedDescription) {
        List<RecordedEvent> matchingEvents = validateEvents(events, expectedSampledType, Integer.MIN_VALUE);
        for (RecordedEvent event : matchingEvents) {
            String description = event.<RecordedObject> getValue("object").getValue("description");
            Assert.assertEquals(expectedDescription, description);
        }
    }

    private static final class MyThreadGroup extends ThreadGroup {
        MyThreadGroup(String name) {
            super(name);
        }
    }

    private static final class MyThread extends Thread {
        MyThread(String name) {
            super(name);
        }
    }
}
