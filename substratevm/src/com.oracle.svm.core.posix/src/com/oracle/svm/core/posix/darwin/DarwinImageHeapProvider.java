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
package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_WRITABLE_END;
import static com.oracle.svm.core.util.PointerUtils.roundUp;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory;
import com.oracle.svm.core.util.UnsignedUtils;

@AutomaticFeature
@Platforms(InternalPlatform.DARWIN_AND_JNI.class)
class DarwinImageHeapProviderFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageSingletons.contains(ImageHeapProvider.class)) {
            ImageSingletons.add(ImageHeapProvider.class, new DarwinImageHeapProvider());
        }
    }
}

/**
 * An optimal image heap provider for Darwin which creates isolate image heaps that are a
 * copy-on-write clone of the original image heap.
 */
public class DarwinImageHeapProvider implements ImageHeapProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    public int initialize(PointerBase begin, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer) {
        Word imageHeapBegin = Isolates.IMAGE_HEAP_BEGIN.get();
        Word imageHeapSize = Isolates.IMAGE_HEAP_END.get().subtract(imageHeapBegin);

        int task = DarwinVirtualMemory.mach_task_self();

        Pointer heap;
        if (begin.isNonNull()) {
            if (reservedSize.belowThan(imageHeapSize)) {
                return CEntryPointErrors.UNSPECIFIED;
            }
            // Virtual memory must be committed for vm_copy() below
            heap = VirtualMemoryProvider.get().commit(begin, imageHeapSize, Access.READ | Access.WRITE);
        } else {
            WordPointer targetPointer = StackValue.get(WordPointer.class);
            if (DarwinVirtualMemory.vm_allocate(task, targetPointer, imageHeapSize, true) != 0) {
                return CEntryPointErrors.MAP_HEAP_FAILED;
            }
            heap = targetPointer.read();
        }

        // Mach vm_copy performs a copy-on-write virtual memory copy
        if (DarwinVirtualMemory.vm_copy(task, imageHeapBegin, imageHeapSize, heap) != 0) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }

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
        return true; // only done when caller also provided the virtual memory for the image heap
    }

    @Override
    @Uninterruptible(reason = "Called during isolate tear-down.")
    public int tearDown(PointerBase heapBase) {
        // Only called when we allocated ourselves with vm_allocate()
        UnsignedWord size = Isolates.IMAGE_HEAP_END.get().subtract(Isolates.IMAGE_HEAP_BEGIN.get());
        if (DarwinVirtualMemory.vm_deallocate(DarwinVirtualMemory.mach_task_self(), heapBase, size) != 0) {
            return CEntryPointErrors.MAP_HEAP_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
