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

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.Assert;
import org.junit.Test;

public class TestRecordingArrayLeak extends JfrOldObjectTest {
    @Test
    public void testArrayLeak() throws Throwable {
        Recording recording = startRecording();

        Object[] node = new Object[3];
        leak = node;
        for (int i = 0; i < 10_000; i++) {
            Object[] value = new Object[100];
            node[0] = value;
            Object[] left = new Object[3];
            node[1] = left;
            Object[] right = new Object[3];
            node[2] = right;
            node = right;
        }

        stopRecording(recording, events -> filterEventsByTypeName("[Ljava.lang.Object;", events).forEach(this::assertRecordedEvent));
    }

    private void assertRecordedEvent(RecordedEvent event) {
        assertOldObjectEvent(event);
        final int arraySize = event.getInt("arrayElements");
        Assert.assertTrue("Unexpected array size: " + arraySize, arraySize == 100 || arraySize == 3);
    }
}
