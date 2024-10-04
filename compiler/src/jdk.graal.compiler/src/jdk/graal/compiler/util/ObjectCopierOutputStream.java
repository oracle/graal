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
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;

import jdk.graal.compiler.core.common.calc.UnsignedMath;

public class ObjectCopierOutputStream extends TypedDataOutputStream {
    // Constants for UNSIGNED5 coding of Pack200
    protected static final long HIGH_WORD_SHIFT = 6;
    protected static final long NUM_HIGH_CODES = 1 << HIGH_WORD_SHIFT; // number of high codes (64)
    protected static final long NUM_LOW_CODES = (1 << Byte.SIZE) - NUM_HIGH_CODES;
    protected static final long MAX_BYTES = 11;

    public ObjectCopierOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void writeTypedValue(Object value) throws IOException {
        if (value instanceof Enum<?>) {
            throw new IllegalArgumentException(String.format("Unsupported type: Value: %s, Value type: %s", value, value.getClass()));
        }
        if (value instanceof Integer) {
            writeByte('I');
            writePackedSignedLong((int) value);
        } else if (value instanceof Long) {
            writeByte('J');
            writePackedSignedLong((long) value);
        } else {
            super.writeTypedValue(value);
        }
    }

    @Override
    protected void writeStringValue(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        this.writePackedUnsignedInt(bytes.length);
        this.write(bytes);
    }

    public void writeTypedPrimitiveArray(Object value) throws IOException {
        Class<?> compClz = value.getClass().componentType();
        int length = Array.getLength(value);
        writePackedUnsignedInt(length);
        if (compClz == boolean.class) {
            writeByte('Z');
            for (int i = 0; i < length; i++) {
                writeBoolean(Array.getBoolean(value, i));
            }
        } else if (compClz == byte.class) {
            writeByte('B');
            for (int i = 0; i < length; i++) {
                writeByte(Array.getByte(value, i));
            }
        } else if (compClz == short.class) {
            writeByte('S');
            for (int i = 0; i < length; i++) {
                writeShort(Array.getShort(value, i));
            }
        } else if (compClz == char.class) {
            writeByte('C');
            for (int i = 0; i < length; i++) {
                writeChar(Array.getChar(value, i));
            }
        } else if (compClz == int.class) {
            writeByte('I');
            for (int i = 0; i < length; i++) {
                writePackedSignedLong(Array.getInt(value, i));
            }
        } else if (compClz == long.class) {
            writeByte('J');
            for (int i = 0; i < length; i++) {
                writePackedSignedLong(Array.getLong(value, i));
            }
        } else if (compClz == float.class) {
            writeByte('F');
            for (int i = 0; i < length; i++) {
                writeFloat(Array.getFloat(value, i));
            }
        } else if (compClz == double.class) {
            writeByte('D');
            for (int i = 0; i < length; i++) {
                writeDouble(Array.getDouble(value, i));
            }
        } else {
            throw new IllegalArgumentException(String.format("Unsupported array: Value: %s, Value type: %s", value, value.getClass()));
        }
    }

    static long encodeSign(long value) {
        return (value << 1) ^ (value >> 63);
    }

    public void writePackedSignedLong(long value) throws IOException {
        // this is a modified version of the SIGNED5 encoding from Pack200
        writePacked(encodeSign(value));
    }

    public void writePackedUnsignedInt(int value) throws IOException {
        // this is a modified version of the UNSIGNED5 encoding from Pack200
        writePacked(value);
    }

    private void writePacked(long value) throws IOException {
        if (UnsignedMath.belowThan(value, NUM_LOW_CODES)) {
            writeByte((int) value);
            return;
        }
        long sum = value;
        for (int i = 1; UnsignedMath.aboveOrEqual(sum, NUM_LOW_CODES) && i < MAX_BYTES; i++) {
            sum -= NUM_LOW_CODES;
            long u1 = NUM_LOW_CODES + (sum & (NUM_HIGH_CODES - 1)); // this is a "high code"
            sum >>>= HIGH_WORD_SHIFT; // extracted 6 bits
            writeByte((int) u1);
        }
        // remainder is either a "low code" or the last byte
        assert sum == (sum & 0xFF) : "not a byte";
        writeByte((int) (sum & 0xFF));
    }
}
