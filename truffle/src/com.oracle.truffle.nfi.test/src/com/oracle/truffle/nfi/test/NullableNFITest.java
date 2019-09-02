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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class NullableNFITest extends NFITest {

    public class TestNullableArgNode extends SendExecuteNode {

        public TestNullableArgNode() {
            super("null_arg", String.format("(%s) : string", NativeSimpleType.NULLABLE));
        }
    }

    @Test
    public void testNullableArgNull(@Inject(TestNullableArgNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call(new NullObject());
        checkResult(ret, "null");
    }

    @Test
    public void testNullableArgString(@Inject(TestNullableArgNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call("a string");
        checkResult(ret, "non-null");
    }

    @Test
    public void testNullableArgClosure(@Inject(TestNullableArgNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call(NULL_RET_CALLBACK);
        checkResult(ret, "non-null");
    }

    public class TestNullableCallbackRetNode extends SendExecuteNode {

        public TestNullableCallbackRetNode() {
            super("callback_null_ret", String.format("(():%s) : string", NativeSimpleType.NULLABLE));
        }
    }

    private static final TruffleObject NULL_RET_CALLBACK = new TestCallback(0, (args) -> {
        return new NullObject();
    });

    @Test
    public void testNullableCallbackRetNull(@Inject(TestNullableCallbackRetNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call(NULL_RET_CALLBACK);
        checkResult(ret, "null");
    }

    private static final TruffleObject STRING_RET_CALLBACK = new TestCallback(0, (args) -> {
        return "a string";
    });

    @Test
    public void testNullableCallbackRetString(@Inject(TestNullableCallbackRetNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call(STRING_RET_CALLBACK);
        checkResult(ret, "non-null");
    }

    private static final TruffleObject CLOSURE_RET_CALLBACK = new TestCallback(0, (args) -> {
        return new TestCallback(0, (ignored) -> 0);
    });

    @Test
    public void testNullableCallbackRetClosure(@Inject(TestNullableCallbackRetNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call(CLOSURE_RET_CALLBACK);
        checkResult(ret, "non-null");
    }

    private static void checkResult(Object ret, String expected) throws UnsupportedMessageException {
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", expected, UNCACHED_INTEROP.asString(obj));
    }
}
