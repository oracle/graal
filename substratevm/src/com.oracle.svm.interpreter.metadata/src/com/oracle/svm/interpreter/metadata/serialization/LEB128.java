/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata.serialization;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class LEB128 {

    public static int readUnsignedInt(DataInput in) throws IOException {
        int result = 0;
        for (int i = 0;; ++i) {
            byte b = in.readByte();
            result |= (b & 0x7F) << (i * 7);
            // The first 4 groups of 7 bits are guaranteed to fit (4 * 7 = 28 bits).
            // That leaves room for only the 4 low-order bits from the 5th group (which has index 4)
            if (i == 4 && (b & 0xF0) != 0) {
                throw new ArithmeticException("Value is larger than 32-bits");
            }
            if ((b & 0x80) == 0) {
                return result;
            }
        }
    }

    public static void writeUnsignedInt(DataOutput out, int value) throws IOException {
        int tmp = value;
        do {
            int b = tmp & 0x7F;
            tmp >>>= 7;
            if (tmp != 0) {
                b |= 0x80;
            }
            out.writeByte(b);
        } while (tmp != 0);
    }

    private static int zigZagEncodeSign(int value) {
        return (value << 1) ^ (value >> 31);
    }

    private static int zigZagDecodeSign(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    public static int readSignedInt(DataInput in) throws IOException {
        return zigZagDecodeSign(readUnsignedInt(in));
    }

    public static void writeSignedInt(DataOutput out, int value) throws IOException {
        writeUnsignedInt(out, zigZagEncodeSign(value));
    }
}
