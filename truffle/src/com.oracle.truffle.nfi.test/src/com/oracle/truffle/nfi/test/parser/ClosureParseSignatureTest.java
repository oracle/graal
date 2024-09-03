/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test.parser;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.nfi.test.parser.backend.TestCallInfo;
import com.oracle.truffle.nfi.test.parser.backend.TestClosure;
import com.oracle.truffle.nfi.test.parser.backend.TestSignature;

public class ClosureParseSignatureTest extends ParseSignatureTest {

    private interface Validator {

        void validateSignature(TestSignature signature);
    }

    private static Matcher<Object> isClosureType(Validator v) {
        return new TypeSafeMatcher<>(TestClosure.class) {

            @Override
            protected boolean matchesSafely(Object item) {
                TestClosure closure = (TestClosure) item;
                v.validateSignature(closure.signature);
                return true;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("is closure");
            }
        };
    }

    private static Object[] mkCallableArgs(int argCount) {
        Object[] ret = new Object[argCount];
        // need to have something that's executable, even though it's never actually called
        Arrays.fill(ret, new TestCallback(0, null));
        return ret;
    }

    private static Object doCall(String signature, int argCount) {
        try {
            Source source = Source.newBuilder("nfi", String.format("with test %s", signature), "parseSignature").build();
            Object parsedSignature = runWithPolyglot.getTruffleTestEnv().parseInternal(source).call();
            return SignatureLibrary.getUncached().call(parsedSignature, testSymbol, mkCallableArgs(argCount));
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void testWithClosure(String closureSig, int argCount, Validator validator) {
        TestCallInfo closureArgInfo = (TestCallInfo) doCall(String.format("(%s):void", closureSig), 1);
        assertThat("return type", closureArgInfo.signature.retType, is(NativeSimpleType.VOID));
        Assert.assertEquals("argument count", 1, closureArgInfo.signature.argTypes.size());
        assertThat("argument type", closureArgInfo.signature.argTypes.get(0), is(NativeSimpleType.POINTER));
        assertThat("argument value", closureArgInfo.args[0], isClosureType(validator));

        Object closureRetSymbol = doCall(String.format("() : %s", closureSig), 0);
        try {
            TestCallInfo returnedCallInfo = (TestCallInfo) InteropLibrary.getUncached().execute(closureRetSymbol, ParseSignatureTest.mkArgs(argCount));
            TestCallInfo closureRetInfo = (TestCallInfo) returnedCallInfo.executable;
            Assert.assertEquals("argument count", 0, closureRetInfo.signature.argTypes.size());
            assertThat("return type", closureRetInfo.signature.retType, is(NativeSimpleType.POINTER));
            validator.validateSignature(returnedCallInfo.signature);
        } catch (InteropException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testClosureNoArgs() {
        testWithClosure("():void", 0, sig -> {
            assertThat("return type", sig.retType, is(NativeSimpleType.VOID));
            Assert.assertEquals("argument count", 0, sig.argTypes.size());
        });
    }

    @Test
    public void testClosureOneArg() {
        testWithClosure("(float):double", 1, sig -> {
            assertThat("return type", sig.retType, is(NativeSimpleType.DOUBLE));
            Assert.assertEquals("argument count", 1, sig.argTypes.size());
            assertThat("argument type", sig.argTypes.get(0), is(NativeSimpleType.FLOAT));
        });
    }

    @Test
    public void testClosureVarargs() {
        testWithClosure("(string, ...sint32):double", 2, sig -> {
            assertThat("return type", sig.retType, is(NativeSimpleType.DOUBLE));
            Assert.assertEquals("argument count", 2, sig.argTypes.size());
            Assert.assertEquals("fixed argument count", 1, sig.fixedArgCount);
        });
    }
}
