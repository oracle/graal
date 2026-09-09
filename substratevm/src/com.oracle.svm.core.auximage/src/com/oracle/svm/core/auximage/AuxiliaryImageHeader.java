/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawFieldOffset;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.shared.util.VMError;

@RawStructure
public interface AuxiliaryImageHeader extends PointerBase {
    @RawField
    void setPrimaryImageId(ComparableWord value);

    @RawFieldOffset
    static int offsetOfPrimaryImageId() {
        throw VMError.intentionallyUnimplemented(); // replaced; ExcludeFromJacocoGeneratedReport
    }

    @RawField
    void setAuxImageHeapOffsetInAddressSpace(UnsignedWord offset);

    @RawFieldOffset
    static int offsetOfAuxImageHeapOffsetInAddressSpace() {
        throw VMError.intentionallyUnimplemented(); // replaced; ExcludeFromJacocoGeneratedReport
    }

    /**
     * Offset of the origin object from the start of the (primary) image heap, that is, a heap
     * base-relative reference to the origin object.
     */
    @RawField
    void setImageHeapToOriginObjectOffset(UnsignedWord offset);

    @RawFieldOffset
    static int offsetOfImageHeapToOriginObjectOffset() {
        throw VMError.intentionallyUnimplemented(); // replaced; ExcludeFromJacocoGeneratedReport
    }
}
