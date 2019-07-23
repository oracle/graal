/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.util.AbstractTypeReader;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.util.NonmovableByteArrayReader;
import com.oracle.svm.core.util.VMError;

/**
 * Custom TypeReader that allows reusing the same instance over and over again. Only getSV(),
 * getSVInt(), getUV(), getUVInt() are implemented.
 */
public final class ReusableTypeReader extends AbstractTypeReader {

    private NonmovableArray<Byte> data;
    private long byteIndex = -1;

    public ReusableTypeReader() {
    }

    public ReusableTypeReader(NonmovableArray<Byte> data, long byteIndex) {
        this.data = data;
        this.byteIndex = byteIndex;
    }

    public void reset() {
        data = NonmovableArrays.nullArray();
        byteIndex = -1;
    }

    public boolean isValid() {
        return data != null && byteIndex >= 0;
    }

    @Override
    public long getByteIndex() {
        return byteIndex;
    }

    @Override
    public void setByteIndex(long byteIndex) {
        this.byteIndex = byteIndex;
    }

    public NonmovableArray<Byte> getData() {
        return data;
    }

    public void setData(NonmovableArray<Byte> data) {
        this.data = data;
    }

    @Override
    public int getS1() {
        throw VMError.unimplemented();
    }

    @Override
    public int getU1() {
        int result = NonmovableByteArrayReader.getU1(data, byteIndex);
        byteIndex += Byte.BYTES;
        return result;
    }

    @Override
    public int getS2() {
        throw VMError.unimplemented();
    }

    @Override
    public int getU2() {
        throw VMError.unimplemented();
    }

    @Override
    public int getS4() {
        throw VMError.unimplemented();
    }

    @Override
    public long getU4() {
        throw VMError.unimplemented();
    }

    @Override
    public long getS8() {
        throw VMError.unimplemented();
    }
}
