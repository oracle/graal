/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, IBM Inc. All rights reserved.
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
package com.oracle.svm.test;

import static com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode.READ;
import static com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode.READ_WRITE;
import static com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode.CREATE;
import static com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode.CREATE_OR_REPLACE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;

import java.io.File;
import java.nio.file.Files;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.impl.Word;
import org.graalvm.word.Pointer;
import org.junit.Before;
import org.junit.Test;

public class RawFileOperationSupportTest {

    @Before
    public void requireRawFileSupport() {
        assumeTrue("RawFileOperationSupport is registered only in native images", RawFileOperationSupport.isPresent());
    }

    @Test
    public void testTempDirectoryAccessible() {
        String tmp = RawFileOperationSupport.nativeByteOrder().getTempDirectory();
        assertNotNull(tmp);
        assertFalse(tmp.isEmpty());
    }

    @Test
    public void testCreate() {
        RawFileOperationSupport fileSupport = RawFileOperationSupport.nativeByteOrder();
        File file = new File(fileSupport.getTempDirectory(), "testCreate_" + System.nanoTime() + ".txt");
        file.deleteOnExit();
        RawFileDescriptor fd = fileSupport.create(file, CREATE, READ_WRITE);
        assertTrue(fileSupport.isValid(fd));
        try {
            readAndWriteOnFile(fd);
        } finally {
            assertTrue(fileSupport.close(fd));
        }
    }

    @Test
    public void testReplace() throws Exception {
        File file = File.createTempFile("testReplace_", ".txt");
        file.deleteOnExit();

        RawFileOperationSupport fileSupport = RawFileOperationSupport.nativeByteOrder();
        RawFileDescriptor fd = fileSupport.create(file, CREATE_OR_REPLACE, READ_WRITE);
        assertTrue(fileSupport.isValid(fd));
        try {
            readAndWriteOnFile(fd);
        } finally {
            assertTrue(fileSupport.close(fd));
        }
    }

    /** Tests write, seek, position, size, and read. */
    private static void readAndWriteOnFile(RawFileDescriptor fd) {
        RawFileOperationSupport fileSupport = RawFileOperationSupport.nativeByteOrder();
        byte[] payload = {1, 2, 3, 4, 5};
        int payloadLength = 5;
        // Write
        assertTrue(fileSupport.write(fd, payload));
        assertEquals(payloadLength, fileSupport.position(fd));
        assertEquals(payloadLength, fileSupport.size(fd));
        // Seek
        assertTrue(fileSupport.seek(fd, 0));
        assertEquals(0, fileSupport.position(fd));
        // Read
        Pointer buf = StackValue.get(payloadLength);
        long n = fileSupport.read(fd, buf, Word.unsigned(payloadLength));
        assertEquals(payloadLength, n);
        // Ensure we got what's expected
        for (int i = 0; i < payloadLength; i++) {
            assertEquals(payload[i], buf.readByte(i));
        }
    }

    @Test
    public void testWriteIntEndianness() throws Exception {
        /* Unique bytes so LE vs BE on-disk layouts differ. */
        int value = 0x01020304;
        File file = File.createTempFile("testWriteIntEndianness_", ".txt");
        file.deleteOnExit();

        RawFileOperationSupport fileSupportLE = RawFileOperationSupport.littleEndian();
        // Check we can open a pre-existing file.
        RawFileDescriptor fdLE = fileSupportLE.open(file, READ_WRITE);
        assertTrue(fileSupportLE.isValid(fdLE));
        try {
            assertTrue(fileSupportLE.writeInt(fdLE, value));
        } finally {
            assertTrue(fileSupportLE.close(fdLE));
        }
        byte[] leBytes = Files.readAllBytes(file.toPath());
        assertEquals(4, leBytes.length);

        RawFileOperationSupport fileSupportBE = RawFileOperationSupport.bigEndian();
        RawFileDescriptor fdBE = fileSupportBE.open(file, READ_WRITE);
        assertTrue(fileSupportBE.isValid(fdBE));
        try {
            assertTrue(fileSupportBE.seek(fdBE, 0));
            assertTrue(fileSupportBE.writeInt(fdBE, value));
        } finally {
            assertTrue(fileSupportBE.close(fdBE));
        }
        byte[] beBytes = Files.readAllBytes(file.toPath());
        assertEquals(4, beBytes.length);

        byte[] expectedLe = expectedLittleEndianOnDisk(value);
        byte[] expectedBe = expectedBigEndianOnDisk(value);
        assertArrayEquals(expectedLe, leBytes);
        assertArrayEquals(expectedBe, beBytes);
        assertArrayEquals(leBytes, reverse4bytes(beBytes));
    }

    private static byte[] expectedLittleEndianOnDisk(int v) {
        return new byte[]{(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    private static byte[] expectedBigEndianOnDisk(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    private static byte[] reverse4bytes(byte[] b) {
        assertEquals(4, b.length);
        return new byte[]{b[3], b[2], b[1], b[0]};
    }

    @Test
    public void testOpenMissingFileIsInvalid() {
        RawFileOperationSupport fileSupport = RawFileOperationSupport.nativeByteOrder();
        File dir = new File(fileSupport.getTempDirectory());
        File missing = new File(dir, "testOpenMissingFileIsInvalid_-" + System.nanoTime());
        RawFileDescriptor fd = fileSupport.open(missing, READ);
        assertFalse(fileSupport.isValid(fd));
    }

    @Test
    public void testCreateFailsWhenFileExists() throws Exception {
        RawFileOperationSupport fileSupport = RawFileOperationSupport.nativeByteOrder();
        File file = new File(fileSupport.getTempDirectory(), "testCreateFailsWhenFileExists_" + System.nanoTime() + ".txt");
        file.deleteOnExit();

        RawFileDescriptor first = fileSupport.create(file, CREATE, READ_WRITE);
        assertTrue(fileSupport.isValid(first));
        assertTrue(fileSupport.close(first));

        RawFileDescriptor second = fileSupport.create(file, CREATE, READ_WRITE);
        assertFalse(fileSupport.isValid(second));
    }
}
