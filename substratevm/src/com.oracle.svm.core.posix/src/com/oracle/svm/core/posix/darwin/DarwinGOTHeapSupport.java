/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.posix.headers.Mman.MAP_ANON;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.mmap;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.pltgot.GOTHeapSupport;
import com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory;

public class DarwinGOTHeapSupport extends GOTHeapSupport {

    private static final CGlobalData<WordPointer> DARWIN_GOT_START_ADDRESS = CGlobalDataFactory.createWord();

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int initialize(WordPointer gotStartAddress) {
        int flags = MAP_ANON() | MAP_PRIVATE();
        Pointer gotMemory = mmap(Word.nullPointer(), getPageAlignedGotSize(), PROT_READ() | PROT_WRITE(), flags, -1, 0);
        if (gotMemory.isNull() || gotMemory.equal(MAP_FAILED())) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_CREATE_FAILED;
        }

        Pointer gotStartInMemory = gotMemory.add(getGotOffsetFromStartOfMapping());
        LibC.memcpy(gotStartInMemory, IMAGE_GOT_BEGIN.get(), getGotSectionSize());

        gotStartAddress.write(gotMemory);
        DARWIN_GOT_START_ADDRESS.get().write(gotMemory);

        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int mapGot(Pointer start) {
        WordPointer taskSelf = DarwinVirtualMemory.mach_task_self();

        /* Unmap part of the heap address space that is designated for the GOT */
        int ret = DarwinVirtualMemory.vm_deallocate(DarwinVirtualMemory.mach_task_self(), start, getPageAlignedGotSize());
        if (ret != 0) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_MMAP_FAILED;
        }

        WordPointer gotStart = StackValue.get(WordPointer.class);
        gotStart.write(start);

        CIntPointer currentProt = StackValue.get(CIntPointer.class);
        CIntPointer maxProt = StackValue.get(CIntPointer.class);

        int intFalse = 0;

        /*
         * Map reserved address space for GOT to "global" GOT allocation, so that all isolates are
         * backed by the same table.
         */
        ret = DarwinVirtualMemory.vm_remap(taskSelf, gotStart, getPageAlignedGotSize(), Word.nullPointer(), intFalse,
                        taskSelf, DARWIN_GOT_START_ADDRESS.get().read(), intFalse, currentProt, maxProt, DarwinVirtualMemory.VM_INHERIT_SHARE());
        if (ret != 0) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_WRONG_MMAP;
        }

        /*
         * The new mapping "inherits" cur_prot and max_prot from the original mapping, but another
         * isolate could race trying to write the original GOT => the new mapping could inherit
         * cur_prot=RW. Ensure that the new-mapping remains read-only, regardless of races.
         */
        if (currentProt.read() != PROT_READ()) {
            ret = VirtualMemoryProvider.get().protect(start, getPageAlignedGotSize(), Access.READ);
            if (ret != 0) {
                return ret;
            }
        }

        return CEntryPointErrors.NO_ERROR;
    }
}
