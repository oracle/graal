/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.util;

import java.nio.*;

import sun.misc.*;

import com.oracle.graal.compiler.common.*;

/**
 * Provides low-level read access from a byte[] array for signed and unsigned values of size 1, 2,
 * 4, and 8 bytes.
 */
public class UnsafeArrayTypeReader implements TypeReader {

    public static int getS1(byte[] data, long byteIndex) {
        return UnsafeAccess.unsafe.getByte(data, readOffset(data, byteIndex, Byte.BYTES));
    }

    public static int getU1(byte[] data, long byteIndex) {
        return UnsafeAccess.unsafe.getByte(data, readOffset(data, byteIndex, Byte.BYTES)) & 0xFF;
    }

    public static int getS2(byte[] data, long byteIndex) {
        if (byteIndex % Short.BYTES == 0) {
            return UnsafeAccess.unsafe.getShort(data, readOffset(data, byteIndex, Short.BYTES));
        } else {
            ByteBuffer buf = ByteBuffer.wrap(new byte[Short.BYTES]).order(ByteOrder.nativeOrder());
            buf.put((byte) getU1(data, byteIndex));
            buf.put((byte) getU1(data, byteIndex + Byte.BYTES));
            return buf.getShort(0);
        }
    }

    public static int getU2(byte[] data, long byteIndex) {
        return getS2(data, byteIndex) & 0xFFFF;
    }

    public static int getS4(byte[] data, long byteIndex) {
        if (byteIndex % Integer.BYTES == 0) {
            return UnsafeAccess.unsafe.getInt(data, readOffset(data, byteIndex, Integer.BYTES));
        } else {
            ByteBuffer buf = ByteBuffer.wrap(new byte[Integer.BYTES]).order(ByteOrder.nativeOrder());
            buf.putShort((short) getS2(data, byteIndex));
            buf.putShort((short) getS2(data, byteIndex + Short.BYTES));
            return buf.getInt(0);
        }
    }

    public static long getU4(byte[] data, long byteIndex) {
        return getS4(data, byteIndex) & 0xFFFFFFFFL;
    }

    public static long getLong(byte[] data, long byteIndex) {
        if (byteIndex % Long.BYTES == 0) {
            return UnsafeAccess.unsafe.getLong(data, readOffset(data, byteIndex, Long.BYTES));
        } else {
            ByteBuffer buf = ByteBuffer.wrap(new byte[Long.BYTES]).order(ByteOrder.nativeOrder());
            buf.putInt(getS4(data, byteIndex));
            buf.putInt(getS4(data, byteIndex + Integer.BYTES));
            return buf.getLong(0);
        }
    }

    private static long readOffset(byte[] data, long byteIndex, int numBytes) {
        assert byteIndex >= 0;
        assert numBytes > 0;
        assert byteIndex + numBytes <= data.length;
        assert Unsafe.ARRAY_BYTE_INDEX_SCALE == 1;

        return byteIndex + Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    private final byte[] data;
    private long byteIndex;

    public UnsafeArrayTypeReader(byte[] data, long byteIndex) {
        this.data = data;
        this.byteIndex = byteIndex;
    }

    @Override
    public long getByteIndex() {
        return byteIndex;
    }

    @Override
    public void setByteIndex(long byteIndex) {
        this.byteIndex = byteIndex;
    }

    @Override
    public int getS1() {
        int result = getS1(data, byteIndex);
        byteIndex += Byte.BYTES;
        return result;
    }

    @Override
    public int getU1() {
        int result = getU1(data, byteIndex);
        byteIndex += Byte.BYTES;
        return result;
    }

    @Override
    public int getS2() {
        int result = getS2(data, byteIndex);
        byteIndex += Short.BYTES;
        return result;
    }

    @Override
    public int getU2() {
        int result = getU2(data, byteIndex);
        byteIndex += Short.BYTES;
        return result;
    }

    @Override
    public int getS4() {
        int result = getS4(data, byteIndex);
        byteIndex += Integer.BYTES;
        return result;
    }

    @Override
    public long getU4() {
        long result = getU4(data, byteIndex);
        byteIndex += Integer.BYTES;
        return result;
    }

    @Override
    public long getS8() {
        long result = getLong(data, byteIndex);
        byteIndex += Long.BYTES;
        return result;
    }
}
