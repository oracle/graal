/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import jdk.jfr.consumer.RecordedEvent;

/**
 * Test if event ThreadCPULoad is generated after a thread exit.
 */
public class TestThreadCPULoadEvent extends JfrRecordingTest {

    private static final int MILLIS = 50;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.ThreadCPULoad"};
    }

    @Override
    protected void validateEvents(List<RecordedEvent> events) throws Throwable {
        assertEquals(1, events.size());
    }

    @Test
    public void test() throws Exception {
        Thread thread = new Thread(() -> {
            long time = System.currentTimeMillis() + MILLIS;
            do {
                writeToFile();
            } while (time > System.currentTimeMillis());
        });
        thread.start();
        thread.join();
    }

    private static void writeToFile() {
        int fileSize = 1000;
        try {
            File temp = File.createTempFile("TestThreadCPULoadEventData_", ".tmp");
            temp.deleteOnExit();
            try (RandomAccessFile file = new RandomAccessFile(temp, "rw")) {
                for (int i = 0; i < fileSize; i++) {
                    file.writeByte(i);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
