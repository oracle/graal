/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback;
import com.oracle.truffle.llvm.tests.interop.values.TestCallback.Function;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;

public class ManagedMemAccessTestBase extends InteropTestBase {

    protected static TruffleObject testLibrary;

    protected enum TestType {
        I8(1, false) {
            @Override
            void serialize(ByteBuffer buffer, Object value) {
                buffer.put((byte) (long) value);
            }
        },
        I16(2, false) {
            @Override
            void serialize(ByteBuffer buffer, Object value) {
                buffer.putShort((short) (long) value);
            }
        },
        I32(4, false) {
            @Override
            void serialize(ByteBuffer buffer, Object value) {
                buffer.putInt((int) (long) value);
            }
        },
        I64(8, false) {
            @Override
            void serialize(ByteBuffer buffer, Object value) {
                buffer.putLong((long) value);
            }
        },
        FLOAT(4, true) {
            @Override
            void serialize(ByteBuffer buffer, Object value) {
                buffer.putFloat((float) (double) value);
            }
        },
        DOUBLE(8, true) {
            @Override
            void serialize(ByteBuffer buffer, Object value) {
                buffer.putDouble((double) value);
            }
        };

        final int elementSize;
        final boolean isFloating;

        TestType(int elementSize, boolean isFloating) {
            this.elementSize = elementSize;
            this.isFloating = isFloating;
        }

        abstract void serialize(ByteBuffer buffer, Object value);
    }

    protected static Object[] types;

    protected static Object getTypeID(TestType type) {
        return types[type.ordinal()];
    }

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = loadTestBitcodeInternal("managedMemmove.c");

        Value lib = runWithPolyglot.getPolyglotContext().asValue(testLibrary);
        Value getTypes = lib.getMember("get_types");

        types = new Object[TestType.values().length];
        getTypes.execute(new TestCallback(1, new Function() {

            int idx = 0;

            @Override
            public Object call(Object... args) {
                types[idx++] = args[0];
                return null;
            }
        }));
    }

    protected static byte[] serialize(TestType type, Object array) {
        int size = Array.getLength(array) * type.elementSize;
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.nativeOrder());
        for (int i = 0; i < Array.getLength(array); i++) {
            type.serialize(buffer, Array.get(array, i));
        }
        return buffer.array();
    }
}
