/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
