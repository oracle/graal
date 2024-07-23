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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.heap.Heap;

/**
 * A provider of ranges of committed memory, which is virtual memory that is backed by physical
 * memory or swap space.
 */
public interface CommittedMemoryProvider {
    @Fold
    static CommittedMemoryProvider get() {
        return ImageSingletons.lookup(CommittedMemoryProvider.class);
    }

    /**
     * Returns whether this provider will always guarantee a heap address space alignment of
     * {@link Heap#getPreferredAddressSpaceAlignment()} at image runtime, which may also depend on
     * {@link ImageHeapProvider#guaranteesHeapPreferredAddressSpaceAlignment()}.
     */
    @Fold
    boolean guaranteesHeapPreferredAddressSpaceAlignment();

    /**
     * Performs initializations <em>for the current isolate</em>, before any other methods of this
     * interface may be called.
     *
     * @return zero in case of success, non-zero in case of an error.
     */
    @Uninterruptible(reason = "Still being initialized.")
    int initialize(WordPointer heapBasePointer, CEntryPointCreateIsolateParameters parameters);

    /**
     * Tear down <em>for the current isolate</em>. This must be the last method of this interface
     * that is called in an isolate.
     *
     * @return zero in case of success, non-zero in case of an error.
     */
    @Uninterruptible(reason = "Tear-down in progress.")
    int tearDown();

    /**
     * Returns the granularity of committed memory management, which is typically the same as that
     * of {@linkplain VirtualMemoryProvider#getGranularity() virtual memory management}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    default UnsignedWord getGranularity() {
        return VirtualMemoryProvider.get().getGranularity();
    }

    Pointer allocateExecutableMemory(UnsignedWord nbytes, UnsignedWord alignment);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void freeExecutableMemory(PointerBase start, UnsignedWord nbytes, UnsignedWord alignment);
}
