/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import org.graalvm.compiler.core.common.util.AbstractTypeReader;

import com.oracle.svm.core.c.NonmovableArray;

public class NonmovableByteArrayTypeReader extends AbstractTypeReader {
    protected final NonmovableArray<Byte> array;
    protected long byteIndex;

    public NonmovableByteArrayTypeReader(NonmovableArray<Byte> array, long byteIndex) {
        this.array = array;
        this.byteIndex = byteIndex;
    }

    @Override
    public long getByteIndex() {
        return byteIndex;
    }

    @Override
    public void setByteIndex(long byteIndex) {
        this.byteIndex = byteIndex;
    }

    @Override
    public int getS1() {
        int result = NonmovableByteArrayReader.getS1(array, byteIndex);
        byteIndex += Byte.BYTES;
        return result;
    }

    @Override
    public int getU1() {
        int result = NonmovableByteArrayReader.getU1(array, byteIndex);
        byteIndex += Byte.BYTES;
        return result;
    }

    @Override
    public int getS2() {
        int result = NonmovableByteArrayReader.getS2(array, byteIndex);
        byteIndex += Short.BYTES;
        return result;
    }

    @Override
    public int getU2() {
        int result = NonmovableByteArrayReader.getU2(array, byteIndex);
        byteIndex += Short.BYTES;
        return result;
    }

    @Override
    public int getS4() {
        int result = NonmovableByteArrayReader.getS4(array, byteIndex);
        byteIndex += Integer.BYTES;
        return result;
    }

    @Override
    public long getU4() {
        long result = NonmovableByteArrayReader.getU4(array, byteIndex);
        byteIndex += Integer.BYTES;
        return result;
    }

    @Override
    public long getS8() {
        long result = NonmovableByteArrayReader.getS8(array, byteIndex);
        byteIndex += Long.BYTES;
        return result;
    }
}
