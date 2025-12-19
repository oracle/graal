/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/**
 * Struct that stores all GC-related sizes. See {@link SizeParameters} for more details on its
 * lifecycle. When adding fields to this struct, please also change
 * {@link RawSizeParametersOnStackAccess#initialize} and {@link SizeParameters#matches} accordingly.
 */
@RawStructure
interface RawSizeParameters extends PointerBase {
    @RawField
    UnsignedWord getInitialEdenSize();

    @RawField
    void setInitialEdenSize(UnsignedWord value);

    @RawField
    UnsignedWord getEdenSize();

    @RawField
    void setEdenSize(UnsignedWord value);

    @RawField
    UnsignedWord getMaxEdenSize();

    @RawField
    void setMaxEdenSize(UnsignedWord value);

    @RawField
    UnsignedWord getInitialSurvivorSize();

    @RawField
    void setInitialSurvivorSize(UnsignedWord value);

    @RawField
    UnsignedWord getSurvivorSize();

    @RawField
    void setSurvivorSize(UnsignedWord value);

    @RawField
    UnsignedWord getMaxSurvivorSize();

    @RawField
    void setMaxSurvivorSize(UnsignedWord value);

    @RawField
    UnsignedWord getInitialYoungSize();

    @RawField
    void setInitialYoungSize(UnsignedWord value);

    @RawField
    UnsignedWord getYoungSize();

    @RawField
    void setYoungSize(UnsignedWord value);

    @RawField
    UnsignedWord getMaxYoungSize();

    @RawField
    void setMaxYoungSize(UnsignedWord value);

    @RawField
    UnsignedWord getInitialOldSize();

    @RawField
    void setInitialOldSize(UnsignedWord value);

    @RawField
    UnsignedWord getOldSize();

    @RawField
    void setOldSize(UnsignedWord value);

    @RawField
    UnsignedWord getMaxOldSize();

    @RawField
    void setMaxOldSize(UnsignedWord value);

    @RawField
    UnsignedWord getPromoSize();

    @RawField
    void setPromoSize(UnsignedWord value);

    @RawField
    UnsignedWord getMinHeapSize();

    @RawField
    void setMinHeapSize(UnsignedWord value);

    @RawField
    UnsignedWord getInitialHeapSize();

    @RawField
    void setInitialHeapSize(UnsignedWord value);

    @RawField
    UnsignedWord getHeapSize();

    @RawField
    void setHeapSize(UnsignedWord value);

    @RawField
    UnsignedWord getMaxHeapSize();

    @RawField
    void setMaxHeapSize(UnsignedWord value);

    /**
     * Either points to null or to an obsolete {@link RawSizeParameters} struct that wasn't freed
     * yet.
     */
    @RawField
    RawSizeParameters getNext();

    @RawField
    void setNext(RawSizeParameters value);
}
