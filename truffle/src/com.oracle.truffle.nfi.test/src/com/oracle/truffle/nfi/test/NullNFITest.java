/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TruffleRunner.ParametersFactory.class)
public class NullNFITest extends NFITest {

    public enum NullMode {
        NATIVE_NULL, // TruffleObject with isNull==true translates to native NULL pointer
        BOXED_NULL, // TruffleObject with isNull==true is passed as-is
        NULL_FORBIDDEN // passing NULL to native code is forbidden
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        ret.add(new Object[]{"pointer", NullMode.NATIVE_NULL});
        ret.add(new Object[]{"string", NullMode.NATIVE_NULL});
        ret.add(new Object[]{"object", NullMode.BOXED_NULL});
        ret.add(new Object[]{"():void", NullMode.NULL_FORBIDDEN});
        return ret;
    }

    @Parameter(0) public String type;
    @Parameter(1) public NullMode nullMode;

    public class TestNullRetNode extends SendExecuteNode {

        public TestNullRetNode() {
            super("return_null", String.format("() : %s", type));
        }
    }

    @Test
    public void testNullRet(@Inject(TestNullRetNode.class) CallTarget callTarget) {
        Object ret = callTarget.call();
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isNull", UNCACHED_INTEROP.isNull(obj));
    }

    private String getExpected() {
        switch (nullMode) {
            case NATIVE_NULL:
                return "null";
            case BOXED_NULL:
                return "non-null";
            case NULL_FORBIDDEN:
                Assume.assumeTrue("can't pass NULL to native", false);
                break;
            default:
                Assert.fail();
        }
        return null;
    }

    public class TestNullArgNode extends SendExecuteNode {

        public TestNullArgNode() {
            super("null_arg", String.format("(%s) : string", type));
        }
    }

    @Test
    public void testNullArg(@Inject(TestNullArgNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        String expected = getExpected();

        Object ret = callTarget.call(new NullObject());
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", expected, UNCACHED_INTEROP.asString(obj));
    }

    public class TestNullCallbackArgNode extends SendExecuteNode {

        public TestNullCallbackArgNode() {
            super("callback_null_arg", String.format("((%s):void) : void", type));
        }
    }

    private static final TruffleObject NULL_ARG_CALLBACK = new TestCallback(1, (args) -> {
        Assert.assertThat("callback argument", args[0], is(instanceOf(TruffleObject.class)));
        Assert.assertTrue("isNull", UNCACHED_INTEROP.isNull(args[0]));
        return null;
    });

    @Test
    public void testNullCallbackArg(@Inject(TestNullCallbackArgNode.class) CallTarget callTarget) {
        callTarget.call(NULL_ARG_CALLBACK);
    }

    public class TestNullCallbackRetNode extends SendExecuteNode {

        public TestNullCallbackRetNode() {
            super("callback_null_ret", String.format("(():%s) : string", type));
        }
    }

    private static final TruffleObject NULL_RET_CALLBACK = new TestCallback(0, (args) -> {
        return new NullObject();
    });

    @Test
    public void testNullCallbackRet(@Inject(TestNullCallbackRetNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        String expected = getExpected();

        Object ret = callTarget.call(NULL_RET_CALLBACK);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", expected, UNCACHED_INTEROP.asString(obj));
    }
}
