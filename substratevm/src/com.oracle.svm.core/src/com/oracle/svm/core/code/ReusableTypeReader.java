/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.code;

import com.oracle.svm.core.Uninterruptible;
import jdk.graal.compiler.core.common.util.AbstractTypeReader;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.util.NonmovableByteArrayReader;
import com.oracle.svm.core.util.VMError;

/**
 * Custom uninterruptible TypeReader that allows reusing the same instance over and over again. Only
 * getSV(), getSVInt(), getUV(), getUVInt() needs implementation.
 */
public class ReusableTypeReader extends AbstractTypeReader {
    private NonmovableArray<Byte> data;
    private long byteIndex = -1;

    public ReusableTypeReader() {
    }

    public ReusableTypeReader(NonmovableArray<Byte> data, long byteIndex) {
        this.data = data;
        this.byteIndex = byteIndex;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ReusableTypeReader reset() {
        data = NonmovableArrays.nullArray();
        byteIndex = -1;
        return this;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isValid() {
        return data != null && byteIndex >= 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getByteIndex() {
        return byteIndex;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setByteIndex(long byteIndex) {
        this.byteIndex = byteIndex;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public NonmovableArray<Byte> getData() {
        return data;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setData(NonmovableArray<Byte> data) {
        this.data = data;
    }

    @Override
    public int getS1() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int getS2() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int getU2() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int getS4() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public long getU4() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public long getS8() {
        throw VMError.intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getU1() {
        int result = NonmovableByteArrayReader.getU1(data, byteIndex);
        byteIndex += Byte.BYTES;
        return result;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getUVInt() {
        return asS4(getUV());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getSVInt() {
        return asS4(getSV());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getSV() {
        return decodeSign(read());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getUV() {
        return read();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long decodeSign(long value) {
        return (value >>> 1) ^ -(value & 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long read() {
        int b0 = getU1();
        if (b0 < UnsafeArrayTypeWriter.NUM_LOW_CODES) {
            return b0;
        } else {
            return readPacked(b0);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private long readPacked(int b0) {
        assert b0 >= UnsafeArrayTypeWriter.NUM_LOW_CODES;
        long sum = b0;
        long shift = UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
        for (int i = 2;; i++) {
            long b = getU1();
            sum += b << shift;
            if (b < UnsafeArrayTypeWriter.NUM_LOW_CODES || i == UnsafeArrayTypeWriter.MAX_BYTES) {
                return sum;
            }
            shift += UnsafeArrayTypeWriter.HIGH_WORD_SHIFT;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isS4(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int asS4(long value) {
        assert isS4(value);
        return (int) value;
    }
}
