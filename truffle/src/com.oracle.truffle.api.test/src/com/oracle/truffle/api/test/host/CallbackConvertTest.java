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
package com.oracle.truffle.api.test.host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;

public final class CallbackConvertTest extends ProxyLanguageEnvTest {
    private char ch;

    public void callback(char v) {
        this.ch = v;
    }

    public interface CallWithInt {
        void callback(int v);
    }

    public interface CallWithChar {
        void callback(char v);
    }

    @Test
    public void callWithIntTest() {
        TruffleObject truffle = asTruffleObject(this);
        CallWithInt callback = asJavaObject(CallWithInt.class, truffle);
        callback.callback(32);
        assertEquals(' ', ch);
    }

    @Test(expected = IllegalArgumentException.class)
    public void callWithHugeIntTest() {
        TruffleObject truffle = asTruffleObject(this);
        CallWithInt callback = asJavaObject(CallWithInt.class, truffle);
        callback.callback(Integer.MAX_VALUE / 2);
        fail("Should thrown an exception as the int value is too big for char: " + ch);
    }

    @Test
    public void callWithCharTest() {
        TruffleObject truffle = asTruffleObject(this);
        CallWithChar callback = asJavaObject(CallWithChar.class, truffle);
        callback.callback('A');
        assertEquals('A', ch);
    }

    @Test(expected = IllegalArgumentException.class)
    public void callWithNegativeNumberTest() {
        TruffleObject truffle = asTruffleObject(this);
        CallWithInt callback = asJavaObject(CallWithInt.class, truffle);
        callback.callback(-32);
        assertEquals("The call will not get here", 0, ch);
    }

    @Test
    public void callWithPositiveNumberTest() {
        TruffleObject truffle = asTruffleObject(this);
        CallWithInt callback = asJavaObject(CallWithInt.class, truffle);
        callback.callback(65504);
        assertEquals(65504, ch);
    }

}
