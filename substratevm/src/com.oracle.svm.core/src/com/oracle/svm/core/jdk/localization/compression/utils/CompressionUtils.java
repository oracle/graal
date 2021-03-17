/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.compression.utils;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class CompressionUtils {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] intsToBytes(int[] data) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(data);
        return byteBuffer.array();
    }

    public static int[] bytesToInts(byte[] data) {
        IntBuffer intBuf = ByteBuffer.wrap(data)
                        .asIntBuffer();
        int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        return array;
    }

    public static int readInt(InputStream stream) throws IOException {
        return stream.read() << 24 | stream.read() << 16 | stream.read() << 8 | stream.read();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void writeInt(OutputStream stream, int value) throws IOException {
        stream.write((byte) (value >>> 24));
        stream.write((byte) (value >>> 16));
        stream.write((byte) (value >>> 8));
        stream.write((byte) value);
    }

    public static int readNBytes(InputStream input, byte[] dst) throws IOException {
        int remaining = dst.length;
        int offset = 0;
        int bytesRead = input.read(dst, 0, remaining);
        while (bytesRead > 0) {
            offset += bytesRead;
            remaining -= bytesRead;
            bytesRead = input.read(dst, offset, remaining);
        }
        return offset;
    }
}
