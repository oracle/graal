/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.word.Word;

/** Platform independent image heap provider that deep-copies the image heap memory. */
public class CopyingImageHeapProvider extends AbstractCopyingImageHeapProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        UnmanagedMemoryUtil.copy(loadedImageHeap, newImageHeap, imageHeapSize);
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * Only called from legacy code, see GR-68584. This method does something fundamentally
     * different than the method with the same name in {@link AbstractImageHeapProvider}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected UnsignedWord getImageHeapAddressSpaceSize() {
        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        int imageHeapOffset = Heap.getHeap().getImageHeapOffsetInAddressSpace();
        assert imageHeapOffset >= 0;
        UnsignedWord size = Word.unsigned(imageHeapOffset);
        size = size.add(getImageHeapSizeInFile());
        size = UnsignedUtils.roundUp(size, pageSize);
        return size;
    }
}
