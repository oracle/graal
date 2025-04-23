/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;

/**
 * Delegates to, but does not subclass, {@link TypedDataInputStream} for symmetry with
 * {@link ObjectCopierOutputStream}, see the reasoning there. Add methods such as {@link #readShort}
 * as needed.
 */
public class ObjectCopierInputStream extends InputStream {
    private final TypedDataInputStream in;

    public ObjectCopierInputStream(InputStream in) {
        this.in = new TypedDataInputStream(in);
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public short readShort() throws IOException {
        return in.readShort();
    }

    protected Object readUntypedValue(int type) throws IOException {
        return switch (type) {
            case 'I' -> (int) readPackedSignedLong();
            case 'J' -> readPackedSignedLong();
            case 'U' -> readStringValue();
            default -> in.readUntypedValue(type);
        };
    }

    protected String readStringValue() throws IOException {
        int len = readPackedUnsignedInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public Object readTypedPrimitiveArray() throws IOException {
        int len = readPackedUnsignedInt();
        int type = in.readUnsignedByte();
        Object arr = switch (type) {
            case 'Z' -> new boolean[len];
            case 'B' -> new byte[len];
            case 'S' -> new short[len];
            case 'C' -> new char[len];
            case 'I' -> new int[len];
            case 'J' -> new long[len];
            case 'F' -> new float[len];
            case 'D' -> new double[len];
            default -> throw new IOException("Unsupported type: " + Integer.toHexString(type));
        };
        for (int i = 0; i < len; i++) {
            Array.set(arr, i, readUntypedValue(type));
        }
        return arr;
    }

    public long readPackedSignedLong() throws IOException {
        return decodeSign(readPacked());
    }

    public int readPackedUnsignedInt() throws IOException {
        return Math.toIntExact(readPacked());
    }

    private static long decodeSign(long value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private long readPacked() throws IOException {
        int b0 = in.readUnsignedByte();
        if (b0 < ObjectCopierOutputStream.NUM_LOW_CODES) {
            return b0;
        }
        assert b0 >= ObjectCopierOutputStream.NUM_LOW_CODES : b0;
        long sum = b0;
        long shift = ObjectCopierOutputStream.HIGH_WORD_SHIFT;
        for (int i = 2;; i++) {
            long b = in.readUnsignedByte();
            sum += b << shift;
            if (b < ObjectCopierOutputStream.NUM_LOW_CODES || i == ObjectCopierOutputStream.MAX_BYTES) {
                return sum;
            }
            shift += ObjectCopierOutputStream.HIGH_WORD_SHIFT;
        }
    }
}
