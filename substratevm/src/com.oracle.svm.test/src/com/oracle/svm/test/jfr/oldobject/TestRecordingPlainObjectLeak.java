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

public class TestRecordingPlainObjectLeak extends JfrOldObjectTest {
    private static final int DEFAULT_OLD_OBJECT_QUEUE_SIZE = 256;

    @Test
    public void testObjectLeak() throws Throwable {
        Recording recording = startRecording();

        Node node = new Node();
        leak = node;
        for (int i = 0; i < 10_000; i++) {
            node.value = new Node();
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }
        // Trigger a GC so that last sweep gets updated,
        // and the objects above are considered older than last GC sweep time.
        System.gc();

        stopRecording(recording, events -> {
            Assert.assertTrue(events.size() < DEFAULT_OLD_OBJECT_QUEUE_SIZE);
            filterEventsByType(Node.class, events).forEach(this::assertRecordedEvent);
        });
    }

    private void assertRecordedEvent(RecordedEvent event) {
        assertOldObjectEvent(event);
        Assert.assertEquals(Integer.MIN_VALUE, event.getInt("arrayElements"));
    }

    static class Node {
        Node left;
        Node right;
        Object value;
    }
}
