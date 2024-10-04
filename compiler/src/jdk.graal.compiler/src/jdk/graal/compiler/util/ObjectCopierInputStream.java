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

public class ObjectCopierInputStream extends TypedDataInputStream {
    public ObjectCopierInputStream(InputStream in) {
        super(in);
    }

    public Object readTypedPrimitiveArray() throws IOException {
        final int length = readInt();
        final byte type = readByte();
        switch (type) {
            case 'Z': {
                boolean[] a = new boolean[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readBoolean();
                }
                return a;
            }
            case 'B': {
                byte[] a = new byte[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readByte();
                }
                return a;
            }
            case 'S': {
                short[] a = new short[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readShort();
                }
                return a;
            }
            case 'C': {
                char[] a = new char[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readChar();
                }
                return a;
            }
            case 'I': {
                int[] a = new int[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readInt();
                }
                return a;
            }
            case 'J': {
                long[] a = new long[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readLong();
                }
                return a;
            }
            case 'F': {
                float[] a = new float[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readFloat();
                }
                return a;
            }
            case 'D': {
                double[] a = new double[length];
                for (int i = 0; i < length; i++) {
                    a[i] = readDouble();
                }
                return a;
            }
            default:
                throw new IOException("Unsupported type: " + Integer.toHexString(type));
        }
    }

    public long readPackedSigned() throws IOException {
        return decodeSign(readPacked());
    }

    public long readPackedUnsigned() throws IOException {
        return readPacked();
    }

    private static long decodeSign(long value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private long readPacked() throws IOException {
        int b0 = readUnsignedByte();
        if (b0 < ObjectCopierOutputStream.NUM_LOW_CODES) {
            return b0;
        }
        assert b0 >= ObjectCopierOutputStream.NUM_LOW_CODES : b0;
        long sum = b0;
        long shift = ObjectCopierOutputStream.HIGH_WORD_SHIFT;
        for (int i = 2;; i++) {
            long b = readUnsignedByte();
            sum += b << shift;
            if (b < ObjectCopierOutputStream.NUM_LOW_CODES || i == ObjectCopierOutputStream.MAX_BYTES) {
                return sum;
            }
            shift += ObjectCopierOutputStream.HIGH_WORD_SHIFT;
        }
    }
}
