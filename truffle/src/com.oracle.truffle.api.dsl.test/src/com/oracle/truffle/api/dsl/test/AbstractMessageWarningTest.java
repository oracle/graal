/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class AbstractMessageWarningTest {

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "truffle-abstract-export"})
    static class MillionSix implements TruffleObject {

        @ExportMessage
        boolean isNumber() {
            return true;
        }

        @ExportMessage
        boolean fitsInByte() {
            return false;
        }

        @ExportMessage
        boolean fitsInShort() {
            return false;
        }

        @ExportMessage
        boolean fitsInInt() {
            return true;
        }

        @ExportMessage
        boolean fitsInLong() {
            return true;
        }

        @ExportMessage
        boolean fitsInFloat() {
            return true;
        }

        @ExportMessage
        boolean fitsInDouble() {
            return true;
        }

        @ExportMessage
        byte asByte() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        short asShort() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        int asInt() {
            return 1000006;
        }

        @ExportMessage
        long asLong() {
            return 1000006L;
        }

        @ExportMessage
        float asFloat() {
            return 1000006.0f;
        }

        @ExportMessage
        double asDouble() {
            return 1000006.0d;
        }
    }

    @Test
    public void testWarningSupression() throws UnsupportedMessageException {
        InteropLibrary interop = InteropLibrary.getUncached();
        MillionSix millionSix = new MillionSix();
        Assert.assertTrue(interop.isNumber(millionSix));
        Assert.assertFalse(interop.fitsInByte(millionSix));
        Assert.assertFalse(interop.fitsInShort(millionSix));
        Assert.assertTrue(interop.fitsInInt(millionSix));
        Assert.assertTrue(interop.fitsInLong(millionSix));
        Assert.assertTrue(interop.fitsInFloat(millionSix));
        Assert.assertTrue(interop.fitsInDouble(millionSix));

        AbstractPolyglotTest.assertFails(() -> interop.asByte(millionSix), UnsupportedMessageException.class);
        AbstractPolyglotTest.assertFails(() -> interop.asByte(millionSix), UnsupportedMessageException.class);
        Assert.assertEquals(1000006, interop.asInt(millionSix));
        Assert.assertEquals(1000006, interop.asLong(millionSix));
        Assert.assertEquals(1000006.0f, interop.asFloat(millionSix), 0.01f);
        Assert.assertEquals(1000006.0d, interop.asDouble(millionSix), 0.01d);
        Assert.assertEquals(BigInteger.valueOf(1000006), interop.asBigInteger(millionSix));
    }
}
