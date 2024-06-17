/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.List;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.svm.core.OS;
import com.oracle.svm.test.jfr.events.ThreadEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

/**
 * Test if event jdk.ContainerConfiguration is working.
 */
public class TestContainerEvent extends JfrRecordingTest {
    @Test
    public void test() throws Throwable {
        Assume.assumeTrue("Container support is limited to Linux", OS.LINUX.isCurrent());

        String[] events = new String[]{"jdk.ContainerConfiguration"};
        Recording recording = startRecording(events);

        // Do something; doesn't matter what
        ThreadEvent event = new ThreadEvent();
        event.thread = Thread.currentThread();
        event.commit();

        stopRecording(recording, TestContainerEvent::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertEquals(1, events.size());
        RecordedEvent re = events.getFirst();
        assertEquals(re.getEventType().getName(), "jdk.ContainerConfiguration");

        long hostTotalMem = re.getValue("hostTotalMemory");
        assertTrue(hostTotalMem > 0);

        if (JavaVersionUtil.JAVA_SPEC >= 23) {
            long hostTotalSwap = re.getValue("hostTotalSwapMemory");
            assertTrue("Host swap not implemented", hostTotalSwap < 0);
        }
    }
}
