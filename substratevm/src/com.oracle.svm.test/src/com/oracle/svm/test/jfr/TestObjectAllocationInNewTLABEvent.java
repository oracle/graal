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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

import java.util.Arrays;

public class TestObjectAllocationInNewTLABEvent extends JfrTest {
    static final int KILO = 1024;
    // the default size for serial and epsilon GC in SVM.
    static final int DEFAULT_ALIGNED_HEAP_CHUNK_SIZE = KILO * KILO;

    public static Helper helper = null;
    public static byte[] byteArray = null;

    @Override
    public String[] getTestedEvents() {
        return new String[]{JfrEvent.ObjectAllocationInNewTLAB.getName()};
    }

    @Override
    public void validateEvents() throws Throwable {
        boolean foundBigByte = false;
        boolean foundSmallByte = false;
        boolean foundBigChar = false;
        boolean foundInstance = false;
        for (RecordedEvent event : getEvents()) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (!eventThread.equals("main")) {
                continue;
            }
            // >= To account for size of reference
            if (event.<Long> getValue("allocationSize").longValue() >= (2 * DEFAULT_ALIGNED_HEAP_CHUNK_SIZE) &&
                            event.<Long> getValue("tlabSize").longValue() >= (2 * DEFAULT_ALIGNED_HEAP_CHUNK_SIZE)) {
                // verify previous owner
                if (event.<RecordedClass> getValue("objectClass").getName().equals(char[].class.getName())) {
                    foundBigChar = true;
                } else if (event.<RecordedClass> getValue("objectClass").getName().equals(byte[].class.getName())) {
                    foundBigByte = true;
                }
            } else if (event.<Long> getValue("allocationSize").longValue() >= KILO && event.<Long> getValue("tlabSize").longValue() == (DEFAULT_ALIGNED_HEAP_CHUNK_SIZE) &&
                            event.<RecordedClass> getValue("objectClass").getName().equals(byte[].class.getName())) {
                foundSmallByte = true;
            } else if (event.<Long> getValue("tlabSize").longValue() == (DEFAULT_ALIGNED_HEAP_CHUNK_SIZE) && event.<RecordedClass> getValue("objectClass").getName().equals(Helper.class.getName())) {
                foundInstance = true;
            }
        }
        if (!foundBigChar || !foundBigByte || !foundSmallByte || !foundInstance) {
            assertTrue("Expected events not found. foundBigChar: " + foundBigChar + " foundBigByte:" + foundBigByte + " foundSmallByte:" + foundSmallByte + " foundInstance:" + foundInstance,
                            foundBigChar && foundBigByte && foundSmallByte && foundInstance);
        }
    }

    @Test
    public void test() throws Exception {

        // These arrays must result in exceeding the large array threshold, resulting in new TLABs. Big Byte.
        byte[] bigByte = new byte[2 * DEFAULT_ALIGNED_HEAP_CHUNK_SIZE];
        Arrays.fill(bigByte, (byte) 0);

        // Using char, so it's the same size as bigByte. Big Char.
        char[] bigChar = new char[DEFAULT_ALIGNED_HEAP_CHUNK_SIZE];
        Arrays.fill(bigChar, 'm');

        // Try to exhaust TLAB with small arrays. Small byte.
        for (int i = 0; i < DEFAULT_ALIGNED_HEAP_CHUNK_SIZE / KILO; i++) {
            byteArray = new byte[KILO];
            Arrays.fill(byteArray, (byte) 0);
        }

        // Try to exhaust TLAB with instances. Instance.
        for (int i = 0; i < DEFAULT_ALIGNED_HEAP_CHUNK_SIZE; i++) {
            helper = new Helper();
        }
    }

    /**
     * This class is only needed to provide a unique name in the event's "objectClass" field that we
     * check.
     */
    static class Helper {
        int testField;
    }
}
