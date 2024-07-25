/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeBuilder;
import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class BytecodeDSLAccessTest {
    @Parameters(name = "{0}")
    public static List<Boolean> getParameters() {
        return List.of(false, true);
    }

    @Parameter(0) public boolean isUnsafe;

    public BytecodeDSLAccess access;

    @Before
    public void setUp() {
        access = BytecodeDSLAccess.lookup(AccessToken.PUBLIC_TOKEN, isUnsafe);
    }

    private static final byte[] BYTE_INPUTS = new byte[]{0, -1, 1, Byte.MAX_VALUE, Byte.MIN_VALUE};
    private static final short[] SHORT_INPUTS = new short[]{0, -1, 1, Short.MAX_VALUE, Short.MIN_VALUE};

    private static final int[] INT_INPUTS = new int[]{0, -1, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};

    @Test
    public void testByte() {
        byte[] arr = new byte[BYTE_INPUTS.length];
        for (int i = 0; i < BYTE_INPUTS.length; i++) {
            access.writeByte(arr, i, BYTE_INPUTS[i]);
        }
        for (int i = 0; i < BYTE_INPUTS.length; i++) {
            assertEquals(BYTE_INPUTS[i], access.readByte(arr, i));
        }

        if (!isUnsafe) {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeByte(arr, -1, (byte) 42));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeByte(arr, arr.length, (byte) 42));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readByte(arr, -1));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readByte(arr, arr.length));
        }
    }

    @Test
    public void testShort() {
        byte[] arr = new byte[SHORT_INPUTS.length * 2];
        for (int i = 0; i < SHORT_INPUTS.length; i++) {
            access.writeShort(arr, i * 2, SHORT_INPUTS[i]);
        }
        for (int i = 0; i < SHORT_INPUTS.length; i++) {
            assertEquals(SHORT_INPUTS[i], access.readShort(arr, i * 2));
        }

        if (!isUnsafe) {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeShort(arr, -1, (short) 42));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeShort(arr, arr.length, (short) 42));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readShort(arr, -1));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readShort(arr, arr.length));
        }
    }

    @Test
    public void testInt() {
        byte[] arr = new byte[INT_INPUTS.length * 4];
        for (int i = 0; i < INT_INPUTS.length; i++) {
            access.writeInt(arr, i * 4, INT_INPUTS[i]);
        }
        for (int i = 0; i < INT_INPUTS.length; i++) {
            assertEquals(INT_INPUTS[i], access.readInt(arr, i * 4));
        }

        if (!isUnsafe) {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeInt(arr, -1, 42));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeInt(arr, arr.length, 42));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readInt(arr, -1));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readInt(arr, arr.length));
        }
    }

    @Test
    public void testCombined() {
        byte[] arr = new byte[4 + 2 + 1 + 2 + 4];
        int i = 0;
        access.writeInt(arr, i, -42);
        i += 4;
        access.writeShort(arr, i, (short) 123);
        i += 2;
        access.writeByte(arr, i, (byte) -30);
        i += 1;
        access.writeShort(arr, i, (short) -60000);
        i += 2;
        access.writeInt(arr, i, 1000000000);

        i = 0;
        assertEquals(-42, access.readInt(arr, i));
        i += 4;
        assertEquals((short) 123, access.readShort(arr, i));
        i += 2;
        assertEquals((byte) -30, access.readByte(arr, i));
        i += 1;
        assertEquals((short) -60000, access.readShort(arr, i));
        i += 2;
        assertEquals(1000000000, access.readInt(arr, i));
    }

    @Test
    public void testObject() {
        String[] arr = new String[4];
        access.writeObject(arr, 0, "the");
        access.writeObject(arr, 1, "quick");
        access.writeObject(arr, 2, "brown");
        access.writeObject(arr, 3, "fox");
        assertEquals("the", access.readObject(arr, 0));
        assertEquals("quick", access.readObject(arr, 1));
        assertEquals("brown", access.readObject(arr, 2));
        assertEquals("fox", access.readObject(arr, 3));

        if (!isUnsafe) {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeObject(arr, -1, "jumps"));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.writeObject(arr, arr.length, "jumps"));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readObject(arr, -1));
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> access.readObject(arr, arr.length));
            assertThrows(ArrayStoreException.class, () -> access.writeObject(arr, 0, new Object()));
        }
    }

    @Test
    public void testCast() {
        Object foo = "foo";
        String fooString = doCast(foo, String.class);
        assertTrue(fooString.contentEquals("foo"));

        foo = new Object();
        if (!isUnsafe) {
            try {
                doCast(foo, String.class);
                fail("Expected ClassCastException");
            } catch (ClassCastException ex) {
                // pass
            }
        } else {
            doCast(foo, String.class); // no failure (unchecked cast)
            try {
                fooString = doCast(foo, String.class); // failure (assignment uses checkcast)
                fail("Expected ClassCastException");
            } catch (ClassCastException ex) {
                // pass
            }
        }
    }

    private <T> T doCast(Object o, Class<T> clazz) {
        return access.cast(o, clazz);
    }

    /**
     * The safe accessor has different implementations for reading numbers depending on the platform
     * byte order. In order to test both implementations regardless of the platform, we manually
     * test the static helpers.
     */

    @Test
    public void testBigEndianNumbers() {
        assumeFalse(isUnsafe);

        byte[] arr = new byte[SHORT_INPUTS.length * 2];
        for (int i = 0; i < SHORT_INPUTS.length; i++) {
            BytecodeDSLAccess.SafeImpl.writeShortBigEndian(arr, i * 2, SHORT_INPUTS[i]);
        }
        for (int i = 0; i < SHORT_INPUTS.length; i++) {
            assertEquals(SHORT_INPUTS[i], BytecodeDSLAccess.SafeImpl.readShortBigEndian(arr, i * 2));
        }

        arr = new byte[INT_INPUTS.length * 4];
        for (int i = 0; i < INT_INPUTS.length; i++) {
            BytecodeDSLAccess.SafeImpl.writeIntBigEndian(arr, i * 4, INT_INPUTS[i]);
        }
        for (int i = 0; i < INT_INPUTS.length; i++) {
            assertEquals(INT_INPUTS[i], BytecodeDSLAccess.SafeImpl.readIntBigEndian(arr, i * 4));
        }
    }

    @Test
    public void testLittleEndianNumbers() {
        assumeFalse(isUnsafe);

        byte[] arr = new byte[SHORT_INPUTS.length * 2];
        for (int i = 0; i < SHORT_INPUTS.length; i++) {
            BytecodeDSLAccess.SafeImpl.writeShortLittleEndian(arr, i * 2, SHORT_INPUTS[i]);
        }
        for (int i = 0; i < SHORT_INPUTS.length; i++) {
            assertEquals(SHORT_INPUTS[i], BytecodeDSLAccess.SafeImpl.readShortLittleEndian(arr, i * 2));
        }

        arr = new byte[INT_INPUTS.length * 4];
        for (int i = 0; i < INT_INPUTS.length; i++) {
            BytecodeDSLAccess.SafeImpl.writeIntLittleEndian(arr, i * 4, INT_INPUTS[i]);
        }
        for (int i = 0; i < INT_INPUTS.length; i++) {
            assertEquals(INT_INPUTS[i], BytecodeDSLAccess.SafeImpl.readIntLittleEndian(arr, i * 4));
        }
    }

    abstract static class AccessToken<T extends RootNode & BytecodeRootNode> extends BytecodeRootNodes<T> {

        protected AccessToken(BytecodeParser<? extends BytecodeBuilder> parse) {
            super(parse);
        }

        static final Object PUBLIC_TOKEN = TOKEN;

    }
}
