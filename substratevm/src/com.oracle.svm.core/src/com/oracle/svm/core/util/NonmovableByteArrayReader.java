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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.vm.ci.meta.JavaKind;

/**
 * Provides low-level read access to a {@link NonmovableArray} array of bytes for signed and
 * unsigned values of size 1, 2, 4, and 8 bytes.
 */
public class NonmovableByteArrayReader {

    public static Pointer pointerTo(NonmovableArray<Byte> data, long byteIndex) {
        if (SubstrateUtil.HOSTED) {
            throw VMError.shouldNotReachHere("Returns a raw pointer and therefore must not be called at image build time.");
        }
        assert byteIndex >= 0 && NumUtil.safeToInt(byteIndex) < NonmovableArrays.lengthOf(data);
        Pointer result = ((Pointer) data).add(getByteArrayBaseOffset()).add(NumUtil.safeToInt(byteIndex));
        assert result.equal(NonmovableArrays.addressOf(data, NumUtil.safeToInt(byteIndex))) : "sanity check that the optimized code above does the right thing";
        return result;
    }

    public static int getS1(NonmovableArray<Byte> data, long byteIndex) {
        if (SubstrateUtil.HOSTED) {
            return ByteArrayReader.getS1(NonmovableArrays.getHostedArray(data), byteIndex);
        }
        return pointerTo(data, byteIndex).readByte(0);
    }

    public static int getS2(NonmovableArray<Byte> data, long byteIndex) {
        if (SubstrateUtil.HOSTED) {
            return ByteArrayReader.getS2(NonmovableArrays.getHostedArray(data), byteIndex);
        }
        return pointerTo(data, byteIndex).readShort(0);
    }

    public static int getS4(NonmovableArray<Byte> data, long byteIndex) {
        if (SubstrateUtil.HOSTED) {
            return ByteArrayReader.getS4(NonmovableArrays.getHostedArray(data), byteIndex);
        }
        return pointerTo(data, byteIndex).readInt(0);
    }

    public static long getS8(NonmovableArray<Byte> data, long byteIndex) {
        if (SubstrateUtil.HOSTED) {
            return ByteArrayReader.getS8(NonmovableArrays.getHostedArray(data), byteIndex);
        }
        return pointerTo(data, byteIndex).readLong(0);
    }

    public static int getU1(NonmovableArray<Byte> data, long byteIndex) {
        return getS1(data, byteIndex) & 0xFF;
    }

    public static int getU2(NonmovableArray<Byte> data, long byteIndex) {
        return getS2(data, byteIndex) & 0xFFFF;
    }

    public static long getU4(NonmovableArray<Byte> data, long byteIndex) {
        return getS4(data, byteIndex) & 0xFFFFFFFFL;
    }

    @Fold
    protected static int getByteArrayBaseOffset() {
        return ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
    }
}
