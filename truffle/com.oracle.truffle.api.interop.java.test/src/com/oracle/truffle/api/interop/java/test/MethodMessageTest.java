/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;

public class MethodMessageTest {
    interface MathWrap {
        @MethodMessage(message = "READ")
        MaxFunction max();
    }

    interface MaxFunction {
        @MethodMessage(message = "IS_NULL")
        boolean isNull();

        @MethodMessage(message = "HAS_SIZE")
        boolean isArray();

        @MethodMessage(message = "GET_SIZE")
        int size();

        @MethodMessage(message = "IS_EXECUTABLE")
        boolean canExecute();

        @MethodMessage(message = "EXECUTE")
        Number compute(int a, int b);
    }

    @Test
    public void functionTest() throws Exception {
        TruffleObject truffleMath = JavaInterop.asTruffleObject(Math.class);
        MathWrap wrap = JavaInterop.asJavaObject(MathWrap.class, truffleMath);
        MaxFunction functionArityTwo = wrap.max();
        assertTrue("function can be executed", functionArityTwo.canExecute());
        assertFalse("function isn't null", functionArityTwo.isNull());
        assertFalse("function isn't array", functionArityTwo.isArray());
        int res = functionArityTwo.compute(10, 5).intValue();
        assertEquals(10, res);
    }

    @Test
    public void workWithAnArray() throws Exception {
        TruffleObject arr = JavaInterop.asTruffleObject(new Object[]{1, 2, 3});

        Boolean itIsAnArray = (Boolean) JavaInteropTest.message(Message.HAS_SIZE, arr);
        assertTrue("Yes, array", itIsAnArray);

        MaxFunction wrap = JavaInterop.asJavaObject(MaxFunction.class, arr);

        assertTrue("It is an array", wrap.isArray());
        assertFalse("No function", wrap.canExecute());
        assertFalse("Not null", wrap.isNull());

        assertEquals("Size is 3", 3, wrap.size());
    }
}
