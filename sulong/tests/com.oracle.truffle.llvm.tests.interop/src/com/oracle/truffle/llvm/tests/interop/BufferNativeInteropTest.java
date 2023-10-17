/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.function.Consumer;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BufferNativeInteropTest extends InteropTestBase {
    @FunctionalInterface
    interface AsType {
        Object as(Value v);
    }

    @FunctionalInterface
    interface Write<T> {
        void write(Value v, ByteOrder order, long offset, T value);
    }

    @FunctionalInterface
    interface Read {
        Object read(Value v, ByteOrder order, long offset);
    }

    @FunctionalInterface
    interface NextRandom {
        Object next(Random rand);
    }

    @Parameter public String name;
    @Parameter(1) public String type;
    @Parameter(2) public int bytes;
    @Parameter(3) public Object testValue;
    @Parameter(4) public AsType conversion;
    @Parameter(5) public Read read;
    @Parameter(6) public Write<Object> write;
    @Parameter(7) public NextRandom next;

    static byte nextByte(Random r) {
        byte[] b = new byte[1];
        r.nextBytes(b);
        return b[0];
    }

    static short nextShort(Random r) {
        return (short) r.nextInt();
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> allTypes() {
        return Arrays.asList(new Object[][]{
                        {"i8", "i8", 1, (byte) 42, (AsType) Value::asByte, (Read) (v, bo, off) -> v.readBufferByte(off), (Write<Byte>) (v, bo, off, val) -> v.writeBufferByte(off, val),
                                        (NextRandom) BufferNativeInteropTest::nextByte},
                        {"i16", "i16", 2, (short) 43, (AsType) Value::asShort, (Read) Value::readBufferShort, (Write<Short>) Value::writeBufferShort, (NextRandom) BufferNativeInteropTest::nextShort},
                        {"i32", "i32", 4, 44, (AsType) Value::asInt, (Read) Value::readBufferInt, (Write<Integer>) Value::writeBufferInt, (NextRandom) Random::nextInt},
                        {"i64", "i64", 8, -42L, (AsType) Value::asLong, (Read) Value::readBufferLong, (Write<Long>) Value::writeBufferLong, (NextRandom) Random::nextLong},
                        {"i64BA", "i64", 8, -42L, (AsType) Value::asLong, (Read) (v, order, offset) -> {
                            byte[] arr = new byte[8];
                            v.readBuffer(offset, arr, 0, 8);
                            return ByteBuffer.wrap(arr).order(order).getLong();
                        }, (Write<Long>) Value::writeBufferLong, (NextRandom) Random::nextLong},
                        {"float", "float", 4, -100.101F, (AsType) Value::asFloat, (Read) Value::readBufferFloat, (Write<Float>) Value::writeBufferFloat, (NextRandom) Random::nextFloat},
                        {"double", "double", 8, -200.202D, (AsType) Value::asDouble, (Read) Value::readBufferDouble, (Write<Double>) Value::writeBufferDouble, (NextRandom) Random::nextDouble},
        });
    }

    public static class BufferLibrary {
        Value lib = loadTestBitcodeValue("bufferNativeInterop.c");

        public Value allocBuffer(long length) {
            return lib.getMember("allocBuffer").execute(length);
        }

        public Value fromBuffer(Value buffer, long size) {
            return lib.getMember("fromBuffer").execute(buffer, size);
        }

        public Value asBuffer(Value buffer) {
            return fromBuffer(buffer, buffer.getBufferSize());
        }

        public Value fromConstBuffer(Value buffer) {
            return lib.getMember("fromConstBuffer").execute(buffer, buffer.getBufferSize());
        }

        public void freeBuffer(Value buffer) {
            lib.getMember("freeBuffer").executeVoid(buffer);
        }

        public Value read(String type, Value buffer, long offset) {
            return lib.getMember("read_" + type).execute(buffer, offset);
        }

        public Value write(String type, Value buffer, long offset, Object value) {
            return lib.getMember("write_" + type).execute(buffer, offset, value);
        }

        public long getBufferSize(Value buffer) {
            return lib.getMember("getBufferSize").execute(buffer).asLong();
        }

        public boolean isBufferWritable(Value buffer) {
            return lib.getMember("isBufferWritable").execute(buffer).asBoolean();
        }

        public boolean hasBufferElements(Value buffer) {
            return lib.getMember("hasBufferElements").execute(buffer).asBoolean();
        }

        public Value aString() {
            return lib.getMember("aString").execute();
        }
    }

    private static BufferLibrary lib;
    private Random rnd;

    @BeforeClass
    public static void loadTestBitcode() {
        lib = new BufferLibrary();
    }

    @Before
    public void setupRandom() {
        rnd = new Random(1234567890);
    }

    static void withBuffer(long length, Consumer<Value> fn) {
        Value buffer = lib.allocBuffer(length);
        try {
            fn.accept(buffer);
        } finally {
            lib.freeBuffer(buffer);
        }
    }

    static void withConstBuffer(long length, Consumer<Value> fn) {
        withBuffer(length, v -> fn.accept(lib.fromConstBuffer(v)));
    }

    public static class NoParametersTests extends InteropTestBase {
        @BeforeClass
        public static void loadTestBitcode() {
            lib = new BufferLibrary();
        }

        @Test
        public void isWritableTest() {
            withBuffer(16, v -> {
                assertTrue(v.isBufferWritable());
                assertFalse(lib.fromConstBuffer(v).isBufferWritable());
            });
        }

        @Test
        public void hasBufferMembersTest() {
            withBuffer(16, v -> {
                assertTrue(v.hasBufferElements());
                assertTrue(lib.fromConstBuffer(v).hasBufferElements());
                assertFalse(lib.aString().hasBufferElements());
            });
        }

        @Test
        public void getBufferSizeTest() {
            Consumer<Long> testSize = size -> {
                withBuffer(size, v -> {
                    assertEquals((long) size, v.getBufferSize());
                    assertEquals((long) size, lib.fromConstBuffer(v).getBufferSize());
                });
            };

            testSize.accept(0L);
            testSize.accept(16L);
            testSize.accept(32L);
        }

        @Test
        public void nativeGetBufferSizeTest() {
            Consumer<Long> testSize = size -> {
                withBuffer(size, v -> {
                    assertEquals((long) size, lib.getBufferSize(v));
                    assertEquals((long) size, lib.getBufferSize(lib.fromConstBuffer(v)));
                });
            };

            testSize.accept(0L);
            testSize.accept(16L);
            testSize.accept(32L);
        }

        @Test
        public void nativeGetJavaBufferSizeTest() {
            Consumer<Long> testSize = size -> {
                ByteBuffer b = ByteBuffer.allocate(size.intValue());
                Value v = Value.asValue(b);
                assertEquals((long) size, lib.getBufferSize(v));
                assertEquals((long) size, lib.getBufferSize(lib.fromConstBuffer(v)));
            };

            testSize.accept(0L);
            testSize.accept(16L);
            testSize.accept(32L);
        }

        @Test(expected = PolyglotException.class)
        public void notAsBuffer() {
            Value v = Value.asValue(new ArrayList<>());
            lib.fromBuffer(v, 16);
        }

        @Test(expected = PolyglotException.class)
        public void bufferTooSmall() {
            Value v = Value.asValue(ByteBuffer.allocate(16));
            lib.fromBuffer(v, 32);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void writeConstFails() {
        withConstBuffer(16, v -> write.write(v, ByteOrder.nativeOrder(), 0L, testValue));
    }

    public void assertBuffersEqual(ByteBuffer javaBuffer, Value nativeBuffer) {
        for (int i = 0; i < javaBuffer.capacity(); i++) {
            assertEquals(javaBuffer.get(i), lib.read("i8", nativeBuffer, i).asByte());
        }
    }

    @Test
    public void testInteropWrite() {
        int size = 32;
        ByteBuffer javaBuffer = ByteBuffer.allocate(size);
        withBuffer(size, nativeBuffer -> {
            for (int i = 0; i < 16; i++) {
                ByteOrder order = rnd.nextBoolean() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                int offset = rnd.nextInt(javaBuffer.capacity() - bytes);

                Object val = next.next(rnd);
                write.write(Value.asValue(javaBuffer), order, offset, val);
                write.write(nativeBuffer, order, offset, val);

                assertBuffersEqual(javaBuffer, nativeBuffer);
                assertBuffersEqual(javaBuffer, lib.fromConstBuffer(nativeBuffer));
            }
        });
    }

    @Test
    public void testInteropRead() {
        int size = 32;
        ByteBuffer javaBuffer = ByteBuffer.allocate(size);
        withBuffer(size, nativeBuffer -> {
            Value vJavaBuffer = Value.asValue(javaBuffer);
            for (int i = 0; i < 16; i++) {
                ByteOrder order = rnd.nextBoolean() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                int offset = rnd.nextInt(javaBuffer.capacity() - bytes);

                Object val = next.next(rnd);
                write.write(vJavaBuffer, order, offset, val);
                write.write(nativeBuffer, order, offset, val);

                assertEquals(read.read(vJavaBuffer, order, offset), read.read(nativeBuffer, order, offset));
                assertEquals(read.read(vJavaBuffer, order, offset), read.read(lib.fromConstBuffer(nativeBuffer), order, offset));
            }
        });
    }

    @Test
    public void nativeReadTheSameJavaBuffer() {
        long offset = 1L;

        ByteBuffer buffer = ByteBuffer.allocate(16);
        Value vBuffer = Value.asValue(buffer);
        buffer.order(ByteOrder.nativeOrder());

        write.write(vBuffer, ByteOrder.nativeOrder(), offset, testValue);
        assertEquals(testValue, conversion.as(lib.read(type, vBuffer, offset)));
    }

    @Test
    public void nativeReadTheSameNativeBuffer() {
        long offset = 1L;

        withBuffer(16, v -> {
            write.write(v, ByteOrder.nativeOrder(), offset, testValue);
            assertEquals(testValue, conversion.as(lib.read(type, v, offset)));
        });
    }

    @Test
    public void nativeWriteTheSame() {
        long offset = 1L;

        withBuffer(16, v -> {
            lib.write(type, v, offset, testValue);
            assertEquals(testValue, read.read(v, ByteOrder.nativeOrder(), offset));
        });
    }

    @Test
    public void asBufferTheSame() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.order(ByteOrder.nativeOrder());
        write.write(Value.asValue(buffer), ByteOrder.nativeOrder(), 1L, testValue);

        Value v = lib.asBuffer(Value.asValue(buffer));
        assertEquals(testValue, read.read(v, ByteOrder.nativeOrder(), 1L));

        withBuffer(16, v1 -> {
            Value v2 = lib.asBuffer(v1);
            write.write(v2, ByteOrder.nativeOrder(), 1L, testValue);
            assertEquals(testValue, read.read(v2, ByteOrder.nativeOrder(), 1L));
        });
    }

    @Test
    public void bufferWriteBeginningAndEndTest() {
        withBuffer(16, v -> {
            write.write(v, ByteOrder.nativeOrder(), 0, testValue);
            write.write(v, ByteOrder.nativeOrder(), 16 - bytes, testValue);
        });
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void bufferWriteNegativeFailsTest() {
        withBuffer(16, v -> {
            write.write(v, ByteOrder.nativeOrder(), -1, testValue);
        });
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void bufferWritePastEndFailsTest() {
        withBuffer(16, v -> {
            write.write(v, ByteOrder.nativeOrder(), 16 - bytes + 1, testValue);
        });
    }
}
