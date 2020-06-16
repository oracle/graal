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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.lang.reflect.Field;
import org.junit.Assert;
import sun.misc.Unsafe;

@ExportLibrary(InteropLibrary.class)
public class NativeVector implements TruffleObject, AutoCloseable {

    private final double[] vector;
    private long nativeStorage;

    private static final Unsafe unsafe = getUnsafe();

    public NativeVector(double[] vector) {
        this.vector = vector.clone();
    }

    @ExportMessage
    public int getArraySize() {
        return vector.length;
    }

    @SuppressWarnings("restriction")
    private static Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            Assert.fail("can't access Unsafe");
            return null;
        }
    }

    @TruffleBoundary
    @ExportMessage
    public void toNative() {
        if (!isPointer()) {
            nativeStorage = unsafe.allocateMemory(vector.length * Double.BYTES);

            for (int i = 0; i < vector.length; i++) {
                set(i, vector[i]);
            }
        }
    }

    @Override
    public void close() {
        if (isPointer()) {
            unsafe.freeMemory(nativeStorage);
        }
    }

    @ExportMessage
    public boolean isPointer() {
        return nativeStorage != 0;
    }

    @ExportMessage
    public long asPointer() {
        return nativeStorage;
    }

    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementInsertable")
    public boolean isArrayElementAccessable(long idx) {
        return 0 <= idx && idx < getArraySize();
    }

    @ExportMessage
    public double readArrayElement(long idx) {
        if (isPointer()) {
            return unsafe.getDouble(nativeStorage + idx * Double.BYTES);
        } else {
            return vector[(int) idx];
        }
    }

    @ExportMessage(limit = "3")
    public void writeArrayElement(long idx, Object value,
                    @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
        try {
            set((int) idx, interop.asDouble(value));
        } catch (UnsupportedMessageException ex) {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }

    public void set(int idx, double value) {
        if (isPointer()) {
            unsafe.putDouble(nativeStorage + idx * Double.BYTES, value);
        } else {
            vector[idx] = value;
        }
    }

    @ExportMessage
    @ExportMessage(name = "fitsInDouble")
    boolean isNumber() {
        return vector.length == 1;
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        if (isNumber()) {
            return readArrayElement(0);
        } else {
            throw UnsupportedMessageException.create();
        }
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
        return false;
    }

    @ExportMessage
    boolean fitsInLong() {
        return false;
    }

    @ExportMessage
    boolean fitsInFloat() {
        return false;
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
    int asInt() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}
