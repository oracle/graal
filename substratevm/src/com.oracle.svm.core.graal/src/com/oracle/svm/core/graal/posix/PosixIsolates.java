/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.graal.posix;

import static com.oracle.svm.core.posix.headers.Mman.MAP_ANON;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.graal.posix.PosixCEntryPointSnippets.Errors;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Mman;
import com.oracle.svm.core.posix.headers.Unistd;

public class PosixIsolates {
    public static final String IMAGE_HEAP_BEGIN_SYMBOL_NAME = "__svm_heap_begin";
    public static final String IMAGE_HEAP_END_SYMBOL_NAME = "__svm_heap_end";

    private static final CGlobalData<Word> IMAGE_HEAP_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_BEGIN_SYMBOL_NAME);
    private static final CGlobalData<Word> IMAGE_HEAP_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_END_SYMBOL_NAME);

    @Uninterruptible(reason = "Thread state not yet set up.", callerMustBe = true, mayBeInlined = true)
    public static int checkSanity(Isolate isolate) {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return isolate.isNull() ? Errors.NO_ERROR : Errors.UNINITIALIZED_ISOLATE;
        }
        return isolate.isNull() ? Errors.NULL_ARGUMENT : Errors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public static int create(WordPointer isolatePointer, @SuppressWarnings("unused") CEntryPointCreateIsolateParameters parameters) {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            isolatePointer.write(Word.nullPointer());
            return Errors.NO_ERROR;
        }

        Word begin = IMAGE_HEAP_BEGIN.get();
        Word size = IMAGE_HEAP_END.get().subtract(begin);

        /*
         * Read-protect image heap to catch illegal write accesses. While read accesses from Java
         * code are also illegal and we could guard against them as well, we need read access when
         * we spawn further isolates, which would require some synchronization.
         */
        Mman.NoTransitions.mprotect(begin, size, PROT_READ());

        /*
         * Request an anonymous memory mapping for this isolate's clone of the image heap. The start
         * address of that mapping becomes the isolate's heap base address. We want extra heap
         * chunks that we allocate later to be at a higher address than the heap base so that we can
         * safely consider the base-relative offsets to be unsigned. Therefore, we request the
         * lowest non-zero multiple of the page size as this mapping's address. However, that
         * request is only a hint unless we use MAP_FIXED, which makes things much more complex on
         * our end. Observations:
         *
         * - Without a hint to mmap(), anonymous mappings on Linux 4.4 (openSUSE) on x86_64 are
         * created just before the mapped shared objects, and subsequent mappings are assigned
         * decreasing addresses. However, specifying sysconf(_SC_PAGE_SIZE) as a hint for the first
         * mapping reproducingly places it at a very low address, even before the loaded executable.
         * Therefore, it seems that a number of isolates can be reliably created with their image
         * heap clones at low addresses and with allocated heap chunks at higher addresses.
         *
         * - On Darwin 13.4, anonymous mappings are created after the mapped executable (and some
         * malloc regions in between). Subsequent mappings are assigned increasing addresses that
         * are close to each other. Specifying hints does not have a noticeable effect. Unmapping a
         * mapping makes its address space immediately reusable. Due to the increasing addresses, a
         * single isolate's heap can be safely created. However, because of the address space reuse,
         * a heap chunk of an isolate can be allocated at a lower address than its heap base when
         * another isolate unmaps a chunk at that location.
         */
        long pageSize = Unistd.NoTransitions.sysconf(Unistd._SC_PAGE_SIZE());
        Pointer heap = Mman.NoTransitions.mmap(Word.pointer(pageSize), size, PROT_READ() | PROT_WRITE(), MAP_ANON() | MAP_PRIVATE(), -1, 0);
        if (heap.equal(MAP_FAILED())) {
            return Errors.HEAP_CLONE_FAILED;
        }
        LibC.memcpy(heap, begin, size);
        isolatePointer.write(heap);
        return Errors.NO_ERROR;
    }

    @Uninterruptible(reason = "Thread state not yet set up.", callerMustBe = true, mayBeInlined = true)
    public static PointerBase getHeapBase(Isolate isolate) {
        if (!SubstrateOptions.SpawnIsolates.getValue() || isolate.isNull()) {
            return IMAGE_HEAP_BEGIN.get();
        }
        return isolate;
    }
}
