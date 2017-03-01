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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.nfi.test.interop.NullObject;
import com.oracle.truffle.nfi.test.interop.TestCallback;
import java.util.ArrayList;
import java.util.Collection;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
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

    @Test
    public void testNullRet() {
        TruffleObject function = lookupAndBind("return_null", String.format("() : %s", type));

        Object ret = sendExecute(function);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isNull", JavaInterop.isNull(obj));
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

    @Test
    public void testNullArg() {
        String expected = getExpected();

        TruffleObject function = lookupAndBind("null_arg", String.format("(%s) : string", type));

        Object ret = sendExecute(function, JavaInterop.asTruffleObject(null));
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isBoxed", JavaInterop.isBoxed(obj));
        Assert.assertEquals("return value", expected, JavaInterop.unbox(obj));
    }

    @Test
    public void testNullCallbackArg() {
        TruffleObject function = lookupAndBind("callback_null_arg", String.format("((%s):void) : void", type));
        TruffleObject nullCallback = new TestCallback(1, (args) -> {
            Assert.assertThat("callback argument", args[0], is(instanceOf(TruffleObject.class)));
            Assert.assertTrue("isNull", JavaInterop.isNull((TruffleObject) args[0]));
            return null;
        });

        sendExecute(function, nullCallback);
    }

    @Test
    public void testNullCallbackRet() {
        String expected = getExpected();

        TruffleObject function = lookupAndBind("callback_null_ret", String.format("(():%s) : string", type));
        TruffleObject nullCallback = new TestCallback(0, (args) -> {
            return new NullObject();
        });

        Object ret = sendExecute(function, nullCallback);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isBoxed", JavaInterop.isBoxed(obj));
        Assert.assertEquals("return value", expected, JavaInterop.unbox(obj));
    }
}
