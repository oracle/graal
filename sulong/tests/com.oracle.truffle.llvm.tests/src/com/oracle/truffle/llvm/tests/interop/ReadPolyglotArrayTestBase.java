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

import java.util.Collection;
import java.util.function.Consumer;

import org.graalvm.polyglot.Value;
import org.junit.Assert;

public class ReadPolyglotArrayTestBase extends PolyglotArrayTestBase {

    // create test entries

    protected static void addTestIntern(Collection<Object[]> configs, String function, ResultConsumer assertion, ExpectedExceptionConsumer expectedException, ExpectedResultMarker support,
                    Object... parameters) {
        configs.add(new Object[]{function, assertion, expectedException, support, new ParameterArray(parameters)});
    }

    /**
     * Adds a test that is expected to fail.
     */
    protected static void addUnsupported(Collection<Object[]> configs, String function, Object object, int index, ExpectedExceptionConsumer expectedException) {
        addTestIntern(configs, function, PolyglotArrayTestBase::doNothing, expectedException, ExpectedResultMarker.UNSUPPORTED, object, index);
    }

    /**
     * Adds a test that is expected to succeed.
     */
    protected static void addSupported(Collection<Object[]> configs, String function, Object object, int index, ResultConsumer assertion) {
        addTestIntern(configs, function, assertion, PolyglotArrayTestBase::doNothing, ExpectedResultMarker.SUPPORTED, object, index);
    }

    protected interface ResultConsumer extends Consumer<Value> {
    }

    // Helpers to make the test specification more readable.

    protected static ResultConsumer resultEquals(byte expected) {
        return (Value ret) -> PolyglotArrayTestBase.assertEqualsHex(expected, ret.asByte());
    }

    protected static ResultConsumer resultNotEquals(byte unexpected) {
        return (Value ret) -> Assert.assertNotEquals(unexpected, ret.asByte());
    }

    protected static ResultConsumer resultEquals(short expected) {
        return (Value ret) -> PolyglotArrayTestBase.assertEqualsHex(expected, ret.asShort());
    }

    protected static ResultConsumer resultNotEquals(short unexpected) {
        return (Value ret) -> Assert.assertNotEquals(unexpected, ret.asShort());
    }

    protected static ResultConsumer resultEquals(int expected) {
        return (Value ret) -> PolyglotArrayTestBase.assertEqualsHex(expected, ret.asInt());
    }

    protected static ResultConsumer resultNotEquals(int unexpected) {
        return (Value ret) -> Assert.assertNotEquals(unexpected, ret.asInt());
    }

    protected static ResultConsumer resultEquals(long expected) {
        return (Value ret) -> PolyglotArrayTestBase.assertEqualsHex(expected, ret.asLong());
    }

    protected static ResultConsumer resultNotEquals(long unexpected) {
        return (Value ret) -> Assert.assertNotEquals(unexpected, ret.asLong());
    }

    protected static ResultConsumer resultEquals(float expected) {
        return (Value ret) -> Assert.assertEquals(expected, ret.asFloat(), 0);
    }

    protected static ResultConsumer resultNotEquals(float unexpected) {
        return (Value ret) -> Assert.assertNotEquals(unexpected, ret.asFloat());
    }

    protected static ResultConsumer resultEquals(double expected) {
        return (Value ret) -> Assert.assertEquals(expected, ret.asDouble(), 0);
    }

    protected static ResultConsumer resultNotEquals(double unexpected) {
        return (Value ret) -> Assert.assertNotEquals(unexpected, ret.asDouble());
    }

    protected static ResultConsumer resultEquals(Object expected) {
        return (Value ret) -> Assert.assertEquals(Value.asValue(expected), ret);
    }

    protected static ResultConsumer resultNotEquals(Object unexpected) {
        return (Value ret) -> Assert.assertNotEquals(Value.asValue(unexpected), ret);
    }
}
