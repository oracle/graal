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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Test;

public class StringNFITest extends NFITest {

    @Test
    public void testJavaStringArg() {
        TruffleObject function = lookupAndBind("string_arg", "(string):sint32");
        Object ret = sendExecute(function, "42");
        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    @Test
    public void testNativeStringArg() {
        Object retObj = run(new TestRootNode() {

            final TruffleObject function = lookupAndBind("string_arg", "(string):sint32");
            final TruffleObject strdup = lookupAndBind(defaultLibrary, "strdup", "(string):string");
            final TruffleObject free = lookupAndBind(defaultLibrary, "free", "(pointer):void");

            @Child Node executeFunction = Message.createExecute(1).createNode();
            @Child Node executeStrdup = Message.createExecute(1).createNode();
            @Child Node executeFree = Message.createExecute(1).createNode();

            @Override
            public Object executeTest(VirtualFrame frame) throws InteropException {
                Object nativeString = ForeignAccess.sendExecute(executeStrdup, strdup, frame.getArguments()[0]);
                Object ret = ForeignAccess.sendExecute(executeFunction, function, nativeString);
                ForeignAccess.sendExecute(executeFree, free, nativeString);
                return ret;
            }
        }, "8472");

        Assert.assertThat("return value", retObj, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 8472, (int) (Integer) retObj);
    }

    @Test
    public void testStringRetConst() {
        TruffleObject function = lookupAndBind("string_ret_const", "():string");
        Object ret = sendExecute(function);

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        TruffleObject obj = (TruffleObject) ret;

        Assert.assertTrue("isBoxed", JavaInterop.isBoxed(obj));
        Assert.assertEquals("return value", "Hello, World!", JavaInterop.unbox(obj));
    }

    @Test
    public void testStringRetDynamic() {
        run(new TestRootNode() {

            final TruffleObject function = lookupAndBind("string_ret_dynamic", "(sint32):string");
            final TruffleObject free = lookupAndBind("free_dynamic_string", "(pointer):sint32");

            @Child Node executeFunction = Message.createExecute(1).createNode();
            @Child Node executeFree = Message.createExecute(1).createNode();

            @Override
            public Object executeTest(VirtualFrame frame) throws InteropException {
                Object ret = ForeignAccess.sendExecute(executeFunction, function, frame.getArguments()[0]);

                checkRet(ret);

                /*
                 * Normally here we'd just call "free" from libc. We're using a wrapper to be able
                 * to reliably test whether it was called with the correct argument.
                 */
                Object magic = ForeignAccess.sendExecute(executeFree, free, ret);
                assertEquals(42, magic);

                return null;
            }

            @TruffleBoundary
            private void checkRet(Object ret) {
                Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
                TruffleObject obj = (TruffleObject) ret;

                Assert.assertTrue("isBoxed", JavaInterop.isBoxed(obj));
                Assert.assertEquals("return value", "42", JavaInterop.unbox(obj));
            }
        }, 42);
    }

    @Test
    public void testStringCallback() {
        TruffleObject strArgCallback = JavaInterop.asTruffleFunction(ToIntFunction.class, (str) -> {
            Assert.assertEquals("string argument", "Hello, Truffle!", str);
            return 42;
        });
        TruffleObject strRetCallback = JavaInterop.asTruffleFunction(Supplier.class, () -> {
            return "Hello, Native!";
        });

        TruffleObject testFunction = lookupAndBind("string_callback", "( (string):sint32, ():string ) : sint32");
        Object ret = sendExecute(testFunction, strArgCallback, strRetCallback);

        Assert.assertThat("return value", ret, is(instanceOf(Integer.class)));
        Assert.assertEquals("return value", 42, (int) (Integer) ret);
    }

    @Test
    public void testNullString() {
        TruffleObject nullCallback = JavaInterop.asTruffleFunction(Function.class, (str) -> {
            Assert.assertNull("callback argument", str);
            return null;
        });

        TruffleObject testFunction = lookupAndBind("null_string_test", "((string):string, string) : string");
        Object ret = sendExecute(testFunction, nullCallback, JavaInterop.asTruffleObject(null));

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));
        Assert.assertTrue("return value is null", JavaInterop.isNull((TruffleObject) ret));
    }
}
