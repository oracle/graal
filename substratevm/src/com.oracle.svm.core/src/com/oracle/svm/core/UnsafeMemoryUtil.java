/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.UnsafeMemorySupport;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;

@AutomaticallyRegisteredImageSingleton(UnsafeMemorySupport.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
public class UnsafeMemoryUtil implements UnsafeMemorySupport {

    /** Implementation of {@code Unsafe.copyMemory}. */
    @Override
    public void unsafeCopyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        if (srcBase != null || destBase != null) {
            JavaMemoryUtil.copyOnHeap(srcBase, WordFactory.unsigned(srcOffset), destBase, WordFactory.unsigned(destOffset), WordFactory.unsigned(bytes));
        } else {
            UnmanagedMemoryUtil.copy(WordFactory.pointer(srcOffset), WordFactory.pointer(destOffset), WordFactory.unsigned(bytes));
        }
    }

    /** Implementation of {@code Unsafe.setMemory}. */
    @Override
    public void unsafeSetMemory(Object destBase, long destOffset, long bytes, byte bvalue) {
        // Can't use UnmanagedMemoryUtil.fill as that method doesn't guarantee atomicity.
        if (destBase != null) {
            JavaMemoryUtil.fillOnHeap(destBase, destOffset, bytes, bvalue);
        } else {
            JavaMemoryUtil.fill(WordFactory.pointer(destOffset), WordFactory.unsigned(bytes), bvalue);
        }
    }

    /** Implementation of {@code Unsafe.copySwapMemory}. */
    @Override
    public void unsafeCopySwapMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes, long elemSize) {
        if (srcBase != null || destBase != null) {
            JavaMemoryUtil.copySwapOnHeap(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);
        } else {
            JavaMemoryUtil.copySwap(WordFactory.unsigned(srcOffset), WordFactory.unsigned(destOffset), WordFactory.unsigned(bytes), WordFactory.unsigned(elemSize));
        }
    }
}
