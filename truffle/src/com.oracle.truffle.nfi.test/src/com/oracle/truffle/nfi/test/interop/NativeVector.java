/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import java.lang.reflect.Field;
import org.junit.Assert;
import sun.misc.Unsafe;

public class NativeVector implements TruffleObject, AutoCloseable {

    private final double[] vector;
    private long nativeStorage;

    private static final Unsafe unsafe = getUnsafe();

    public NativeVector(double[] vector) {
        this.vector = vector.clone();
    }

    public int size() {
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
    public void transitionToNative() {
        assert !isPointer() : "is already transitioned";

        nativeStorage = unsafe.allocateMemory(vector.length * Double.BYTES);

        for (int i = 0; i < vector.length; i++) {
            set(i, vector[i]);
        }
    }

    @Override
    public void close() {
        if (isPointer()) {
            unsafe.freeMemory(nativeStorage);
        }
    }

    public boolean isPointer() {
        return nativeStorage != 0;
    }

    public long asPointer() {
        return nativeStorage;
    }

    public double get(int idx) {
        if (isPointer()) {
            return unsafe.getDouble(nativeStorage + idx * Double.BYTES);
        } else {
            return vector[idx];
        }
    }

    public void set(int idx, double value) {
        if (isPointer()) {
            unsafe.putDouble(nativeStorage + idx * Double.BYTES, value);
        } else {
            vector[idx] = value;
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return NativeVectorMessageResolutionForeign.ACCESS;
    }
}
