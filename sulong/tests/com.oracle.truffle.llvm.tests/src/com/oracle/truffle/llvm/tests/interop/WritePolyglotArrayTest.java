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

import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.OBJECT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_BYTE_ARRAY_16;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_BYTE_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_DOUBLE_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_FLOAT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_INT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_LONG_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.PRIMITIVE_LONG_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_DOUBLE_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_FLOAT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I16_BYTE_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I16_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I16_INT_ARRAY_2;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I32_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I32_LONG_ARRAY_2;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I64_BYTE_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I64_LONG_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I64_LONG_ARRAY_2;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I8_BYTE_ARRAY_16;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I8_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I8_INT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I8_LONG_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_I8_LONG_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.WritePolyglotArrayTest.TestObject.TYPED_POINTER_ARRAY;
import static com.oracle.truffle.llvm.tests.interop.values.UntypedArrayObject.UntypedByteArrayObject;
import static com.oracle.truffle.llvm.tests.interop.values.UntypedArrayObject.UntypedDoubleArrayObject;
import static com.oracle.truffle.llvm.tests.interop.values.UntypedArrayObject.UntypedFloatArrayObject;
import static com.oracle.truffle.llvm.tests.interop.values.UntypedArrayObject.UntypedIntegerArrayObject;
import static com.oracle.truffle.llvm.tests.interop.values.UntypedArrayObject.UntypedLongArrayObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropWriteNode;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.tests.interop.values.ArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.DoubleArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.LongArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback;
import com.oracle.truffle.llvm.tests.interop.values.TypedArrayObject;
import com.oracle.truffle.tck.TruffleRunner;

