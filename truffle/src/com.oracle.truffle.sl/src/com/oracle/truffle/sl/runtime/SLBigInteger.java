/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.sl.SLLanguage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class SLBigInteger implements TruffleObject, Comparable<SLBigInteger> {

    private final BigInteger value;

    public SLBigInteger(BigInteger value) {
        this.value = value;
    }

    public SLBigInteger(long value) {
        this.value = BigInteger.valueOf(value);
    }

    public BigInteger getValue() {
        return value;
    }

    @TruffleBoundary
    public int compareTo(SLBigInteger o) {
        return value.compareTo(o.getValue());
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return value.toString();
    }

    @Override
    @TruffleBoundary
    public boolean equals(Object obj) {
        if (obj instanceof SLBigInteger) {
            return value.equals(((SLBigInteger) obj).getValue());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isNumber() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInByte() {
        return value.bitLength() < 8;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInShort() {
        return value.bitLength() < 16;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInFloat() {
        if (value.bitLength() <= 24) { // 24 = size of float mantissa + 1
            return true;
        } else {
            float floatValue = value.floatValue();
            if (!Float.isFinite(floatValue)) {
                return false;
            }
            try {
                return new BigDecimal(floatValue).toBigIntegerExact().equals(value);
            } catch (ArithmeticException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInLong() {
        return value.bitLength() < 64;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInInt() {
        return value.bitLength() < 32;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInDouble() {
        if (value.bitLength() <= 53) { // 53 = size of double mantissa + 1
            return true;
        } else {
            double doubleValue = value.doubleValue();
            if (!Double.isFinite(doubleValue)) {
                return false;
            }
            try {
                return new BigDecimal(doubleValue).toBigIntegerExact().equals(value);
            } catch (ArithmeticException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @ExportMessage
    public boolean fitsInBigInteger() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    double asDouble() throws UnsupportedMessageException {
        if (fitsInDouble()) {
            return value.doubleValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    long asLong() throws UnsupportedMessageException {
        if (fitsInLong()) {
            return value.longValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    byte asByte() throws UnsupportedMessageException {
        if (fitsInByte()) {
            return value.byteValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    int asInt() throws UnsupportedMessageException {
        if (fitsInInt()) {
            return value.intValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    float asFloat() throws UnsupportedMessageException {
        if (fitsInFloat()) {
            return value.floatValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    @TruffleBoundary
    short asShort() throws UnsupportedMessageException {
        if (fitsInShort()) {
            return value.shortValue();
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    BigInteger asBigInteger() {
        return value;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return SLLanguage.class;
    }

    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() {
        return SLType.NUMBER;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return value.toString();
    }

}
