/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

/**
 * Tests compiled call to {@link java.util.zip.Adler32#update(int)}.
 */
@SuppressWarnings("javadoc")
public class Adler32SubstitutionsTest extends GraalCompilerTest {

    public static long update(byte[] input) {
        Adler32 adler = new Adler32();
        for (byte b : input) {
            adler.update(b);
        }
        return adler.getValue();
    }

    @Test
    public void test1() {
        test("update", "some string".getBytes());
    }

    public static long updateBytes(byte[] input, int offset, int length) {
        Adler32 adler = new Adler32();
        adler.update(input, offset, length);
        return adler.getValue();
    }

    @Test
    public void test2() {
        byte[] buf = "some string".getBytes();
        int off = 0;
        int len = buf.length;
        test("updateBytes", buf, off, len);
    }

    @Test
    public void test3() throws Throwable {
        String classfileName = Adler32SubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = Adler32SubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);
        test("updateBytes", buf, 0, buf.length);
        for (int offset = 1; offset < buf.length; offset++) {
            test("updateBytes", buf, offset, buf.length - offset);
        }
    }

    public static long updateByteBuffer(ByteBuffer buffer) {
        Adler32 adler = new Adler32();
        buffer.rewind();
        adler.update(buffer);
        return adler.getValue();
    }

    @Test
    public void test4() throws Throwable {
        String classfileName = Adler32SubstitutionsTest.class.getSimpleName().replace('.', '/') + ".class";
        InputStream s = Adler32SubstitutionsTest.class.getResourceAsStream(classfileName);
        byte[] buf = new byte[s.available()];
        new DataInputStream(s).readFully(buf);

        ByteBuffer directBuf = ByteBuffer.allocateDirect(buf.length);
        directBuf.put(buf);
        ByteBuffer heapBuf = ByteBuffer.wrap(buf);

        test("updateByteBuffer", directBuf);
        test("updateByteBuffer", heapBuf);
    }

}
