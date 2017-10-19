/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
