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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;

import jdk.graal.compiler.core.common.calc.UnsignedMath;

/**
 * Wraps an {@link OutputStream} and writes binary data of certain kinds to it. Optionally prints a
 * text representation to a separate stream for debugging. This delegates to, but does not subclass,
 * {@link TypedDataOutputStream} because it cannot override final public methods such as
 * {@link DataOutputStream#writeShort} to intercept them for debug printing. Add such methods such
 * as {@link #writeShort} as needed.
 */
public class ObjectCopierOutputStream extends OutputStream {
    // Constants for UNSIGNED5 coding of Pack200
    protected static final long HIGH_WORD_SHIFT = 6;
    protected static final long NUM_HIGH_CODES = 1 << HIGH_WORD_SHIFT; // number of high codes (64)
    protected static final long NUM_LOW_CODES = (1 << Byte.SIZE) - NUM_HIGH_CODES;
    protected static final long MAX_BYTES = 11;

    private final TypedDataOutputStream out;
    private final PrintStream debugOut;

    /**
     * @param debugOut {@code null} or a stream to print a text representation of the written binary
     *            data to. This stream is never closed.
     */
    public ObjectCopierOutputStream(OutputStream out, PrintStream debugOut) {
        this.out = new TypedDataOutputStream(out);
        this.debugOut = debugOut;
    }

    @Override
    public void write(int b) throws IOException {
        internalWriteByte(b);
        if (debugOut != null) {
            debugOut.printf(" 0x%02x", b);
        }
    }

    protected void internalWriteByte(int v) throws IOException {
        out.writeByte(v);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public void writeShort(int v) throws IOException {
        out.writeShort(v);
        debugPrintValue(v);
    }

    public void writeUntypedValue(Object value) throws IOException {
        Class<?> valueClz = value.getClass();
        if (valueClz == Boolean.class) {
            out.writeBoolean((Boolean) value);
        } else if (valueClz == Byte.class) {
            internalWriteByte((Byte) value);
        } else if (valueClz == Short.class) {
            out.writeShort((Short) value);
        } else if (valueClz == Character.class) {
            out.writeChar((Character) value);
        } else if (valueClz == Integer.class) {
            internalWritePackedSigned((int) value);
        } else if (valueClz == Long.class) {
            internalWritePackedSigned((long) value);
        } else if (valueClz == Float.class) {
            out.writeFloat((Float) value);
        } else if (valueClz == Double.class) {
            out.writeDouble((Double) value);
        } else if (valueClz == String.class) {
            writeStringValue((String) value);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported type: Value: %s, Value type: %s", value, valueClz));
        }
        debugPrintValue(value);
    }

    protected void debugPrintValue(Object value) {
        if (debugOut != null) {
            Object debugValue = switch (value) {
                case String s -> ObjectCopier.Encoder.escapeDebugStringValue(s);
                case Character c -> (int) c;
                default -> value;
            };
            debugOut.printf(" %s", debugValue);
        }
    }

    protected void writeStringValue(String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        internalWritePackedUnsignedInt(bytes.length);
        out.write(bytes);
    }

    public void writeTypedPrimitiveArray(Object value) throws IOException {
        Class<?> compClz = value.getClass().componentType();
        int length = Array.getLength(value);
        internalWritePackedUnsignedInt(length);
        if (compClz == boolean.class) {
            internalWriteByte('Z');
            for (int i = 0; i < length; i++) {
                out.writeBoolean(Array.getBoolean(value, i));
            }
        } else if (compClz == byte.class) {
            internalWriteByte('B');
            for (int i = 0; i < length; i++) {
                internalWriteByte(Array.getByte(value, i));
            }
        } else if (compClz == short.class) {
            internalWriteByte('S');
            for (int i = 0; i < length; i++) {
                out.writeShort(Array.getShort(value, i));
            }
        } else if (compClz == char.class) {
            internalWriteByte('C');
            for (int i = 0; i < length; i++) {
                out.writeChar(Array.getChar(value, i));
            }
        } else if (compClz == int.class) {
            internalWriteByte('I');
            for (int i = 0; i < length; i++) {
                internalWritePackedSigned(Array.getInt(value, i));
            }
        } else if (compClz == long.class) {
            internalWriteByte('J');
            for (int i = 0; i < length; i++) {
                internalWritePackedSigned(Array.getLong(value, i));
            }
        } else if (compClz == float.class) {
            internalWriteByte('F');
            for (int i = 0; i < length; i++) {
                out.writeFloat(Array.getFloat(value, i));
            }
        } else if (compClz == double.class) {
            internalWriteByte('D');
            for (int i = 0; i < length; i++) {
                out.writeDouble(Array.getDouble(value, i));
            }
        } else {
            throw new IllegalArgumentException(String.format("Unsupported array: Value: %s, Value type: %s", value, value.getClass()));
        }
        if (debugOut != null) {
            for (int i = 0; i < length; i++) {
                debugPrintValue(Array.get(value, i));
            }
        }
    }

    private static long encodeSign(long value) {
        return (value << 1) ^ (value >> 63);
    }

    protected void internalWritePackedSigned(long value) throws IOException {
        // this is a modified version of the SIGNED5 encoding from Pack200
        writePacked(encodeSign(value));
    }

    public void writePackedUnsignedInt(int value) throws IOException {
        internalWritePackedUnsignedInt(value);
        debugPrintValue(value);
    }

    protected void internalWritePackedUnsignedInt(int value) throws IOException {
        // this is a modified version of the UNSIGNED5 encoding from Pack200
        writePacked(value);
    }

    private void writePacked(long value) throws IOException {
        if (UnsignedMath.belowThan(value, NUM_LOW_CODES)) {
            internalWriteByte((int) value);
            return;
        }
        long sum = value;
        for (int i = 1; UnsignedMath.aboveOrEqual(sum, NUM_LOW_CODES) && i < MAX_BYTES; i++) {
            sum -= NUM_LOW_CODES;
            long u1 = NUM_LOW_CODES + (sum & (NUM_HIGH_CODES - 1)); // this is a "high code"
            sum >>>= HIGH_WORD_SHIFT; // extracted 6 bits
            internalWriteByte((int) u1);
        }
        // remainder is either a "low code" or the last byte
        assert sum == (sum & 0xFF) : "not a byte";
        internalWriteByte((int) (sum & 0xFF));
    }
}
