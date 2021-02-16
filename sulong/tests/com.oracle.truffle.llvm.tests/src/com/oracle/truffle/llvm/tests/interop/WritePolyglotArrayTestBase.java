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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;

import org.junit.Assert;

import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public class WritePolyglotArrayTestBase extends PolyglotArrayTestBase {

    // create test entries

    protected static void addTestIntern(Collection<Object[]> configs, String function, InputConsumer assertion, ExpectedExceptionConsumer expectedException, ExpectedResultMarker support,
                    Object... parameters) {
        configs.add(new Object[]{function, assertion, expectedException, support, new ParameterArray(parameters)});
    }

    /**
     * Adds a test that is expected to fail.
     */
    protected static void addUnsupported(Collection<Object[]> configs, String function, Object object, int index, Object value, ExpectedExceptionConsumer expectedException) {
        addTestIntern(configs, function, InputConsumer::doNothing, expectedException, ExpectedResultMarker.UNSUPPORTED, object, index, value);
    }

    /**
     * Adds a test that is expected to succeed.
     */
    protected static void addSupported(Collection<Object[]> configs, String function, Object object, int index, Object value, InputConsumer assertion) {
        addTestIntern(configs, function, assertion, PolyglotArrayTestBase::doNothing, ExpectedResultMarker.SUPPORTED, object, index, value);
    }

    @FunctionalInterface
    protected interface InputConsumer {
        void accept(Object actualArray, PolyglotArrayBuilder newArray, int idx, Object value);

        /**
         * @param actualArray
         * @param newArray
         * @param idx
         * @param value
         */
        static void doNothing(Object actualArray, PolyglotArrayBuilder newArray, int idx, Object value) {
        }
    }

    @FunctionalInterface
    protected interface ResultProducer {
        Object accept(PolyglotArrayBuilder newArray, int idx, Object value);
    }

    @FunctionalInterface
    protected interface ResultProducerByteArrayInput {
        Object accept(PolyglotArrayBuilder newArray, int idx, byte[] inputBytes);
    }

    public static InputConsumer assertResult(ResultProducer producer) {
        return (actualArray, newArray, idx, value) -> assertPolyglotArrayEquals(producer.accept(newArray, idx, value), actualArray);
    }

    public static InputConsumer assertResultByteArray(ResultProducerByteArrayInput producer) {
        return (actualArray, newArray, idx, value) -> {
            byte[] byteArray = toByteArray(value);
            assertPolyglotArrayEquals(producer.accept(newArray, idx, byteArray), actualArray);
        };
    }

    private static byte[] toByteArray(Object obj) {
        final ByteBuffer bb;
        final ByteOrder bo = ByteOrder.LITTLE_ENDIAN;
        if (obj instanceof Short) {
            bb = ByteBuffer.allocate(Short.BYTES).order(bo);
            bb.putShort((Short) obj);
        } else if (obj instanceof Float) {
            bb = ByteBuffer.allocate(Float.BYTES).order(bo);
            bb.putFloat((Float) obj);
        } else if (obj instanceof Integer) {
            bb = ByteBuffer.allocate(Integer.BYTES).order(bo);
            bb.putInt((Integer) obj);
        } else if (obj instanceof Long) {
            bb = ByteBuffer.allocate(Long.BYTES).order(bo);
            bb.putLong((Long) obj);
        } else if (obj instanceof Double) {
            bb = ByteBuffer.allocate(Double.BYTES).order(bo);
            bb.putDouble((Double) obj);
        } else if (LLVMNativePointer.isInstance(obj)) {
            bb = ByteBuffer.allocate(Long.BYTES).order(bo);
            bb.putLong(LLVMNativePointer.cast(obj).asNative());
        } else {
            Assert.fail("Unexpected Type: " + obj.getClass());
            throw new AssertionError();
        }
        return bb.array();
    }
}
