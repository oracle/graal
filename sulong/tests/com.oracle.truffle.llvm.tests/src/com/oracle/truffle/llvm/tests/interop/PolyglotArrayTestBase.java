/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.rules.ExpectedException;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;

public class PolyglotArrayTestBase extends ManagedMemAccessTestBase {

    protected static void assertEqualsHex(byte expected, byte actual) {
        if (expected != actual) {
            Assert.assertEquals("0x" + Integer.toHexString(Byte.toUnsignedInt(expected)), "0x" + Integer.toHexString(Byte.toUnsignedInt(actual)));
        }
    }

    protected static void assertEqualsHex(short expected, short actual) {
        if (expected != actual) {
            Assert.assertEquals("0x" + Integer.toHexString(Short.toUnsignedInt(expected)), "0x" + Integer.toHexString(Short.toUnsignedInt(actual)));
        }
    }

    protected static void assertEqualsHex(int expected, int actual) {
        if (expected != actual) {
            Assert.assertEquals("0x" + Integer.toHexString(expected), "0x" + Integer.toHexString(actual));
        }
    }

    protected static void assertEqualsHex(long expected, long actual) {
        if (expected != actual) {
            Assert.assertEquals("0x" + Long.toHexString(expected), "0x" + Long.toHexString(actual));
        }
    }

    protected static void assertPolyglotArrayEquals(Object expected, Object actual) {
        assertHexArrayEquals(expected, actual);
    }

    protected static void assertHexArrayEquals(Object expected, Object actual) {
        Object[] expectedArray = polyglotArrayToJavaArray(expected);
        Object[] actualArray = polyglotArrayToJavaArray(actual);
        try {
            Assert.assertArrayEquals(expectedArray, actualArray);
        } catch (AssertionError e) {
            Assert.assertArrayEquals(toStringArray(expectedArray), toStringArray(actualArray));
            throw e;
        }
    }

    protected static Object[] toStringArray(Object[] expectedArray) {
        return Arrays.stream(expectedArray).map(PolyglotArrayTestBase::toHexString).toArray();
    }

    protected static void doNothing(@SuppressWarnings("unused") Object obj) {
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    protected static class PolyglotArrayBuilder implements TruffleObject {
        final Object delegate;

        public PolyglotArrayBuilder(Object delegate) {
            this.delegate = delegate;
        }

        public PolyglotArrayBuilder set(int idx, Object value) {
            writePolyglotArrayElement(delegate, idx, value);
            return this;
        }

        public static PolyglotArrayBuilder create(Object delegate) {
            return new PolyglotArrayBuilder(delegate);
        }
    }

    protected static Object[] polyglotArrayToJavaArray(Object expected) {
        try {
            InteropLibrary expectedInterop = InteropLibrary.getUncached(expected);
            int length = Math.toIntExact(expectedInterop.getArraySize(expected));
            Object[] array = new Object[length];
            for (int i = 0; i < length; i++) {
                array[i] = expectedInterop.readArrayElement(expected, i);
            }
            return array;
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw new AssertionError(e);
        }
    }

    protected static void writePolyglotArrayElement(Object newArray, int idx, Object value) {
        try {
            InteropLibrary.getUncached(newArray).writeArrayElement(newArray, idx, value);
        } catch (UnsupportedMessageException | UnsupportedTypeException | InvalidArrayIndexException e) {
            throw new AssertionError(e);
        }
    }

    protected static Object getPolyglotArrayElement(Object o, long idx) {
        try {
            return InteropLibrary.getUncached(o).readArrayElement(o, idx);
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw new AssertionError(e);
        }
    }

    static short toNativeEndian(short x) {
        return Short.reverseBytes(x);
    }

    static int toNativeEndian(int x) {
        return Integer.reverseBytes(x);
    }

    static long toNativeEndian(long x) {
        return Long.reverseBytes(x);
    }

    protected static String toHexString(Object obj) {
        if (obj instanceof Byte) {
            return "0x" + Integer.toHexString(Byte.toUnsignedInt((Byte) obj));
        }
        if (obj instanceof Short) {
            return "0x" + Integer.toHexString(Short.toUnsignedInt((Short) obj));
        }
        if (obj instanceof Integer) {
            return "0x" + Integer.toHexString((Integer) obj);
        }
        if (obj instanceof Long) {
            return "0x" + Long.toHexString((Long) obj);
        }
        return Objects.toString(obj);
    }

    enum ExpectedResultMarker {
        SUPPORTED,
        UNSUPPORTED;

        @Override
        public String toString() {
            return String.format("%11s", super.toString().toLowerCase());
        }
    }

    protected interface ExpectedExceptionConsumer extends Consumer<ExpectedException> {
    }

    protected static ExpectedExceptionConsumer expectPolyglotException(String exceptionMessage) {
        return (ExpectedException thrown) -> {
            thrown.expect(PolyglotException.class);
            thrown.expectMessage(exceptionMessage);
        };
    }

    /**
     * Warp parameter to get nice toString.
     */
    protected static class ParameterArray {
        protected final Object[] parameters;

        ParameterArray(Object... parameters) {
            this.parameters = parameters;
        }

        protected Object[] getArguments() {
            return Arrays.stream(parameters).map(o -> (o instanceof Supplier ? ((Supplier<?>) o).get() : o)).toArray();
        }

        @Override
        public String toString() {
            return Arrays.stream(parameters).map(Object::toString).collect(Collectors.joining(", "));
        }
    }

    private static class HexSupplier<T> implements Supplier<T> {

        private final T value;

        HexSupplier(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public String toString() {
            try {
                return PolyglotArrayTestBase.toHexString(value);
            } catch (AssertionError e) {
                return String.valueOf(value);
            }
        }

    }

    /**
     * Wrap value to get nice toString.
     */
    protected static HexSupplier<Long> hex(long value) {
        return new HexSupplier<>(value);
    }

    /**
     * Wrap value to get nice toString.
     */
    protected static HexSupplier<Integer> hex(int value) {
        return new HexSupplier<>(value);
    }

    /**
     * Wrap value to get nice toString.
     */
    protected static HexSupplier<Short> hex(short value) {
        return new HexSupplier<>(value);
    }

    /**
     * Wrap value to get nice toString.
     */
    protected static HexSupplier<Byte> hex(byte value) {
        return new HexSupplier<>(value);
    }
}
