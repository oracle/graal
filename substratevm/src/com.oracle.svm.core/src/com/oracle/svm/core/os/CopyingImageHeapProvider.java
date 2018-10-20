/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_END;
import static com.oracle.svm.core.util.PointerUtils.roundUp;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.util.UnsignedUtils;

public class CopyingImageHeapProvider implements ImageHeapProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(PointerBase begin, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer) {
        Word imageHeapBegin = Isolates.IMAGE_HEAP_BEGIN.get();
        Word imageHeapSize = Isolates.IMAGE_HEAP_END.get().subtract(imageHeapBegin);
        if (begin.isNonNull() && reservedSize.belowThan(imageHeapSize)) {
            return CEntryPointErrors.UNSPECIFIED;
        }

        Pointer heap = VirtualMemoryProvider.get().commit(begin, imageHeapSize, Access.READ | Access.WRITE);
        if (heap.isNull()) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

        MemoryUtil.copyConjointMemoryAtomic(imageHeapBegin, heap, imageHeapSize);

        UnsignedWord pageSize = VirtualMemoryProvider.get().getGranularity();
        UnsignedWord writableBeginPageOffset = UnsignedUtils.roundDown(IMAGE_HEAP_WRITABLE_BEGIN.get().subtract(imageHeapBegin), pageSize);
        if (writableBeginPageOffset.aboveThan(0)) {
            if (VirtualMemoryProvider.get().protect(heap, writableBeginPageOffset, Access.READ) != 0) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }
        UnsignedWord writableEndPageOffset = UnsignedUtils.roundUp(IMAGE_HEAP_WRITABLE_END.get().subtract(imageHeapBegin), pageSize);
        if (writableEndPageOffset.belowThan(imageHeapSize)) {
            Pointer afterWritableBoundary = heap.add(writableEndPageOffset);
            Word afterWritableSize = imageHeapSize.subtract(writableEndPageOffset);
            if (VirtualMemoryProvider.get().protect(afterWritableBoundary, afterWritableSize, Access.READ) != 0) {
                return CEntryPointErrors.PROTECT_HEAP_FAILED;
            }
        }

        basePointer.write(heap);
        if (endPointer.isNonNull()) {
            endPointer.write(roundUp(heap.add(imageHeapSize), pageSize));
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean canUnmapInsteadOfTearDown(PointerBase heapBase) {
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int tearDown(PointerBase heapBase) {
        Word size = Isolates.IMAGE_HEAP_END.get().subtract(Isolates.IMAGE_HEAP_BEGIN.get());
        if (VirtualMemoryProvider.get().free(heapBase, size) != 0) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
