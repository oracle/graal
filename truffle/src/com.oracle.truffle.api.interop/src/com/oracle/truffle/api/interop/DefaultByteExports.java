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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(value = InteropLibrary.class, receiverType = Byte.class)
@SuppressWarnings("unused")
final class DefaultByteExports {

    @ExportMessage
    static boolean isNumber(Byte receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInByte(Byte receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInInt(Byte receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInShort(Byte receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInLong(Byte receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInFloat(Byte receiver) {
        return true;
    }

    @ExportMessage
    static boolean fitsInDouble(Byte receiver) {
        return true;
    }

    @ExportMessage
    static byte asByte(Byte receiver) {
        return receiver;
    }

    @ExportMessage
    static short asShort(Byte receiver) {
        return receiver;
    }

    @ExportMessage
    static int asInt(Byte receiver) {
        return receiver;
    }

    @ExportMessage
    static long asLong(Byte receiver) {
        return receiver;
    }

    @ExportMessage
    static float asFloat(Byte receiver) {
        return receiver;
    }

    @ExportMessage
    static double asDouble(Byte receiver) {
        return receiver;
    }

    /*
     * We export these messages explicitly because the legacy default is very costly. Remove with
     * the complicated legacy implementation in InteropLibrary.
     */
    @ExportMessage
    static boolean hasLanguage(Byte receiver) {
        return false;
    }

    @ExportMessage
    static Class<? extends TruffleLanguage<?>> getLanguage(Byte receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean hasSourceLocation(Byte receiver) {
        return false;
    }

    @ExportMessage
    static SourceSection getSourceLocation(Byte receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    static boolean hasMetaObject(Byte receiver) {
        return false;
    }

    @ExportMessage
    static Object getMetaObject(Byte receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @TruffleBoundary
    static Object toDisplayString(Byte receiver, boolean allowSideEffects) {
        return receiver.toString();
    }
}
