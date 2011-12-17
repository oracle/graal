/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package test.com.sun.max.asm;

/**
 * Helper functions for manipulating byte data.
 */
public final class ByteUtils {
    private ByteUtils() {
    }

    public static byte[] toByteArray(byte value) {
        return new byte[]{value};
    }

    public static byte[] toBigEndByteArray(short value) {
        return new byte[]{(byte) ((value >>> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    public static byte[] toLittleEndByteArray(short value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >>> 8) & 0xFF)};
    }

    public static byte[] toBigEndByteArray(int value) {
        return new byte[]{(byte) ((value >>> 24) & 0xFF), (byte) ((value >>> 16) & 0xFF), (byte) ((value >>> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    public static byte[] toLittleEndByteArray(int value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >>> 8) & 0xFF), (byte) ((value >>> 16) & 0xFF), (byte) ((value >>> 24) & 0xFF)};
    }

    public static byte[] toBigEndByteArray(long value) {
        return new byte[]{
            (byte) ((value >>> 56) & 0xFF), (byte) ((value >>> 48) & 0xFF), (byte) ((value >>> 40) & 0xFF), (byte) ((value >>> 32) & 0xFF),
            (byte) ((value >>> 24) & 0xFF), (byte) ((value >>> 16) & 0xFF), (byte) ((value >>> 8) & 0xFF), (byte) ((value >>> 0) & 0xFF)
        };
    }

    public static byte[] toLittleEndByteArray(long value) {
        return new byte[]{
            (byte) ((value >>> 0) & 0xFF), (byte) ((value >>> 8) & 0xFF), (byte) ((value >>> 16) & 0xFF),  (byte) ((value >>> 24) & 0xFF),
            (byte) ((value >>> 32) & 0xFF), (byte) ((value >>> 40) & 0xFF), (byte) ((value >>> 48) & 0xFF), (byte) ((value >>> 56) & 0xFF)
        };
    }

    public static boolean checkBytes(byte[] expected, byte[] codeBuffer, int offset) {
        if (codeBuffer.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != codeBuffer[offset + i]) {
                return false;
            }
        }
        return true;
    }
}