/**
 * Test and document which values can be written to polyglot arrays. This mainly tests
 * {@link LLVMInteropWriteNode}.
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class WritePolyglotArrayTest extends WritePolyglotArrayTestBase {

    /**
     * Object representing an arbitrary "pointer".
     */
    private static final TruffleObject A_POINTER = new TruffleObject() {
    };

    /**
     * Wrap test objects in an enum to get a nice name for the parameter.
     */
    public enum TestObject implements java.util.function.Supplier<Object> {
        OBJECT_ARRAY_8(() -> new ArrayObject("11111111", "22222222", "33333333", "44444444", "55555555", "66666666", "77777777", "88888888")),
        PRIMITIVE_INT_ARRAY_1(() -> new UntypedIntegerArrayObject(0x11111111)),
        PRIMITIVE_INT_ARRAY_8(() -> new UntypedIntegerArrayObject(0x11111111, 0x22222222, 0x33333333, 0x44444444, 0x55555555, 0x66666666, 0x77777777, 0x88888888)),
        PRIMITIVE_LONG_ARRAY_1(() -> new UntypedLongArrayObject(0x11111111_12345678L)),
        PRIMITIVE_LONG_ARRAY_8(
                        () -> new UntypedLongArrayObject(0x11111111_12345678L, 0x87654321_18273645L, 0x11223344_55667788L, 0x99AABBCC_DDEEFF00L, 0x11111111_22222222L, 0x33333333_44444444L,
                                        0x55555555_66666666L, 0x77777777_88888888L)),
        PRIMITIVE_FLOAT_ARRAY_8(() -> new UntypedFloatArrayObject(1, 1, 1, 1, 1, 1, 1, 1)),
        PRIMITIVE_DOUBLE_ARRAY_8(() -> new UntypedDoubleArrayObject(1, 1, 1, 1, 1, 1, 1, 1)),
        PRIMITIVE_BYTE_ARRAY_8(() -> new UntypedByteArrayObject((byte) 0xCA, (byte) 0xFE, (byte) 0xFE, (byte) 0xED, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78)),
        PRIMITIVE_BYTE_ARRAY_16(
                        () -> new UntypedByteArrayObject((byte) 0xCA, (byte) 0xFE, (byte) 0xFE, (byte) 0xED, (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78, (byte) 0x11, (byte) 0x22, (byte) 0x33,
                                        (byte) 0x44, (byte) 0x55, (byte) 0x66, (byte) 0x77, (byte) 0x88)),
        TYPED_I8_INT_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I8), 0x11111111)),
        TYPED_I8_INT_ARRAY_8(() -> new LongArrayObject(getTypeID(TestType.I8), 0x11111111, 0x22222222L, 0x33333333, 0x44444444L, 0x55555555, 0x66666666L, 0x77777777, 0x88888888L)),
        TYPED_I8_LONG_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I8), 0x11111111_12345678L)),
        TYPED_I8_LONG_ARRAY_8(
                        () -> new LongArrayObject(getTypeID(TestType.I8), 0x11111111_12345678L, 0x87654321_18273645L, 0x11223344_55667788L, 0x99AABBCC_DDEEFF00L, 0x11111111_22222222L,
                                        0x33333333_44444444L, 0x55555555_66666666L, 0x77777777_88888888L)),
        TYPED_I8_BYTE_ARRAY_16(() -> new LongArrayObject(getTypeID(TestType.I8), 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00)),
        TYPED_I16_INT_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I16), 0x11111111)),
        TYPED_I16_INT_ARRAY_2(() -> new LongArrayObject(getTypeID(TestType.I16), 0x11111111, 0x12345678)),
        TYPED_I16_BYTE_ARRAY_4(() -> new LongArrayObject(getTypeID(TestType.I16), 0x1111, 0x2222, 0x3333, 0x4444)),
        TYPED_I32_INT_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I32), 0x11111111)),
        TYPED_I32_LONG_ARRAY_2(() -> new LongArrayObject(getTypeID(TestType.I32), 0x11111111_12345678L, 0x11223344_55667788L)),
        TYPED_I64_LONG_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I64), 0x11111111_12345678L)),
        TYPED_I64_LONG_ARRAY_2(() -> new LongArrayObject(getTypeID(TestType.I64), 0x11111111_12345678L, 0x11223344_55667788L)),
        TYPED_I64_BYTE_ARRAY_4(() -> new LongArrayObject(getTypeID(TestType.I64), 0x1111L, 0x2222L, 0x3333L, 0x4444L)),
        TYPED_POINTER_ARRAY(() -> new TypedArrayObject(getPointerTypeID(), A_POINTER)),
        TYPED_FLOAT_ARRAY_8(() -> new DoubleArrayObject(getTypeID(TestType.FLOAT), 1, 1, 1, 1, 1, 1, 1, 1)),
        TYPED_DOUBLE_ARRAY_8(() -> new DoubleArrayObject(getTypeID(TestType.DOUBLE), 1, 1, 1, 1, 1, 1, 1, 1));

        private final Supplier<Object> factory;

        TestObject(Supplier<Object> factory) {
            this.factory = factory;
        }

        @Override
        public Object get() {
            return factory.get();
        }
    }

    @Parameterized.Parameters(name = "{3}: {0}, {4}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> c = new ArrayList<>();
        // write i8
        WriteI8.untyped(c);
        WriteI8.toI8(c);
        WriteI8.toI16(c);
        WriteI8.toI32(c);
        WriteI8.toI64(c);
        WriteI8.toFloatingPoint(c);
        WriteI8.toPointer(c);
        // write i16
        WriteI16.untyped(c);
        WriteI16.toI8(c);
        WriteI16.toI16(c);
        WriteI16.toI32(c);
        WriteI16.toI64(c);
        WriteI16.toFloatingPoint(c);
        // write i32
        WriteI32.untyped(c);
        WriteI32.toI8(c);
        WriteI32.toI16(c);
        WriteI32.toI32(c);
        WriteI32.toI64(c);
        WriteI32.toFloatingPoint(c);
        // write i64
        WriteI64.untyped(c);
        WriteI64.toI8(c);
        WriteI64.toI16(c);
        WriteI64.toI32(c);
        WriteI64.toI64(c);
        WriteI64.toFloatingPoint(c);
        WriteI64.toPointer(c);
        // write float
        WriteFloat.untyped(c);
        WriteFloat.toI8(c);
        WriteFloat.toI16(c);
        WriteFloat.toI32(c);
        WriteFloat.toI64(c);
        WriteFloat.toFloatingPoint(c);
        // write double
        WriteDouble.untyped(c);
        WriteDouble.toI8(c);
        WriteDouble.toI16(c);
        WriteDouble.toI32(c);
        WriteDouble.toI64(c);
        WriteDouble.toFloatingPoint(c);
        WriteDouble.toPointer(c);
        // write pointer
        WritePointer.untyped(c);
        WritePointer.toI8(c);
        WritePointer.toI16(c);
        WritePointer.toI32(c);
        WritePointer.toI64(c);
        WritePointer.toFloatingPoint(c);
        WritePointer.toPointer(c);
        return c;
    }

    private static class WriteI8 {

        /**
         * Write an i8 to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            addSupported(c, "write_i8", PRIMITIVE_INT_ARRAY_8, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8", PRIMITIVE_FLOAT_ARRAY_8, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write i8 to i8 arrays.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts
            addSupported(c, "write_i8", TYPED_I8_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8", TYPED_I8_INT_ARRAY_8, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8", TYPED_I8_BYTE_ARRAY_16, 1, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));

            // untyped arrays, explicit casts
            addSupported(c, "write_i8_to_i8_array", PRIMITIVE_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8_to_i8_array", PRIMITIVE_INT_ARRAY_8, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8_to_i8_array", PRIMITIVE_BYTE_ARRAY_8, 1, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));

            // typed arrays, explicit casts
            addSupported(c, "write_i8_to_i8_array", TYPED_I8_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8_to_i8_array", TYPED_I8_INT_ARRAY_8, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Writing i8 to i8 should always work.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addSupported(c, "write_i8", PRIMITIVE_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8", TYPED_I8_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8_to_i8_array", PRIMITIVE_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i8_to_i8_array", TYPED_I8_INT_ARRAY_1, 0, hex((byte) 0xCA), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI32(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i8", TYPED_I32_INT_ARRAY_1, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8", TYPED_I32_INT_ARRAY_1, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_i32_array", PRIMITIVE_INT_ARRAY_1, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_i32_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i8", TYPED_I64_LONG_ARRAY_1, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8", TYPED_I64_LONG_ARRAY_2, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8", TYPED_I64_BYTE_ARRAY_4, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_i64_array", PRIMITIVE_LONG_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_i64_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
        }

        /**
         * Write i8 to floating point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i8", TYPED_FLOAT_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8", TYPED_DOUBLE_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_float_array", PRIMITIVE_FLOAT_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_float_array", TYPED_FLOAT_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i8_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
        }

        /**
         * Write i8 to a pointer.
         */
        private static void toPointer(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i8", TYPED_POINTER_ARRAY, 0, hex((byte) 0xCA), expectPolyglotException("to foreign object"));
        }
    }

    private static class WriteI16 {

        /**
         * Write an i16 to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /* A short does not fit into a byte. */
            addUnsupported(c, "write_i16", PRIMITIVE_BYTE_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("Can not write array element"));
            addSupported(c, "write_i16", PRIMITIVE_INT_ARRAY_8, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i16", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i16", PRIMITIVE_FLOAT_ARRAY_8, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write i16 to i8 arrays. Two byte writes are expected.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts

            /* Cannot write more than one element to the supplied array. */
            addUnsupported(c, "write_i16", TYPED_I8_INT_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("Invalid array index 1"));
            /* Issue two byte writes. */
            addSupported(c, "write_i16", TYPED_I8_INT_ARRAY_8, 0, hex((short) 0xCAFE), assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1])));
            /*
             * Write 16 bit to the second element. Since the index calculation is done in 16 bit,
             * element 2-3 of the byte array are written.
             */
            addSupported(c, "write_i16", TYPED_I8_BYTE_ARRAY_16, 1, hex((short) 0xCAFE), assertResultByteArray((newArray, idx, b) -> newArray.set(2, b[0]).set(3, b[1])));

            // untyped arrays, explicit casts

            addUnsupported(c, "write_i16_to_i8_array", PRIMITIVE_INT_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_i16_to_i8_array", PRIMITIVE_INT_ARRAY_8, 0, hex((short) 0xCAFE), assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1])));
            addSupported(c, "write_i16_to_i8_array", PRIMITIVE_BYTE_ARRAY_8, 1, hex((short) 0xCAFE), assertResultByteArray((newArray, idx, b) -> newArray.set(2, b[0]).set(3, b[1])));

            // typed arrays, explicit casts

            addUnsupported(c, "write_i16_to_i8_array", TYPED_I8_INT_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_i16_to_i8_array", TYPED_I8_INT_ARRAY_8, 0, hex((short) 0xCAFE), assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1])));
            addSupported(c, "write_i16_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, hex((short) 0xCAFE), assertResultByteArray((newArray, idx, b) -> newArray.set(2, b[0]).set(3, b[1])));
        }

        /**
         * Writing i16 to i16 should always work.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addSupported(c, "write_i16", PRIMITIVE_INT_ARRAY_1, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i16", TYPED_I16_INT_ARRAY_1, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i16_to_i16_array", PRIMITIVE_INT_ARRAY_1, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i16_to_i16_array", TYPED_I16_INT_ARRAY_1, 0, hex((short) 0xCAFE), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI32(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i16", TYPED_I32_INT_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16", TYPED_I32_INT_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_i32_array", PRIMITIVE_INT_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_i32_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i16", TYPED_I64_LONG_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16", TYPED_I64_LONG_ARRAY_2, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16", TYPED_I64_BYTE_ARRAY_4, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_i64_array", PRIMITIVE_LONG_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_i64_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
        }

        /**
         * Write i16 to floating point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i16", TYPED_FLOAT_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16", TYPED_DOUBLE_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_float_array", PRIMITIVE_FLOAT_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_float_array", TYPED_FLOAT_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i16_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, hex((short) 0xCAFE), expectPolyglotException("to foreign object"));
        }
    }

    private static class WriteI32 {

        /**
         * Write an i32 to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /* No explicit type cast. Write one element to polyglot array. */
            addSupported(c, "write_i32", PRIMITIVE_INT_ARRAY_8, 0, hex(0xCAFEFEED), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            /* The i32 cannot be converted to a float. */
            addUnsupported(c, "write_i32", PRIMITIVE_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED), expectPolyglotException("Can not write array element"));
            /* The i32 can be converted to a double. */
            addSupported(c, "write_i32", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write i32 to i8 arrays. Four byte write expected.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts

            /* Cannot write more than one element to the supplied array. */
            addUnsupported(c, "write_i32", TYPED_I8_INT_ARRAY_1, 0, hex(0xCAFEFEED),
                            expectPolyglotException("Invalid array index 1"));
            /* Issue 4 byte writes. */
            addSupported(c, "write_i32", TYPED_I8_INT_ARRAY_8, 0, hex(0xCAFEFEED),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3])));
            /*
             * Write 32 bit to the second element. Since the index calculation is done in 32 bit,
             * element 4-7 of the byte array are written.
             */
            addSupported(c, "write_i32", TYPED_I8_BYTE_ARRAY_16, 1, hex(0xCAFEFEED),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(4, b[0]).set(5, b[1]).set(6, b[2]).set(7, b[3])));

            // untyped arrays, explicit casts

            addUnsupported(c, "write_i32_to_i8_array", PRIMITIVE_INT_ARRAY_1, 0, hex(0xCAFEFEED),
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_i32_to_i8_array", PRIMITIVE_INT_ARRAY_8, 0, hex(0xCAFEFEED),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3])));
            addSupported(c, "write_i32_to_i8_array", PRIMITIVE_BYTE_ARRAY_8, 1, hex(0xCAFEFEED),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(4, b[0]).set(5, b[1]).set(6, b[2]).set(7, b[3])));

            // typed arrays, explicit casts

            addUnsupported(c, "write_i32_to_i8_array", TYPED_I8_INT_ARRAY_1, 0, hex(0xCAFEFEED), expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_i32_to_i8_array", TYPED_I8_INT_ARRAY_8, 0, hex(0xCAFEFEED),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3])));
            addSupported(c, "write_i32_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, hex(0xCAFEFEED),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(4, b[0]).set(5, b[1]).set(6, b[2]).set(7, b[3])));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i32", TYPED_I16_INT_ARRAY_1, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32", TYPED_I16_INT_ARRAY_2, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32", TYPED_I16_BYTE_ARRAY_4, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32_to_i16_array", PRIMITIVE_INT_ARRAY_1, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32_to_i16_array", PRIMITIVE_INT_ARRAY_8, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32_to_i16_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
        }

        /**
         * Writing i32 to i32 should always work.
         */
        private static void toI32(ArrayList<Object[]> c) {
            addSupported(c, "write_i32", PRIMITIVE_INT_ARRAY_1, 0, hex(0xCAFEFEED), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i32", TYPED_I32_INT_ARRAY_1, 0, hex(0xCAFEFEED), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i32_to_i32_array", PRIMITIVE_INT_ARRAY_1, 0, hex(0xCAFEFEED), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i32_to_i32_array", TYPED_I32_INT_ARRAY_1, 0, hex(0xCAFEFEED), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i32", TYPED_I64_LONG_ARRAY_1, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32", TYPED_I64_LONG_ARRAY_2, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32", TYPED_I64_BYTE_ARRAY_4, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32_to_i64_array", PRIMITIVE_LONG_ARRAY_8, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i32_to_i64_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex(0xCAFEFEED), expectPolyglotException("to foreign object"));
        }

        /**
         * Write i32 to floating point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addSupported(c, "write_i32", TYPED_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Float.intBitsToFloat((Integer) value))));
            addUnsupported(c, "write_i32", TYPED_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED),
                            expectPolyglotException("to foreign object"));
            addSupported(c, "write_i32_to_float_array", PRIMITIVE_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Float.intBitsToFloat((Integer) value))));
            addUnsupported(c, "write_i32_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED),
                            expectPolyglotException("to foreign object"));
            addSupported(c, "write_i32_to_float_array", TYPED_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Float.intBitsToFloat((Integer) value))));
            addUnsupported(c, "write_i32_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED),
                            expectPolyglotException("to foreign object"));
        }
    }

    private static class WriteI64 {

        /**
         * Write an i64 to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i64", PRIMITIVE_INT_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("Can not write array element"));
            addUnsupported(c, "write_i64", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("Can not write array element"));
            addUnsupported(c, "write_i64", PRIMITIVE_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("Can not write array element"));
            addSupported(c, "write_i64", PRIMITIVE_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write i64 to i8 arrays. Eight byte writes are expected.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts

            /* Cannot write more than one element to the supplied array. */
            addUnsupported(c, "write_i64", TYPED_I8_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L),
                            expectPolyglotException("Invalid array index 1"));
            /* Issue 4 byte writes. */
            addSupported(c, "write_i64", TYPED_I8_LONG_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            /*
             * Write 64 bit to the second element. Since the index calculation is done in 64 bit,
             * element 8-15 of the byte array are written.
             */
            addSupported(c, "write_i64", TYPED_I8_BYTE_ARRAY_16, 1, hex(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));

            // untyped arrays, explicit casts

            addUnsupported(c, "write_i64_to_i8_array", PRIMITIVE_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L),
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_i64_to_i8_array", PRIMITIVE_LONG_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            addSupported(c, "write_i64_to_i8_array", PRIMITIVE_BYTE_ARRAY_16, 1, hex(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));

            // typed arrays, explicit casts

            addUnsupported(c, "write_i64_to_i8_array", TYPED_I8_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L),
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_i64_to_i8_array", TYPED_I8_LONG_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            addSupported(c, "write_i64_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, hex(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i64", TYPED_I16_BYTE_ARRAY_4, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i64_to_i16_array", PRIMITIVE_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i64_to_i16_array", PRIMITIVE_LONG_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i64_to_i16_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI32(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i64", TYPED_I32_INT_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i64", TYPED_I32_LONG_ARRAY_2, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i64_to_i32_array", PRIMITIVE_INT_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_i64_to_i32_array", PRIMITIVE_BYTE_ARRAY_8, 0, hex(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
        }

        /**
         * Writing i64 to i64 should always work.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addSupported(c, "write_i64", PRIMITIVE_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i64", TYPED_I64_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i64_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_i64_to_i64_array", TYPED_I64_LONG_ARRAY_1, 0, hex(0xCAFEFEED_12345678L), assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write i64 to floating point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "write_i64", TYPED_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            expectPolyglotException("to foreign object "));
            addSupported(c, "write_i64", TYPED_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.longBitsToDouble((Long) value))));
            addUnsupported(c, "write_i64_to_float_array", PRIMITIVE_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            expectPolyglotException("to foreign object "));
            addSupported(c, "write_i64_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.longBitsToDouble((Long) value))));
            addUnsupported(c, "write_i64_to_float_array", TYPED_FLOAT_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            expectPolyglotException("to foreign object "));
            addSupported(c, "write_i64_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, hex(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.longBitsToDouble((Long) value))));
        }

        /**
         * Write i64 to a pointer. We expect that no conversion happens and we get the object back
         * as is.
         */
        private static void toPointer(ArrayList<Object[]> c) {
            addSupported(c, "write_i64", TYPED_POINTER_ARRAY, 0, hex(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, LLVMNativePointer.create((Long) value))));
        }
    }

    private static class WriteFloat {

        /**
         * Write a float to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            addUnsupported(c, "write_float", PRIMITIVE_INT_ARRAY_8, 0, (float) Math.PI, expectPolyglotException("Can not write array element"));
            addSupported(c, "write_float", PRIMITIVE_DOUBLE_ARRAY_8, 0, (float) Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addSupported(c, "write_float", PRIMITIVE_FLOAT_ARRAY_8, 0, (float) Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write float to i8 arrays.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts

            /* Cannot write more than one element to the supplied array. */
            addUnsupported(c, "write_float", TYPED_I8_INT_ARRAY_1, 0, (float) Math.PI,
                            expectPolyglotException("Invalid array index 1"));
            /*
             * Reinterpret float as i32 and issue 4 byte writes.
             */
            addSupported(c, "write_float", TYPED_I8_INT_ARRAY_8, 0, (float) Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3])));
            /*
             * Write float (reinterpreted as i32) to the second element. Since the index calculation
             * is done in 32 bit, element 4-7 of the byte array are written.
             */
            addSupported(c, "write_float", TYPED_I8_BYTE_ARRAY_16, 1, (float) Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(4, b[0]).set(5, b[1]).set(6, b[2]).set(7, b[3])));

            // untyped arrays, explicit casts

            addUnsupported(c, "write_float_to_i8_array", PRIMITIVE_INT_ARRAY_1, 0, (float) Math.PI,
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_float_to_i8_array", PRIMITIVE_INT_ARRAY_8, 0, (float) Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3])));
            addSupported(c, "write_float_to_i8_array", PRIMITIVE_BYTE_ARRAY_8, 1, (float) Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(4, b[0]).set(5, b[1]).set(6, b[2]).set(7, b[3])));

            // typed arrays, explicit casts

            addUnsupported(c, "write_float_to_i8_array", TYPED_I8_INT_ARRAY_1, 0, (float) Math.PI,
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_float_to_i8_array", TYPED_I8_INT_ARRAY_8, 0, (float) Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3])));
            addSupported(c, "write_float_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, (float) Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(4, b[0]).set(5, b[1]).set(6, b[2]).set(7, b[3])));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addUnsupported(c, "write_float", TYPED_I16_INT_ARRAY_1, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float", TYPED_I16_INT_ARRAY_2, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float", TYPED_I16_BYTE_ARRAY_4, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float_to_i16_array", PRIMITIVE_INT_ARRAY_1, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float_to_i16_array", PRIMITIVE_INT_ARRAY_8, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float_to_i16_array", PRIMITIVE_BYTE_ARRAY_8, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
        }

        /**
         * Writing float to float should always work.
         */
        private static void toI32(ArrayList<Object[]> c) {
            /*
             * No type info, assume writing to a float array. The float cannot be converted to an
             * int.
             */
            addUnsupported(c, "write_float", PRIMITIVE_INT_ARRAY_1, 0, (float) Math.PI, expectPolyglotException("Can not write array element"));
            /* Explicitly typed as float array. The float cannot be converted to an int. */
            addUnsupported(c, "write_float_to_float_array", PRIMITIVE_INT_ARRAY_1, 0, (float) Math.PI, expectPolyglotException("Can not write array element"));
            /* Typed as i32 array. Reinterpret float bits as int. */
            addSupported(c, "write_float", TYPED_I32_INT_ARRAY_1, 0, (float) Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, Float.floatToIntBits((Float) value))));
            addSupported(c, "write_float_to_float_array", TYPED_I32_INT_ARRAY_1, 0, (float) Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, Float.floatToIntBits((Float) value))));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addUnsupported(c, "write_float", TYPED_I64_LONG_ARRAY_1, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float", TYPED_I64_LONG_ARRAY_2, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float", TYPED_I64_BYTE_ARRAY_4, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float_to_i64_array", PRIMITIVE_LONG_ARRAY_8, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_float_to_i64_array", PRIMITIVE_BYTE_ARRAY_8, 0, (float) Math.PI, expectPolyglotException("to foreign object"));
        }

        /**
         * Write float to floating point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addSupported(c, "write_float", TYPED_FLOAT_ARRAY_8, 0, (float) Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addUnsupported(c, "write_float", TYPED_DOUBLE_ARRAY_8, 0, (float) Math.PI,
                            expectPolyglotException("to foreign object"));
            addSupported(c, "write_float_to_float_array", PRIMITIVE_FLOAT_ARRAY_8, 0, (float) Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addUnsupported(c, "write_float_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, (float) Math.PI,
                            expectPolyglotException("to foreign object"));
            addSupported(c, "write_float_to_float_array", TYPED_FLOAT_ARRAY_8, 0, (float) Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addUnsupported(c, "write_float_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, (float) Math.PI,
                            expectPolyglotException("to foreign object"));
        }
    }

    private static class WriteDouble {

        /**
         * Write a double to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            addUnsupported(c, "write_double", PRIMITIVE_INT_ARRAY_8, 0, Math.PI, expectPolyglotException("Can not write array element"));
            addSupported(c, "write_double", PRIMITIVE_DOUBLE_ARRAY_8, 0, Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addUnsupported(c, "write_double", PRIMITIVE_FLOAT_ARRAY_8, 0, Math.PI, expectPolyglotException("Can not write array element"));
        }

        /**
         * Write double to i8 arrays.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts

            /* Cannot write more than one element to the supplied array. */
            addUnsupported(c, "write_double", TYPED_I8_INT_ARRAY_1, 0, Math.PI,
                            expectPolyglotException("Invalid array index 1"));
            /* Reinterpret double as i64 and issue 8 byte writes. */
            addSupported(c, "write_double", TYPED_I8_INT_ARRAY_8, 0, Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            /*
             * Write double reinterpreted as i64 to the second element. Since the index calculation
             * is done in 64 bit, element 8-16 of the byte array are written.
             */
            addSupported(c, "write_double", TYPED_I8_BYTE_ARRAY_16, 1, Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));

            // untyped arrays, explicit casts

            addUnsupported(c, "write_double_to_i8_array", PRIMITIVE_INT_ARRAY_1, 0, Math.PI,
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_double_to_i8_array", PRIMITIVE_INT_ARRAY_8, 0, Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            addSupported(c, "write_double_to_i8_array", PRIMITIVE_BYTE_ARRAY_16, 1, Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));

            // typed arrays, explicit casts

            addUnsupported(c, "write_double_to_i8_array", TYPED_I8_INT_ARRAY_1, 0, Math.PI,
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_double_to_i8_array", TYPED_I8_INT_ARRAY_8, 0, Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            addSupported(c, "write_double_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, Math.PI,
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addUnsupported(c, "write_double", TYPED_I16_INT_ARRAY_1, 0, Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_double", TYPED_I16_INT_ARRAY_2, 0, Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_double", TYPED_I16_BYTE_ARRAY_4, 0, Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_double_to_i16_array", PRIMITIVE_INT_ARRAY_1, 0, Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_double_to_i16_array", PRIMITIVE_INT_ARRAY_8, 0, Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_double_to_i16_array", PRIMITIVE_BYTE_ARRAY_8, 0, Math.PI, expectPolyglotException("to foreign object"));
        }

        /**
         * Writing double to double should always work.
         */
        private static void toI32(ArrayList<Object[]> c) {
            addUnsupported(c, "write_double", PRIMITIVE_INT_ARRAY_1, 0, Math.PI, expectPolyglotException("Can not write array element"));
            addUnsupported(c, "write_double", TYPED_I32_INT_ARRAY_1, 0, Math.PI, expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_double_to_double_array", PRIMITIVE_INT_ARRAY_1, 0, Math.PI, expectPolyglotException("Can not write array element"));
            addUnsupported(c, "write_double_to_double_array", TYPED_I32_INT_ARRAY_1, 0, Math.PI, expectPolyglotException("to foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addSupported(c, "write_double", TYPED_I64_LONG_ARRAY_1, 0, Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, Double.doubleToLongBits((Double) value))));
            addSupported(c, "write_double", TYPED_I64_LONG_ARRAY_2, 0, Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, Double.doubleToLongBits((Double) value))));
            addSupported(c, "write_double", TYPED_I64_BYTE_ARRAY_4, 0, Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, Double.doubleToLongBits((Double) value))));
            addSupported(c, "write_double_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.doubleToLongBits((Double) value))));
            addSupported(c, "write_double_to_i64_array", PRIMITIVE_LONG_ARRAY_8, 0, Math.PI, assertResult((newArray, idx, value) -> newArray.set(idx, Double.doubleToLongBits((Double) value))));
            /* Casting to i64 array, but an i64 does not fit into a byte. */
            addUnsupported(c, "write_double_to_i64_array", PRIMITIVE_BYTE_ARRAY_8, 0, Math.PI, expectPolyglotException("Can not write array element"));
        }

        /**
         * Write double to doubleing point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "write_double", TYPED_FLOAT_ARRAY_8, 0, Math.PI,
                            expectPolyglotException("to foreign object"));
            addSupported(c, "write_double", TYPED_DOUBLE_ARRAY_8, 0, Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addUnsupported(c, "write_double_to_double_array", PRIMITIVE_FLOAT_ARRAY_8, 0, Math.PI,
                            expectPolyglotException("Can not write array element"));
            addSupported(c, "write_double_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            addUnsupported(c, "write_double_to_double_array", TYPED_FLOAT_ARRAY_8, 0, Math.PI,
                            expectPolyglotException("to foreign object"));
            addSupported(c, "write_double_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }

        /**
         * Write i64 to a pointer. We expect that no conversion happens and we get the object back
         * as is.
         */
        private static void toPointer(ArrayList<Object[]> c) {
            addSupported(c, "write_double", TYPED_POINTER_ARRAY, 0, Math.PI,
                            assertResult((newArray, idx, value) -> newArray.set(idx, LLVMNativePointer.create(Double.doubleToLongBits((Double) value)))));
        }
    }

    private static class WritePointer {

        /**
         * Write a pointer to an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            addUnsupported(c, "write_pointer", PRIMITIVE_INT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("Can not write array element"));
            addUnsupported(c, "write_pointer", PRIMITIVE_DOUBLE_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("Can not write array element"));
            addUnsupported(c, "write_pointer", PRIMITIVE_FLOAT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("Can not write array element"));
            addSupported(c, "write_pointer", OBJECT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), assertResult((newArray, idx, value) -> newArray.set(idx, value)));

        }

        /**
         * Write pointer to i8 arrays.
         */
        private static void toI8(ArrayList<Object[]> c) {

            // typed arrays, no casts

            /* Cannot write more than one element to the supplied array. */
            addUnsupported(c, "write_pointer", TYPED_I8_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("Invalid array index 1"));
            /* Reinterpret pointer as i64 (asNative) and issue 8 byte writes. */
            addSupported(c, "write_pointer", TYPED_I8_LONG_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            /*
             * Write pointer reinterpreted as i64 to the second element. Since the index calculation
             * is done in 64 bit, element 8-16 of the byte array are written.
             */
            addSupported(c, "write_pointer", TYPED_I8_BYTE_ARRAY_16, 1, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));

            // untyped arrays, explicit casts

            addUnsupported(c, "write_pointer_to_i8_array", PRIMITIVE_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_pointer_to_i8_array", PRIMITIVE_LONG_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            addSupported(c, "write_pointer_to_i8_array", PRIMITIVE_BYTE_ARRAY_16, 1, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));

            // typed arrays, explicit casts

            addUnsupported(c, "write_pointer_to_i8_array", TYPED_I8_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("Invalid array index 1"));
            addSupported(c, "write_pointer_to_i8_array", TYPED_I8_LONG_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(0, b[0]).set(1, b[1]).set(2, b[2]).set(3, b[3]).set(4, b[4]).set(5, b[5]).set(6, b[6]).set(7, b[7])));
            addSupported(c, "write_pointer_to_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResultByteArray((newArray, idx, b) -> newArray.set(8, b[0]).set(9, b[1]).set(10, b[2]).set(11, b[3]).set(12, b[4]).set(13, b[5]).set(14, b[6]).set(15, b[7])));
        }

        /**
         * These calls all fail since we to not issue multiple writes unless the receiver is a i8
         * array.
         */
        private static void toI16(ArrayList<Object[]> c) {
            addUnsupported(c, "write_pointer", TYPED_I16_BYTE_ARRAY_4, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_pointer_to_i16_array", PRIMITIVE_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_pointer_to_i16_array", PRIMITIVE_LONG_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_pointer_to_i16_array", PRIMITIVE_BYTE_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple write unless the receiver is a i8
         * array.
         */
        private static void toI32(ArrayList<Object[]> c) {
            addUnsupported(c, "write_pointer", TYPED_I32_INT_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_pointer", TYPED_I32_LONG_ARRAY_2, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_pointer_to_i32_array", PRIMITIVE_INT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
            addUnsupported(c, "write_pointer_to_i32_array", PRIMITIVE_BYTE_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L), expectPolyglotException("to foreign object"));
        }

        /**
         * Writing pointer to pointer should always work.
         */
        private static void toI64(ArrayList<Object[]> c) {
            addSupported(c, "write_pointer", OBJECT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
            /* Untyped array. Assume that the pointer can be written as is, but it can't. */
            addUnsupported(c, "write_pointer", PRIMITIVE_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            expectPolyglotException("Can not write array element"));
            /*
             * However, if we know the array is an i64 array, the pointer is converted to a long
             * (asNative).
             */
            addSupported(c, "write_pointer", TYPED_I64_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, LLVMNativePointer.cast(value).asNative())));
            addSupported(c, "write_pointer_to_i64_array", PRIMITIVE_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, LLVMNativePointer.cast(value).asNative())));
            addSupported(c, "write_pointer_to_i64_array", TYPED_I64_LONG_ARRAY_1, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, LLVMNativePointer.cast(value).asNative())));
        }

        /**
         * Write pointer to floating point arrays.
         */
        private static void toFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "write_pointer", TYPED_FLOAT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            expectPolyglotException("to foreign object "));
            addSupported(c, "write_pointer", TYPED_DOUBLE_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.longBitsToDouble(LLVMNativePointer.cast(value).asNative()))));
            addUnsupported(c, "write_pointer_to_float_array", PRIMITIVE_FLOAT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            expectPolyglotException("to foreign object "));
            addSupported(c, "write_pointer_to_double_array", PRIMITIVE_DOUBLE_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.longBitsToDouble(LLVMNativePointer.cast(value).asNative()))));
            addUnsupported(c, "write_pointer_to_float_array", TYPED_FLOAT_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            expectPolyglotException("to foreign object "));
            addSupported(c, "write_pointer_to_double_array", TYPED_DOUBLE_ARRAY_8, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, Double.longBitsToDouble(LLVMNativePointer.cast(value).asNative()))));
        }

        /**
         * Write pointer to a pointer. We expect that no conversion happens and we get the object
         * back as is.
         */
        private static void toPointer(ArrayList<Object[]> c) {
            addSupported(c, "write_pointer", TYPED_POINTER_ARRAY, 0, LLVMNativePointer.create(0xCAFEFEED_12345678L),
                            assertResult((newArray, idx, value) -> newArray.set(idx, value)));
        }
    }

    @Parameterized.Parameter(0) public String function;
    @Parameterized.Parameter(1) public InputConsumer assertion;
    @Parameterized.Parameter(2) public ExpectedExceptionConsumer expectedException;
    /**
     * This parameter is only used to indicate whether the call is expected to work or not.
     */
    @Parameterized.Parameter(3) public ExpectedResultMarker support;
    @Parameterized.Parameter(4) public ParameterArray parameters;

    private static Value polyglotWritePointerLibrary;

    private static Object pointerTypeId;

    /**
     * Load bitcode library. Method name needs to be different than in the base class, otherwise it
     * is not executed.
     */
    @BeforeClass
    public static void loadPolyglotWritePointerLibrary() {
        polyglotWritePointerLibrary = loadTestBitcodeValue("writePolyglotArray.c");
        Value lib = runWithPolyglot.getPolyglotContext().asValue(polyglotWritePointerLibrary);
        Value getTypes = lib.getMember("get_pointer_typeid");

        getTypes.execute(new TestCallback(1, args -> {
            pointerTypeId = args[0];
            return null;
        }));
    }

    private static Object getPointerTypeID() {
        return pointerTypeId;
    }

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Test
    public void test() {
        Value write = polyglotWritePointerLibrary.getMember(function);
        Assert.assertNotNull("Function not found: " + function, write);
        expectedException.accept(thrown);
        Object[] arguments = parameters.getArguments();
        write.execute(arguments);
        Object modifiedArray = arguments[0];
        Object freshPolyglotArray = parameters.getArguments()[0];
        int idx = (Integer) arguments[1];
        Object value = arguments[2];
        assertion.accept(modifiedArray, PolyglotArrayBuilder.create(freshPolyglotArray), idx, value);
    }
}
