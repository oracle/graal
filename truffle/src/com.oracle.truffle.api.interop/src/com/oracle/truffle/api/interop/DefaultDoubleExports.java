/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(value = InteropLibrary.class, receiverType = Double.class)
@SuppressWarnings("unused")
final class DefaultDoubleExports {

    @ExportMessage
    static boolean fitsInByte(Double receiver) {
        double d = receiver;
        byte b = (byte) d;
        return b == d && !NumberUtils.isNegativeZero(d);
    }

    @ExportMessage
    static boolean fitsInInt(Double receiver) {
        double d = receiver;
        int i = (int) d;
        return i == d && !NumberUtils.isNegativeZero(d);
    }

    @ExportMessage
    static boolean fitsInShort(Double receiver) {
        double d = receiver;
        short s = (short) d;
        return s == d && !NumberUtils.isNegativeZero(d);
    }

    @ExportMessage
    static boolean fitsInLong(Double receiver) {
        double d = receiver;
        long l = (long) d;
        return NumberUtils.inSafeIntegerRange(d) && !NumberUtils.isNegativeZero(d) && l == d;
    }

    @ExportMessage
    static boolean fitsInFloat(Double receiver) {
        double d = receiver;
        float f = (float) d;
        return !Double.isFinite(d) || f == d;
    }

    @ExportMessage
    static byte asByte(Double receiver) throws UnsupportedMessageException {
        double d = receiver;
        byte b = (byte) d;
        if (b == d && !NumberUtils.isNegativeZero(d)) {
            return b;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static short asShort(Double receiver) throws UnsupportedMessageException {
        double d = receiver;
        short s = (short) d;
        if (s == d && !NumberUtils.isNegativeZero(d)) {
            return s;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static int asInt(Double receiver) throws UnsupportedMessageException {
        double d = receiver;
        int i = (int) d;
        if (i == d && !NumberUtils.isNegativeZero(d)) {
            return i;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static long asLong(Double receiver) throws UnsupportedMessageException {
        double d = receiver;
        if (NumberUtils.inSafeIntegerRange(d) && !NumberUtils.isNegativeZero(d)) {
            long l = (long) d;
            if (l == d) {
                return l;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static float asFloat(Double receiver) throws UnsupportedMessageException {
        double d = receiver;
        float f = (float) d;
        if (!Double.isFinite(d) || f == d) {
            return f;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean isNumber(Double receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInDouble(Double receiver) {
        return true;
    }

    @ExportMessage
    static double asDouble(Double receiver) {
        return receiver;
    }

    /*
     * We export these messages explicitly because the legacy default is very costly. Remove with
     * the complicated legacy implementation in InteropLibrary.
     */
    @ExportMessage
    static boolean hasLanguage(Double receiver) {
        return false;
    }

    @ExportMessage
    static Class<? extends TruffleLanguage<?>> getLanguage(Double receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean hasSourceLocation(Double receiver) {
        return false;
    }

    @ExportMessage
    static SourceSection getSourceLocation(Double receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean hasMetaObject(Double receiver) {
        return false;
    }

    @ExportMessage
    static Object getMetaObject(Double receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    static Object toDisplayString(Double receiver, boolean allowSideEffects) {
        return receiver.toString();
    }

}
