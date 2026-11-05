/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Assert;
import org.junit.Test;

public class SharedArenaBulkCopyTest {
    private static final int WINDOW_SIZE = 8 * 1024;
    private static final byte TARGET = (byte) 'c';
    private static final VarHandle JAVA_INT_HANDLE = ValueLayout.JAVA_INT.varHandle();

    @Test
    public void testAllocatedSharedArenaVarHandleAccess() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(2L * ValueLayout.JAVA_INT.byteSize(), ValueLayout.JAVA_INT.byteAlignment());

            JAVA_INT_HANDLE.set(segment, 0L, 0x12345678);
            JAVA_INT_HANDLE.set(segment, ValueLayout.JAVA_INT.byteSize(), -17);

            Assert.assertEquals(0x12345678, (int) JAVA_INT_HANDLE.get(segment, 0L));
            Assert.assertEquals(-17, (int) JAVA_INT_HANDLE.get(segment, ValueLayout.JAVA_INT.byteSize()));
        }
    }

    @Test
    public void testMappedSharedArenaBulkCopy() throws Exception {
        byte[] content = createContent();
        Path file = Files.createTempFile("shared-arena-bulk-copy", ".txt");
        Files.write(file, content);

        try (Arena arena = Arena.ofShared();
                        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);

            Assert.assertEquals(count(content, 0, content.length, TARGET), countInWindows(segment, 0, channel.size(), TARGET));

            long offset = 37;
            long remaining = channel.size() - offset;
            Assert.assertEquals(count(content, (int) offset, (int) remaining, TARGET), countInWindows(segment, offset, remaining, TARGET));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void testMappedSharedArenaVarHandleAccess() throws Exception {
        Path file = Files.createTempFile("shared-arena-varhandle", ".bin");
        Files.write(file, new byte[2 * Integer.BYTES]);

        try (Arena arena = Arena.ofShared();
                        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, 2L * Integer.BYTES, arena);

            JAVA_INT_HANDLE.set(segment, 0L, 0x10203040);
            JAVA_INT_HANDLE.set(segment, (long) Integer.BYTES, -17);

            Assert.assertEquals(0x10203040, (int) JAVA_INT_HANDLE.get(segment, 0L));
            Assert.assertEquals(-17, (int) JAVA_INT_HANDLE.get(segment, (long) Integer.BYTES));
        } finally {
            byte[] bytes = Files.readAllBytes(file);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
            Assert.assertEquals(0x10203040, buffer.getInt());
            Assert.assertEquals(-17, buffer.getInt());
            Files.deleteIfExists(file);
        }
    }

    private static int countInWindows(MemorySegment segment, long offset, long length, byte target) {
        byte[] window = new byte[WINDOW_SIZE];
        long end = offset + length;
        long position = offset;
        int count = 0;

        while (position < end) {
            int windowLength = (int) Math.min(WINDOW_SIZE, end - position);
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, position, window, 0, windowLength);
            count += count(window, 0, windowLength, target);
            position += windowLength;
        }
        return count;
    }

    private static int count(byte[] bytes, int offset, int length, byte target) {
        int count = 0;
        for (int i = offset; i < offset + length; i++) {
            if (bytes[i] == target) {
                count++;
            }
        }
        return count;
    }

    private static byte[] createContent() {
        String line = "{\"city\":\"city-%04d\",\"value\":42}\n";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4096; i++) {
            builder.append(line.formatted(i));
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }
}
