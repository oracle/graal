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
package com.oracle.truffle.nfi.test.interop;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public class BoxedPrimitive implements TruffleObject {

    final Object primitive;
    boolean toNativeCalled;

    public BoxedPrimitive(Object primitive) {
        this.primitive = primitive;
        this.toNativeCalled = false;
    }

    @ExportMessage
    void toNative() {
        this.toNativeCalled = true;
    }

    public boolean isNative() {
        return toNativeCalled;
    }

    @ExportMessage
    boolean isBoolean(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.isBoolean(primitive);
    }

    @ExportMessage
    boolean asBoolean(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asBoolean(primitive);
    }

    @ExportMessage
    boolean isString(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.isString(primitive);
    }

    @ExportMessage
    String asString(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asString(primitive);
    }

    @ExportMessage
    boolean isNumber(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.isNumber(primitive);
    }

    @ExportMessage
    boolean fitsInByte(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.fitsInByte(primitive);
    }

    @ExportMessage
    boolean fitsInShort(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.fitsInShort(primitive);
    }

    @ExportMessage
    boolean fitsInInt(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.fitsInInt(primitive);
    }

    @ExportMessage
    boolean fitsInLong(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.fitsInLong(primitive);
    }

    @ExportMessage
    boolean fitsInFloat(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.fitsInFloat(primitive);
    }

    @ExportMessage
    boolean fitsInDouble(@CachedLibrary("this.primitive") InteropLibrary interop) {
        return interop.fitsInDouble(primitive);
    }

    @ExportMessage
    byte asByte(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asByte(primitive);
    }

    @ExportMessage
    short asShort(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asShort(primitive);
    }

    @ExportMessage
    int asInt(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asInt(primitive);
    }

    @ExportMessage
    long asLong(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asLong(primitive);
    }

    @ExportMessage
    float asFloat(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asFloat(primitive);
    }

    @ExportMessage
    double asDouble(@CachedLibrary("this.primitive") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asDouble(primitive);
    }

    @Override
    public String toString() {
        return "boxed<" + primitive + ">";
    }
}
