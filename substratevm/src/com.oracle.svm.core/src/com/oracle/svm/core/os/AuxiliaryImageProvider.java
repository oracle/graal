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
package com.oracle.svm.core.os;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArguments;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.shared.Uninterruptible;

/** Provides the low-level implementation for auxiliary images. */
public interface AuxiliaryImageProvider {

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static AuxiliaryImageProvider get() {
        return ImageSingletons.lookup(AuxiliaryImageProvider.class);
    }

    @Uninterruptible(reason = "Called during isolate initialization.")
    int initializeHeapAddressRange(IsolateArguments arguments, Pointer reservedBegin, UnsignedWord reservedSize, Pointer imageHeapEnd, WordPointer collectedHeapBeginOut);

    /**
     * Loads an auxiliary image into a contiguous reserved memory range.
     *
     * @param reservedAddressSpace Specifies the start address (never {@code null}) of a contiguous
     *            memory range into which the auxiliary image can mapped.
     * @param reservedSize The size in bytes of the reserved memory at {@code reservedAddressSpace}.
     * @param basePointer An address where a pointer to the actual start of the loaded auxiliary
     *            image in the reserved range will be written. Must not be {@code null}.
     * @param endPointer An address where a pointer to the actual end of the loaded auxiliary image
     *            in the reserved range will be written. Must not be {@code null}.
     * @return a result code from {@link CEntryPointErrors}.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    int loadAuxiliaryImage(Pointer reservedAddressSpace, UnsignedWord reservedSize, CCharPointer filePath, WordPointer basePointer, WordPointer endPointer);
}
