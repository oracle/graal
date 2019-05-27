/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class NullableNFITest extends NFITest {

    @Parameters
    public static Object[] data() {
        return new Object[]{
                        new NullObject(),
                        "a string",
                        new TestCallback(0, (args) -> 0)
        };
    }

    @Parameter(0) public Object param;

    public class TestNullableArgNode extends SendExecuteNode {

        public TestNullableArgNode() {
            super("null_arg", String.format("(%s) : string", NativeSimpleType.NULLABLE));
        }
    }

    // TruffleObject with isNull==true translates to native NULL pointer
    public String getExpected() {
        return UNCACHED_INTEROP.isNull(param) ? "null" : "non-null";
    }

    @Test
    public void testNullableArg(@Inject(TestNullableArgNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        String expected = getExpected();

        Object ret = callTarget.call(param);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", expected, UNCACHED_INTEROP.asString(obj));
    }

    public class TestNullableCallbackRetNode extends SendExecuteNode {

        public TestNullableCallbackRetNode() {
            super("callback_null_ret", String.format("(():%s) : string", NativeSimpleType.NULLABLE));
        }
    }

    @Test
    public void testNullableCallbackRet(@Inject(TestNullableCallbackRetNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        String expected = getExpected();

        TruffleObject nullCallback = new TestCallback(0, (args) -> param);

        Object ret = callTarget.call(nullCallback);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", expected, UNCACHED_INTEROP.asString(obj));
    }
}
