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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.nfi.test.interop.BoxedPrimitive;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class StringNFITest extends NFITest {

    public static class StringArgNode extends SendExecuteNode {

        public StringArgNode() {
            super("string_arg", "(string):sint32");
        }
    }

    @Test
    public void testJavaStringArg(@Inject(StringArgNode.class) CallTarget callTarget) {
        Object ret = callTarget.call("42");
        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    @Test
    public void testBoxedStringArg(@Inject(StringArgNode.class) CallTarget callTarget) {
        Object ret = callTarget.call(new BoxedPrimitive("42"));
        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    public static class NativeStringArgNode extends NFITestRootNode {

        final TruffleObject function = lookupAndBind("string_arg", "(string):sint32");
        final TruffleObject strdup = lookupAndBindDefault("strdup", "(string):string");
        final TruffleObject free = lookupAndBindDefault("free", "(pointer):void");

        @Child InteropLibrary functionInterop = getInterop(function);
        @Child InteropLibrary strdupInterop = getInterop(strdup);
        @Child InteropLibrary freeInterop = getInterop(free);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object nativeString = strdupInterop.execute(strdup, frame.getArguments()[0]);
            Object ret = functionInterop.execute(function, nativeString);
            freeInterop.execute(free, nativeString);
            return ret;
        }
    }

    @Test
    public void testNativeStringArg(@Inject(NativeStringArgNode.class) CallTarget callTarget) {
        Object retObj = callTarget.call("8472");
        Assert.assertThat("return value", retObj, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 8472, (int) (Integer) retObj);
    }

    public static class StringRetConstNode extends SendExecuteNode {

        public StringRetConstNode() {
            super("string_ret_const", "():string");
        }
    }

    @Test
    public void testStringRetConst(@Inject(StringRetConstNode.class) CallTarget callTarget) throws UnsupportedMessageException {
        Object ret = callTarget.call();

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        TruffleObject obj = (TruffleObject) ret;

        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", "Hello, World!", UNCACHED_INTEROP.asString(obj));
    }

    public static class StringRetDynamicNode extends NFITestRootNode {

        final TruffleObject function = lookupAndBind("string_ret_dynamic", "(sint32):string");
        final TruffleObject free = lookupAndBind("free_dynamic_string", "(pointer):sint32");

        @Child InteropLibrary functionInterop = getInterop(function);
        @Child InteropLibrary freeInterop = getInterop(free);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object ret = functionInterop.execute(function, frame.getArguments()[0]);

            checkRet(ret);

            /*
             * Normally here we'd just call "free" from libc. We're using a wrapper to be able to
             * reliably test whether it was called with the correct argument.
             */
            Object magic = freeInterop.execute(free, ret);
            assertEquals(42, magic);

            return null;
        }

        @TruffleBoundary
        private static void checkRet(Object ret) throws UnsupportedMessageException {
            Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
            TruffleObject obj = (TruffleObject) ret;

            Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
            Assert.assertEquals("return value", "42", UNCACHED_INTEROP.asString(obj));
        }

    }

    @Test
    public void testStringRetDynamic(@Inject(StringRetDynamicNode.class) CallTarget target) {
        target.call(42);
    }

    public static class StringCallbackNode extends SendExecuteNode {

        public StringCallbackNode() {
            super("string_callback", "( (string):sint32, ():string ) : sint32");
        }
    }

    private String expectedArg;
    private Object callbackRet;

    private final TruffleObject strArgCallback = new TestCallback(1, (args) -> {
        Assert.assertEquals("string argument", expectedArg, args[0]);
        return 42;
    });

    private final TruffleObject strRetCallback = new TestCallback(0, (args) -> {
        return callbackRet;
    });

    private void testStringCallback(CallTarget target, Object cbRet, String expected) {
        expectedArg = expected;
        callbackRet = cbRet;

        Object ret = target.call(strArgCallback, strRetCallback);

        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    @Test
    public void testStringCallback(@Inject(StringCallbackNode.class) CallTarget target) {
        testStringCallback(target, "Hello, Native!", "Hello, Truffle!");
    }

    @Test
    public void testUTF8StringCallback(@Inject(StringCallbackNode.class) CallTarget target) {
        testStringCallback(target, "Hello, UTF-8 \u00e4\u00e9\u00e7!", "UTF-8 seems to work \u20ac\u00a2");
    }

    @Test
    public void testBoxedStringCallback(@Inject(StringCallbackNode.class) CallTarget target) {
        testStringCallback(target, new BoxedPrimitive("Hello, Native!"), "Hello, Truffle!");
    }

    public class NativeStringCallbackNode extends NFITestRootNode {

        final TruffleObject stringRetConst = lookupAndBind("string_ret_const", "():string");
        final TruffleObject nativeStringCallback = lookupAndBind("native_string_callback", "(():string) : string");

        @Child InteropLibrary stringRetConstInterop = getInterop(stringRetConst);
        @Child InteropLibrary nativeStringCallbackInterop = getInterop(nativeStringCallback);

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            callbackRet = stringRetConstInterop.execute(stringRetConst);
            return nativeStringCallbackInterop.execute(nativeStringCallback, strRetCallback);
        }
    }

    @Test
    public void testNativeStringCallback(@Inject(NativeStringCallbackNode.class) CallTarget target) throws UnsupportedMessageException {
        Object ret = target.call();

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        TruffleObject obj = (TruffleObject) ret;

        Assert.assertTrue("isString", UNCACHED_INTEROP.isString(obj));
        Assert.assertEquals("return value", "same", UNCACHED_INTEROP.asString(obj));
    }
}
