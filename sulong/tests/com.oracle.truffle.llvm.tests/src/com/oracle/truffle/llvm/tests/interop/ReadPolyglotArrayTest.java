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

import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_BYTE_ARRAY_16;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_BYTE_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_DOUBLE_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_FLOAT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_INT_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_LONG_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_LONG_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_LONG_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.BOXED_SHORT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_DOUBLE_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_FLOAT_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I16_BYTE_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I16_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I16_INT_ARRAY_2;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I32_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I32_LONG_ARRAY_2;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I64_BYTE_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I64_LONG_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I64_LONG_ARRAY_2;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I8_BYTE_ARRAY_16;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I8_INT_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I8_INT_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I8_LONG_ARRAY_1;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I8_LONG_ARRAY_4;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_I8_LONG_ARRAY_8;
import static com.oracle.truffle.llvm.tests.interop.ReadPolyglotArrayTest.TestObject.TYPED_POINTER_ARRAY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropReadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.tests.interop.values.ArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.DoubleArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.LongArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback;
import com.oracle.truffle.llvm.tests.interop.values.TypedArrayObject;
import com.oracle.truffle.tck.TruffleRunner;

/**
 * Test and document which values can be read from polyglot arrays. This mainly tests
 * {@link LLVMInteropReadNode}.
 */
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class ReadPolyglotArrayTest extends ReadPolyglotArrayTestBase {

    /**
     * Object representing an arbitrary "pointer".
     */
    private static final TruffleObject A_POINTER = new TruffleObject() {
    };

    /**
     * Wrap test objects in an enum to get a nice name for the parameter.
     */
    public enum TestObject implements java.util.function.Supplier<Object> {
        BOXED_SHORT_ARRAY_1(() -> new ArrayObject(0xCAFE)),
        BOXED_INT_ARRAY_1(() -> new ArrayObject(0xCAFEFEED)),
        BOXED_INT_ARRAY_4(() -> new ArrayObject(0xCAFEFEED, 0x12345678, 0x87654321, 0x18273645)),
        BOXED_LONG_ARRAY_1(() -> new ArrayObject(0xCAFEFEED_12345678L)),
        BOXED_LONG_ARRAY_4(() -> new ArrayObject(0xCAFEFEED_12345678L, 0x87654321_18273645L, 0x11223344_55667788L, 0x99AABBCC_DDEEFF00L)),
        BOXED_LONG_ARRAY_8(
                        () -> new ArrayObject(0xCAFEFEED_12345678L, 0x87654321_18273645L, 0x11223344_55667788L, 0x99AABBCC_DDEEFF00L,
                                        0x11111111_22222222L, 0x33333333_44444444L, 0x55555555_66666666L, 0x77777777_88888888L)),
        BOXED_FLOAT_ARRAY_8(
                        () -> new ArrayObject((float) Math.PI, (float) Math.E, (float) -Math.PI, (float) -Math.E, (float) (1.0 / Math.PI), (float) (1.0 / Math.E), (float) (1.0 / -Math.PI),
                                        (float) (1.0 / -Math.E))),
        BOXED_DOUBLE_ARRAY_8(() -> new ArrayObject(Math.PI, Math.E, -Math.PI, -Math.E, (1.0 / Math.PI), (1.0 / Math.E), (1.0 / -Math.PI), (1.0 / -Math.E))),
        BOXED_BYTE_ARRAY_8(() -> new ArrayObject(0xCA, 0xFE, 0xFE, 0xED, 0x12, 0x34, 0x56, 0x78)),
        BOXED_BYTE_ARRAY_16(() -> new ArrayObject(0xCA, 0xFE, 0xFE, 0xED, 0x12, 0x34, 0x56, 0x78, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88)),
        TYPED_I8_INT_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I8), 0xCAFEFEED)),
        TYPED_I8_INT_ARRAY_4(() -> new LongArrayObject(getTypeID(TestType.I8), 0xCAFEFEED, 0x12345678, 0x87654321, 0x18273645)),
        TYPED_I8_LONG_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I8), 0xCAFEFEED_12345678L)),
        TYPED_I8_LONG_ARRAY_4(() -> new LongArrayObject(getTypeID(TestType.I8), 0xCAFEFEED_12345678L, 0x87654321_18273645L, 0x11223344_55667788L, 0x99AABBCC_DDEEFF00L)),
        TYPED_I8_LONG_ARRAY_8(
                        () -> new LongArrayObject(getTypeID(TestType.I8), 0xCAFEFEED_12345678L, 0x87654321_18273645L, 0x11223344_55667788L, 0x99AABBCC_DDEEFF00L,
                                        0x11111111_22222222L, 0x33333333_44444444L, 0x55555555_66666666L, 0x77777777_88888888L)),
        TYPED_I8_BYTE_ARRAY_16(() -> new LongArrayObject(getTypeID(TestType.I8), 0xCA, 0xFE, 0xFE, 0xED, 0x12, 0x34, 0x56, 0x78, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88)),
        TYPED_I16_INT_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I16), 0xCAFEFEED)),
        TYPED_I16_INT_ARRAY_2(() -> new LongArrayObject(getTypeID(TestType.I16), 0xCAFEFEED, 0x12345678)),
        TYPED_I16_BYTE_ARRAY_4(() -> new LongArrayObject(getTypeID(TestType.I16), 0xCAFE, 0xFEED, 0x1234, 0x5678)),
        TYPED_I32_INT_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I32), 0xCAFEFEED)),
        TYPED_I32_LONG_ARRAY_2(() -> new LongArrayObject(getTypeID(TestType.I32), 0xCAFEFEED_12345678L, 0x11223344_55667788L)),
        TYPED_I64_LONG_ARRAY_1(() -> new LongArrayObject(getTypeID(TestType.I64), 0xCAFEFEED_12345678L)),
        TYPED_I64_LONG_ARRAY_2(() -> new LongArrayObject(getTypeID(TestType.I64), 0xCAFEFEED_12345678L, 0x11223344_55667788L)),
        TYPED_I64_BYTE_ARRAY_4(() -> new LongArrayObject(getTypeID(TestType.I64), 0xCAFEL, 0xFEEDL, 0x1234L, 0x5678L)),
        TYPED_POINTER_ARRAY(() -> new TypedArrayObject(getPointerTypeID(), A_POINTER)),
        TYPED_FLOAT_ARRAY_8(
                        () -> new DoubleArrayObject(getTypeID(TestType.FLOAT), (float) Math.PI, (float) Math.E, (float) -Math.PI, (float) -Math.E, (float) (1.0 / Math.PI), (float) (1.0 / Math.E),
                                        (float) (1.0 / -Math.PI), (float) (1.0 / -Math.E))),
        TYPED_DOUBLE_ARRAY_8(() -> new DoubleArrayObject(getTypeID(TestType.DOUBLE), Math.PI, Math.E, -Math.PI, -Math.E, (1.0 / Math.PI), (1.0 / Math.E), (1.0 / -Math.PI), (1.0 / -Math.E)));

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
        // read i8
        ReadI8.untyped(c);
        ReadI8.fromI8(c);
        ReadI8.fromI16(c);
        ReadI8.fromI32(c);
        ReadI8.fromI64(c);
        ReadI8.fromFloatingPoint(c);
        ReadI8.fromPointer(c);
        // read i16
        ReadI16.untyped(c);
        ReadI16.fromI8(c);
        ReadI16.fromI16(c);
        ReadI16.fromI32(c);
        ReadI16.fromI64(c);
        ReadI16.fromFloatingPoint(c);
        // read i32
        ReadI32.untyped(c);
        ReadI32.fromI8(c);
        ReadI32.fromI16(c);
        ReadI32.fromI32(c);
        ReadI32.fromI64(c);
        ReadI32.fromFloatingPoint(c);
        // read i64
        ReadI64.untyped(c);
        ReadI64.fromI8(c);
        ReadI64.fromI16(c);
        ReadI64.fromI32(c);
        ReadI64.fromI64(c);
        ReadI64.fromFloatingPoint(c);
        ReadI64.fromPointer(c);
        // read float
        ReadFloat.untyped(c);
        ReadFloat.fromI8(c);
        ReadFloat.fromI16(c);
        ReadFloat.fromI32(c);
        ReadFloat.fromI64(c);
        ReadFloat.fromFloatingPoint(c);
        // read double
        ReadDouble.untyped(c);
        ReadDouble.fromI8(c);
        ReadDouble.fromI16(c);
        ReadDouble.fromI32(c);
        ReadDouble.fromI64(c);
        ReadDouble.fromFloatingPoint(c);
        ReadDouble.fromPointer(c);
        // read pointer
        ReadPointer.untyped(c);
        ReadPointer.fromI8(c);
        ReadPointer.fromI16(c);
        ReadPointer.fromI32(c);
        ReadPointer.fromI64(c);
        ReadPointer.fromFloatingPoint(c);
        ReadPointer.fromPointer(c);
        return c;
    }

    private static class ReadI8 {

        /**
         * Read an i8 from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i8", BOXED_INT_ARRAY_1, 0, resultEquals((byte) 0xED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i8", BOXED_SHORT_ARRAY_1, 0, resultEquals((byte) 0xFE));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i8", BOXED_BYTE_ARRAY_8, 0, resultEquals((byte) 0xCA));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_i8", BOXED_DOUBLE_ARRAY_8, 0, resultEquals((byte) Math.PI));
            /*
             * No explicit type cast. Read one float element from polyglot array and cast it.
             */
            addSupported(c, "read_i8", BOXED_FLOAT_ARRAY_8, 0, resultEquals((byte) (float) Math.PI));
        }

        /**
         * Read i8 from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            addSupported(c, "read_i8", TYPED_I8_INT_ARRAY_1, 0, resultEquals((byte) 0xED));
            addSupported(c, "read_i8_from_i8_array", BOXED_INT_ARRAY_4, 0, resultEquals((byte) 0xED));
            addSupported(c, "read_i8_from_i8_array", BOXED_BYTE_ARRAY_8, 1, resultEquals((byte) 0xFE));
            addSupported(c, "read_i8_from_i8_array", TYPED_I8_INT_ARRAY_1, 0, resultEquals((byte) 0xED));
            addSupported(c, "read_i8_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals((byte) 0xCA));
            addSupported(c, "read_i8_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, resultEquals((byte) 0xFE));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i8", TYPED_I16_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_i16_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_i16_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i8", TYPED_I32_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_i32_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_i32_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i8", TYPED_I64_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_i64_array", BOXED_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_i64_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Read i8 from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i8", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_float_array", BOXED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_float_array", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i8_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Reading i8 from a pointer should not work.
         */
        private static void fromPointer(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i8", TYPED_POINTER_ARRAY, 0, expectPolyglotException("from foreign object"));
        }
    }

    private static class ReadI16 {

        /**
         * Read an i16 from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i16", BOXED_INT_ARRAY_1, 0, resultEquals((short) 0xFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i16", BOXED_SHORT_ARRAY_1, 0, resultEquals((short) 0xCAFE));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i16", BOXED_BYTE_ARRAY_8, 0, resultEquals((short) 0xCA));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_i16", BOXED_DOUBLE_ARRAY_8, 0, resultEquals((short) Math.PI));
            /*
             * No explicit type cast. Read one float element from polyglot array and cast it.
             */
            addSupported(c, "read_i16", BOXED_FLOAT_ARRAY_8, 0, resultEquals((short) (float) Math.PI));
        }

        /**
         * Read i16 from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            // typed arrays, no casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i16", TYPED_I8_INT_ARRAY_1, 0, resultEquals(toNativeEndian((short) 0xED00)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i16", TYPED_I8_INT_ARRAY_4, 2, expectPolyglotException("Invalid array index 4"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 16 bit result in 2 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i16", TYPED_I8_INT_ARRAY_4, 0, resultEquals(toNativeEndian((short) 0xED78)));

            // untyped arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i16_from_i8_array", BOXED_INT_ARRAY_1, 0, resultEquals(toNativeEndian((short) 0xED00)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i16_from_i8_array", BOXED_INT_ARRAY_4, 2, expectPolyglotException("Invalid array index 4"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 32 bit result in 4 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i16_from_i8_array", BOXED_INT_ARRAY_4, 0, resultNotEquals(toNativeEndian((short) 0xCAFE)));
            /*
             * Read the second i16 from a "byte array".
             */
            addSupported(c, "read_i16_from_i8_array", BOXED_BYTE_ARRAY_8, 1, resultEquals(toNativeEndian((short) 0xFEED)));

            // typed arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i16_from_i8_array", TYPED_I8_INT_ARRAY_1, 0, resultEquals(toNativeEndian((short) 0xED00)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i16_from_i8_array", TYPED_I8_INT_ARRAY_4, 2, expectPolyglotException("Invalid array index 4"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 32 bit result in 4 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i16_from_i8_array", TYPED_I8_INT_ARRAY_4, 0, resultEquals(toNativeEndian((short) 0xED78)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i16_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(toNativeEndian((short) 0xCAFE)));
            /*
             * Read the second i16 from a "byte array".
             */
            addSupported(c, "read_i16_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, resultEquals(toNativeEndian((short) 0xFEED)));
        }

        /**
         * Reading i16 from i16 should always work.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addSupported(c, "read_i16", BOXED_INT_ARRAY_1, 0, resultEquals((short) 0xFEED));
            addSupported(c, "read_i16", TYPED_I16_INT_ARRAY_1, 0, resultEquals((short) 0xFEED));
            addSupported(c, "read_i16_from_i16_array", BOXED_INT_ARRAY_1, 0, resultEquals((short) 0xFEED));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i16", TYPED_I32_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_i32_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_i32_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i16", TYPED_I64_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_i64_array", BOXED_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_i64_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Read i16 from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i16", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_float_array", BOXED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_float_array", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i16_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }
    }

    private static class ReadI32 {

        /**
         * Read an i32 from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i32", BOXED_INT_ARRAY_1, 0, resultEquals(0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i32", BOXED_INT_ARRAY_4, 0, resultEquals(0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i32", BOXED_BYTE_ARRAY_8, 0, resultEquals(0xCA));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_i32", BOXED_DOUBLE_ARRAY_8, 0, resultEquals((int) Math.PI));
            /*
             * No explicit type cast. Read one float element from polyglot array and cast it.
             */
            addSupported(c, "read_i32", BOXED_FLOAT_ARRAY_8, 0, resultEquals((int) (float) Math.PI));
        }

        /**
         * Read i32 from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            // typed arrays, no casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i32", TYPED_I8_INT_ARRAY_1, 0, resultEquals(toNativeEndian(0xED000000)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i32", TYPED_I8_INT_ARRAY_4, 1, expectPolyglotException("Invalid array index 4"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 32 bit result in 4 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i32", TYPED_I8_INT_ARRAY_4, 0, resultNotEquals(toNativeEndian(0xCAFEFEED)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i32", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(toNativeEndian(0xCAFEFEED)));

            // untyped arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i32_from_i8_array", BOXED_INT_ARRAY_1, 0, resultEquals(toNativeEndian(0xED000000)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i32_from_i8_array", BOXED_INT_ARRAY_4, 1, expectPolyglotException("Invalid array index 4"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 32 bit result in 4 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i32_from_i8_array", BOXED_INT_ARRAY_4, 0, resultNotEquals(toNativeEndian(0xCAFEFEED)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i32_from_i8_array", BOXED_BYTE_ARRAY_8, 0, resultEquals(toNativeEndian(0xCAFEFEED)));
            /*
             * Read the second i32 from a "byte array".
             */
            addSupported(c, "read_i32_from_i8_array", BOXED_BYTE_ARRAY_8, 1, resultEquals(toNativeEndian(0x12345678)));

            // typed arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i32_from_i8_array", TYPED_I8_INT_ARRAY_1, 0, resultEquals(toNativeEndian(0xED000000)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i32_from_i8_array", TYPED_I8_INT_ARRAY_4, 1, expectPolyglotException("Invalid array index 4"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 32 bit result in 4 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i32_from_i8_array", TYPED_I8_INT_ARRAY_4, 0, resultNotEquals(toNativeEndian(0xCAFEFEED)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i32_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(toNativeEndian(0xCAFEFEED)));
            /*
             * Read the second i32 from a "byte array".
             */
            addSupported(c, "read_i32_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, resultEquals(toNativeEndian(0x12345678)));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i32", TYPED_I16_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32", TYPED_I16_INT_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32", TYPED_I16_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32_from_i16_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32_from_i16_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32_from_i16_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Reading i32 from i32 should always work.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addSupported(c, "read_i32", BOXED_INT_ARRAY_1, 0, resultEquals(0xCAFEFEED));
            addSupported(c, "read_i32", TYPED_I32_INT_ARRAY_1, 0, resultEquals(0xCAFEFEED));
            addSupported(c, "read_i32_from_i32_array", BOXED_INT_ARRAY_1, 0, resultEquals(0xCAFEFEED));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i32", TYPED_I64_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32", TYPED_I64_LONG_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32", TYPED_I64_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32_from_i64_array", BOXED_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32_from_i64_array", BOXED_LONG_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i32_from_i64_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Read i32 from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addSupported(c, "read_i32", TYPED_FLOAT_ARRAY_8, 0, resultEquals(Float.floatToRawIntBits((float) Math.PI)));
            addUnsupported(c, "read_i32", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_i32_from_float_array", BOXED_FLOAT_ARRAY_8, 0, resultEquals(Float.floatToRawIntBits((float) Math.PI)));
            addUnsupported(c, "read_i32_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_i32_from_float_array", TYPED_FLOAT_ARRAY_8, 0, resultEquals(Float.floatToRawIntBits((float) Math.PI)));
            addUnsupported(c, "read_i32_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }
    }

    private static class ReadI64 {

        /**
         * Read an i64 from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i64", BOXED_INT_ARRAY_1, 0, resultEquals(0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i64", BOXED_INT_ARRAY_4, 0, resultEquals(0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i64", BOXED_LONG_ARRAY_1, 0, resultEquals(0xCAFEFEED_12345678L));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i64", BOXED_LONG_ARRAY_4, 0, resultEquals(0xCAFEFEED_12345678L));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_i64", BOXED_BYTE_ARRAY_8, 0, resultEquals(0xCA));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_i64", BOXED_DOUBLE_ARRAY_8, 0, resultEquals((long) Math.PI));
            /*
             * No explicit type cast. Read one float element from polyglot array and cast it.
             */
            addSupported(c, "read_i64", BOXED_FLOAT_ARRAY_8, 0, resultEquals((long) (float) Math.PI));
        }

        /**
         * Read i64 from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            // typed arrays, no casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i64", TYPED_I8_LONG_ARRAY_4, 0, resultEquals(toNativeEndian(0x78458800_00000000L)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i64", TYPED_I8_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i64", TYPED_I8_LONG_ARRAY_8, 0, resultNotEquals(toNativeEndian(0xCAFEFEED_12345678L)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i64", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(toNativeEndian(0xCAFEFEED_12345678L)));

            // untyped arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i64_from_i8_array", BOXED_LONG_ARRAY_4, 0, resultEquals(toNativeEndian(0x78458800_00000000L)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i64_from_i8_array", BOXED_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i64_from_i8_array", BOXED_LONG_ARRAY_8, 0, resultNotEquals(toNativeEndian(0xCAFEFEED_12345678L)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i64_from_i8_array", BOXED_BYTE_ARRAY_8, 0, resultEquals(toNativeEndian(0xCAFEFEED_12345678L)));
            /*
             * Read the second i64 from a "byte array".
             */
            addSupported(c, "read_i64_from_i8_array", BOXED_BYTE_ARRAY_16, 1, resultEquals(toNativeEndian(0x11223344_55667788L)));

            // typed arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_i64_from_i8_array", TYPED_I8_LONG_ARRAY_1, 0, resultEquals(toNativeEndian(0x78000000_00000000L)));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_i64_from_i8_array", TYPED_I8_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_i64_from_i8_array", TYPED_I8_LONG_ARRAY_8, 0, resultNotEquals(toNativeEndian(0xCAFEFEED_12345678L)));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_i64_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(toNativeEndian(0xCAFEFEED_12345678L)));
            /*
             * Read the second i64 from a "byte array".
             */
            addSupported(c, "read_i64_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, resultEquals(toNativeEndian(0x11223344_55667788L)));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i64", TYPED_I16_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64", TYPED_I16_INT_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64", TYPED_I16_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64_from_i16_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64_from_i16_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64_from_i16_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i64", TYPED_I32_LONG_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64_from_i32_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64_from_i32_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_i64_from_i32_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Reading i64 from i64 should always work.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            addSupported(c, "read_i64", BOXED_LONG_ARRAY_1, 0, resultEquals(0xCAFEFEED_12345678L));
            addSupported(c, "read_i64", TYPED_I64_LONG_ARRAY_1, 0, resultEquals(0xCAFEFEED_12345678L));
            addSupported(c, "read_i64_from_i64_array", BOXED_LONG_ARRAY_1, 0, resultEquals(0xCAFEFEED_12345678L));
        }

        /**
         * Read i64 from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "read_i64", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_i64", TYPED_DOUBLE_ARRAY_8, 0, resultEquals(Double.doubleToRawLongBits(Math.PI)));
            addUnsupported(c, "read_i64_from_float_array", BOXED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_i64_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, resultEquals(Double.doubleToRawLongBits(Math.PI)));
            addUnsupported(c, "read_i64_from_float_array", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_i64_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, resultEquals(Double.doubleToRawLongBits(Math.PI)));
        }

        /**
         * Reading i64 from a pointer. We expect that no conversion happens and we get the object
         * back as is.
         */
        private static void fromPointer(ArrayList<Object[]> c) {
            addSupported(c, "read_i64", TYPED_POINTER_ARRAY, 0, resultEquals(A_POINTER));
        }
    }

    private static class ReadFloat {

        /**
         * Read a float from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_float", BOXED_INT_ARRAY_1, 0, resultEquals((float) 0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_float", BOXED_BYTE_ARRAY_8, 0, resultEquals((float) 0xCA));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_float", BOXED_DOUBLE_ARRAY_8, 0, resultEquals((float) Math.PI));
            /*
             * No explicit type cast. Read one float element from polyglot array and cast it.
             */
            addSupported(c, "read_float", BOXED_FLOAT_ARRAY_8, 0, resultEquals((float) Math.PI));
        }

        /**
         * Read float from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            // typed arrays, no casts
            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_float", TYPED_I8_INT_ARRAY_1, 0, resultEquals(Float.intBitsToFloat(toNativeEndian(0xED000000))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_float", TYPED_I8_INT_ARRAY_4, 1, expectPolyglotException("Invalid array index 4"));
            addSupported(c, "read_float", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(Float.intBitsToFloat(toNativeEndian(0xCAFEFEED))));

            // untyped arrays, explicit casts
            addSupported(c, "read_float_from_i8_array", BOXED_BYTE_ARRAY_8, 0, resultEquals(Float.intBitsToFloat(toNativeEndian(0xCAFEFEED))));
            addSupported(c, "read_float_from_i8_array", BOXED_BYTE_ARRAY_8, 1, resultEquals(Float.intBitsToFloat(toNativeEndian(0x12345678))));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addUnsupported(c, "read_float", TYPED_I16_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float", TYPED_I16_INT_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float", TYPED_I16_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float_from_i16_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float_from_i16_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float_from_i16_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Reading float from float should always work.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addSupported(c, "read_float", BOXED_INT_ARRAY_1, 0, resultEquals((float) 0xCAFEFEED));
            addSupported(c, "read_float", TYPED_I32_INT_ARRAY_1, 0, resultEquals(Float.intBitsToFloat(0xCAFEFEED)));
            addSupported(c, "read_float_from_i32_array", BOXED_INT_ARRAY_1, 0, resultEquals(Float.intBitsToFloat(0xCAFEFEED)));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            addUnsupported(c, "read_float", TYPED_I64_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float", TYPED_I64_LONG_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float", TYPED_I64_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float_from_i64_array", BOXED_LONG_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float_from_i64_array", BOXED_LONG_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_float_from_i64_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Read float from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addSupported(c, "read_float", TYPED_FLOAT_ARRAY_8, 0, resultEquals((float) Math.PI));
            addUnsupported(c, "read_float", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_float_from_float_array", BOXED_FLOAT_ARRAY_8, 0, resultEquals((float) Math.PI));
            addUnsupported(c, "read_float_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_float_from_float_array", TYPED_FLOAT_ARRAY_8, 0, resultEquals((float) Math.PI));
            addUnsupported(c, "read_float_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }
    }

    private static class ReadDouble {

        /**
         * Read a double from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_double", BOXED_INT_ARRAY_1, 0, resultEquals((double) 0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_double", BOXED_BYTE_ARRAY_8, 0, resultEquals((double) 0xCA));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_double", BOXED_DOUBLE_ARRAY_8, 0, resultEquals(Math.PI));
            /*
             * No explicit type cast. Read one double element from polyglot array and cast it.
             */
            addSupported(c, "read_double", BOXED_FLOAT_ARRAY_8, 0, resultEquals((double) (float) Math.PI));
        }

        /**
         * Read double from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            // typed arrays, no casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_double", TYPED_I8_LONG_ARRAY_4, 0, resultEquals(Double.longBitsToDouble(toNativeEndian(0x78458800_00000000L))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_double", TYPED_I8_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_double", TYPED_I8_LONG_ARRAY_8, 0, resultNotEquals(Double.longBitsToDouble(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_double", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(Double.longBitsToDouble(toNativeEndian(0xCAFEFEED_12345678L))));

            // untyped arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_double_from_i8_array", BOXED_LONG_ARRAY_4, 0, resultEquals(Double.longBitsToDouble(toNativeEndian(0x78458800_00000000L))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_double_from_i8_array", BOXED_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_double_from_i8_array", BOXED_LONG_ARRAY_8, 0, resultNotEquals(Double.longBitsToDouble(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_double_from_i8_array", BOXED_BYTE_ARRAY_8, 0, resultEquals(Double.longBitsToDouble(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read the second double from a "byte array".
             */
            addSupported(c, "read_double_from_i8_array", BOXED_BYTE_ARRAY_16, 1, resultEquals(Double.longBitsToDouble(toNativeEndian(0x11223344_55667788L))));

            // typed arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_double_from_i8_array", TYPED_I8_LONG_ARRAY_1, 0, resultEquals(Double.longBitsToDouble(toNativeEndian(0x78000000_00000000L))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_double_from_i8_array", TYPED_I8_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_double_from_i8_array", TYPED_I8_LONG_ARRAY_8, 0, resultNotEquals(Double.longBitsToDouble(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_double_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(Double.longBitsToDouble(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read the second double from a "byte array".
             */
            addSupported(c, "read_double_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, resultEquals(Double.longBitsToDouble(toNativeEndian(0x11223344_55667788L))));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addUnsupported(c, "read_double", TYPED_I16_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double", TYPED_I16_INT_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double", TYPED_I16_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double_from_i16_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double_from_i16_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double_from_i16_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addUnsupported(c, "read_double", TYPED_I32_LONG_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double_from_i32_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double_from_i32_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_double_from_i32_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Reading double from i64 should always work.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            addSupported(c, "read_double", BOXED_LONG_ARRAY_1, 0, resultEquals((double) 0xCAFEFEED_12345678L));
            addSupported(c, "read_double", TYPED_I64_LONG_ARRAY_1, 0, resultEquals(Double.longBitsToDouble(0xCAFEFEED_12345678L)));
            addSupported(c, "read_double_from_i64_array", BOXED_LONG_ARRAY_1, 0, resultEquals(Double.longBitsToDouble(0xCAFEFEED_12345678L)));
        }

        /**
         * Read double from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "read_double", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_double", TYPED_DOUBLE_ARRAY_8, 0, resultEquals(Math.PI));
            addUnsupported(c, "read_double_from_float_array", BOXED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_double_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, resultEquals(Math.PI));
            addUnsupported(c, "read_double_from_float_array", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addSupported(c, "read_double_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, resultEquals(Math.PI));
        }

        /**
         * Reading double from a pointer should not work.
         */
        private static void fromPointer(ArrayList<Object[]> c) {
            addUnsupported(c, "read_double", TYPED_POINTER_ARRAY, 0, expectPolyglotException("Cannot convert a pointer to DOUBLE"));
        }

    }

    private static class ReadPointer {

        /**
         * Read a pointer from an untyped polyglot array without cast.
         */
        private static void untyped(ArrayList<Object[]> c) {
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_pointer", BOXED_INT_ARRAY_1, 0, resultEquals(0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_pointer", BOXED_INT_ARRAY_4, 0, resultEquals(0xCAFEFEED));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_pointer", BOXED_LONG_ARRAY_1, 0, resultEquals(0xCAFEFEED_12345678L));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_pointer", BOXED_LONG_ARRAY_4, 0, resultEquals(0xCAFEFEED_12345678L));
            /*
             * No explicit type cast. Read one element from polyglot array.
             */
            addSupported(c, "read_pointer", BOXED_BYTE_ARRAY_8, 0, resultEquals(0xCA));
            /*
             * This might be unexpected. We read a double from the array. The result is an LLVM
             * pointer pointing to a double. However, there are no pointers in polyglot so the
             * result is dereferenced. Thus the double is return.
             */
            addSupported(c, "read_pointer", BOXED_DOUBLE_ARRAY_8, 0, resultEquals(Math.PI));
            /*
             * This might be unexpected. We read a float from the array. The result is an LLVM
             * pointer pointing to a float. However, there are no pointers in polyglot so the result
             * is dereferenced. Thus the float is return.
             */
            addSupported(c, "read_pointer", BOXED_FLOAT_ARRAY_8, 0, resultEquals((float) Math.PI));
        }

        /**
         * Read pointer from i8 arrays.
         */
        private static void fromI8(ArrayList<Object[]> c) {
            // typed arrays, no casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_pointer", TYPED_I8_LONG_ARRAY_4, 0, resultEquals(LLVMNativePointer.create(toNativeEndian(0x78458800_00000000L))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_pointer", TYPED_I8_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_pointer", TYPED_I8_LONG_ARRAY_8, 0, resultNotEquals(LLVMNativePointer.create(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_pointer", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(LLVMNativePointer.create(toNativeEndian(0xCAFEFEED_12345678L))));

            // untyped arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_pointer_from_i8_array", BOXED_LONG_ARRAY_4, 0, resultEquals(LLVMNativePointer.create(toNativeEndian(0x78458800_00000000L))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_pointer_from_i8_array", BOXED_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_pointer_from_i8_array", BOXED_LONG_ARRAY_8, 0, resultNotEquals(LLVMNativePointer.create(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_pointer_from_i8_array", BOXED_BYTE_ARRAY_8, 0, resultEquals(LLVMNativePointer.create(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read the second pointer from a "byte array".
             */
            addSupported(c, "read_pointer_from_i8_array", BOXED_BYTE_ARRAY_16, 1, resultEquals(LLVMNativePointer.create(toNativeEndian(0x11223344_55667788L))));

            // typed arrays, explicit casts

            /* Out of bounds reads are ok as long as we are in the aligned bounds. */
            addSupported(c, "read_pointer_from_i8_array", TYPED_I8_LONG_ARRAY_1, 0, resultEquals(LLVMNativePointer.create(toNativeEndian(0x78000000_00000000L))));
            /* Cannot read out of (aligned) bounds of the supplied array. */
            addUnsupported(c, "read_pointer_from_i8_array", TYPED_I8_LONG_ARRAY_8, 1, expectPolyglotException("Invalid array index 8"));
            /*
             * The result might be unexpected. The interop value is interpreted as an byte array,
             * thus reading 64 bit result in 8 byte reads. Only the least significant byte of every
             * element is read.
             */
            addSupported(c, "read_pointer_from_i8_array", TYPED_I8_LONG_ARRAY_8, 0, resultNotEquals(LLVMNativePointer.create(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read from a "byte array". The supplied objects is in fact not a byte array but an
             * object array of Integers, but it does not really matter.
             */
            addSupported(c, "read_pointer_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 0, resultEquals(LLVMNativePointer.create(toNativeEndian(0xCAFEFEED_12345678L))));
            /*
             * Read the second pointer from a "byte array".
             */
            addSupported(c, "read_pointer_from_i8_array", TYPED_I8_BYTE_ARRAY_16, 1, resultEquals(LLVMNativePointer.create(toNativeEndian(0x11223344_55667788L))));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI16(ArrayList<Object[]> c) {
            addUnsupported(c, "read_pointer", TYPED_I16_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer", TYPED_I16_INT_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer", TYPED_I16_BYTE_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_i16_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_i16_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_i16_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * These calls all fail since we to not issue multiple reads unless the receiver is a i8
         * array.
         */
        private static void fromI32(ArrayList<Object[]> c) {
            addUnsupported(c, "read_pointer", TYPED_I32_LONG_ARRAY_2, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_i32_array", BOXED_INT_ARRAY_1, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_i32_array", BOXED_INT_ARRAY_4, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_i32_array", BOXED_BYTE_ARRAY_8, 0, expectPolyglotException("from foreign object"));
        }

        /**
         * Reading pointer from i64 should always work.
         */
        private static void fromI64(ArrayList<Object[]> c) {
            /*
             * This might be unexpected. If no type is attached, the array is interpreted as an
             * array of objects (which happen to be boxed Longs). We expect that we get back the
             * Long object.
             */
            addSupported(c, "read_pointer", BOXED_LONG_ARRAY_1, 0, (Value ret) -> Assert.assertEquals(Long.valueOf(0xCAFEFEED_12345678L), ret.as(Long.class)));
            addSupported(c, "read_pointer", TYPED_I64_LONG_ARRAY_1, 0, resultEquals(LLVMNativePointer.create(0xCAFEFEED_12345678L)));
            addSupported(c, "read_pointer_from_i64_array", BOXED_LONG_ARRAY_1, 0, resultEquals(LLVMNativePointer.create(0xCAFEFEED_12345678L)));
        }

        /**
         * Read pointer from floating point arrays.
         */
        private static void fromFloatingPoint(ArrayList<Object[]> c) {
            addUnsupported(c, "read_pointer", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("Cannot convert a double to POINTER"));
            addUnsupported(c, "read_pointer_from_float_array", BOXED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_double_array", BOXED_DOUBLE_ARRAY_8, 0, expectPolyglotException("Cannot convert a double to POINTER"));
            addUnsupported(c, "read_pointer_from_float_array", TYPED_FLOAT_ARRAY_8, 0, expectPolyglotException("from foreign object"));
            addUnsupported(c, "read_pointer_from_double_array", TYPED_DOUBLE_ARRAY_8, 0, expectPolyglotException("Cannot convert a double to POINTER"));
        }

        /**
         * Reading pointer from a pointer.
         */
        private static void fromPointer(ArrayList<Object[]> c) {
            addSupported(c, "read_pointer", TYPED_POINTER_ARRAY, 0, resultEquals(A_POINTER));
        }
    }

    @Parameterized.Parameter(0) public String function;
    @Parameterized.Parameter(1) public ResultConsumer assertion;
    @Parameterized.Parameter(2) public ExpectedExceptionConsumer expectedException;
    /**
     * This parameter is only used to indicate whether the call is expected to work or not.
     */
    @Parameterized.Parameter(3) public ExpectedResultMarker support;
    @Parameterized.Parameter(4) public ParameterArray parameters;

    private static Value polyglotReadPointerLibrary;

    private static Object pointerTypeId;

    /**
     * Load bitcode library. Method name needs to be different than in the base class, otherwise it
     * is not executed.
     */
    @BeforeClass
    public static void loadPolyglotReadPointerLibrary() {
        polyglotReadPointerLibrary = loadTestBitcodeValue("readPolyglotArray.c");
        Value lib = runWithPolyglot.getPolyglotContext().asValue(polyglotReadPointerLibrary);
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
        Value read = polyglotReadPointerLibrary.getMember(function);
        Assert.assertNotNull("Function not found: " + function, read);
        expectedException.accept(thrown);
        Value ret = read.execute(parameters.getArguments());
        assertion.accept(ret);
    }
}
