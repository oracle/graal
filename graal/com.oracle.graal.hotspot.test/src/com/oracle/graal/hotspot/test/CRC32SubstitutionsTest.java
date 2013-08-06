/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.test;

import java.io.*;
import java.util.zip.*;

import org.junit.*;

import com.oracle.graal.compiler.test.*;

/**
 * Tests compiled call to {@link CRC32#update(int, int)}.
 */
@SuppressWarnings("javadoc")
public class CRC32SubstitutionsTest extends GraalCompilerTest {

    public static long update(byte[] input) {
        CRC32 crc = new CRC32();
        for (byte b : input) {
            crc.update(b);
        }
        return crc.getValue();
    }

    @Test
    public void test1() {
        test("update", "some string".getBytes());
    }

    public static long updateBytes(byte[] input) {
        CRC32 crc = new CRC32();
        crc.update(input, 0, input.length);
        return crc.getValue();
    }

    @Test
    public void test2() {
        test("updateBytes", "some string".getBytes());
    }

    @Test
    public void test3() throws Throwable {
        String classfileName = CRC32SubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = CRC32SubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);
        test("updateBytes", buf);
    }

}
