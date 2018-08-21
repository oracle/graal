/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
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
        final TruffleObject strdup = lookupAndBind(defaultLibrary, "strdup", "(string):string");
        final TruffleObject free = lookupAndBind(defaultLibrary, "free", "(pointer):void");

        @Child Node executeFunction = Message.EXECUTE.createNode();
        @Child Node executeStrdup = Message.EXECUTE.createNode();
        @Child Node executeFree = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object nativeString = ForeignAccess.sendExecute(executeStrdup, strdup, frame.getArguments()[0]);
            Object ret = ForeignAccess.sendExecute(executeFunction, function, nativeString);
            ForeignAccess.sendExecute(executeFree, free, nativeString);
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
    public void testStringRetConst(@Inject(StringRetConstNode.class) CallTarget callTarget) {
        Object ret = callTarget.call();

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        TruffleObject obj = (TruffleObject) ret;

        Assert.assertTrue("isBoxed", isBoxed(obj));
        Assert.assertEquals("return value", "Hello, World!", unbox(obj));
    }

    public static class StringRetDynamicNode extends NFITestRootNode {

        final TruffleObject function = lookupAndBind("string_ret_dynamic", "(sint32):string");
        final TruffleObject free = lookupAndBind("free_dynamic_string", "(pointer):sint32");

        @Child Node executeFunction = Message.EXECUTE.createNode();
        @Child Node executeFree = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object ret = ForeignAccess.sendExecute(executeFunction, function, frame.getArguments()[0]);

            checkRet(ret);

            /*
             * Normally here we'd just call "free" from libc. We're using a wrapper to be able to
             * reliably test whether it was called with the correct argument.
             */
            Object magic = ForeignAccess.sendExecute(executeFree, free, ret);
            assertEquals(42, magic);

            return null;
        }

        @TruffleBoundary
        private static void checkRet(Object ret) {
            Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
            TruffleObject obj = (TruffleObject) ret;

            Assert.assertTrue("isBoxed", isBoxed(obj));
            Assert.assertEquals("return value", "42", unbox(obj));
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

    private static void testStringCallback(CallTarget target, Object callbackRet) {
        TruffleObject strArgCallback = new TestCallback(1, (args) -> {
            Assert.assertEquals("string argument", "Hello, Truffle!", args[0]);
            return 42;
        });
        TruffleObject strRetCallback = new TestCallback(0, (args) -> {
            return callbackRet;
        });

        Object ret = target.call(strArgCallback, strRetCallback);

        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    @Test
    public void testStringCallback(@Inject(StringCallbackNode.class) CallTarget target) {
        testStringCallback(target, "Hello, Native!");
    }

    @Test
    public void testBoxedStringCallback(@Inject(StringCallbackNode.class) CallTarget target) {
        testStringCallback(target, new BoxedPrimitive("Hello, Native!"));
    }

    public static class NativeStringCallbackNode extends NFITestRootNode {

        final TruffleObject stringRetConst = lookupAndBind("string_ret_const", "():string");
        final TruffleObject nativeStringCallback = lookupAndBind("native_string_callback", "(():string) : string");

        @Child Node executeStringRetConst = Message.EXECUTE.createNode();
        @Child Node executeNativeStringCallback = Message.EXECUTE.createNode();

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            Object string = ForeignAccess.sendExecute(executeStringRetConst, stringRetConst);
            TruffleObject callback = createCallback(string);
            return ForeignAccess.sendExecute(executeNativeStringCallback, nativeStringCallback, callback);
        }

        @TruffleBoundary
        private static TruffleObject createCallback(Object obj) {
            return new TestCallback(0, (args) -> {
                return obj;
            });
        }
    }

    @Test
    public void testNativeStringCallback(@Inject(NativeStringCallbackNode.class) CallTarget target) {
        Object ret = target.call();

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        TruffleObject obj = (TruffleObject) ret;

        Assert.assertTrue("isBoxed", isBoxed(obj));
        Assert.assertEquals("return value", "same", unbox(obj));
    }
}
