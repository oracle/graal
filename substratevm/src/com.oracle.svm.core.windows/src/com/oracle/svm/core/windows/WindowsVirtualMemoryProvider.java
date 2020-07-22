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
package com.oracle.svm.core.windows;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.SysinfoAPI;

@AutomaticFeature
class WindowsVirtualMemoryProviderFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ImageSingletons.add(VirtualMemoryProvider.class, new WindowsVirtualMemoryProvider());
    }
}

public class WindowsVirtualMemoryProvider implements VirtualMemoryProvider {

    private static final CGlobalData<WordPointer> CACHED_PAGE_SIZE = CGlobalDataFactory.createWord();
    private static final CGlobalData<WordPointer> CACHED_ALLOC_GRAN = CGlobalDataFactory.createWord();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void initCaches() {
        SysinfoAPI.SYSTEM_INFO sysInfo = StackValue.get(SysinfoAPI.SYSTEM_INFO.class);
        SysinfoAPI.GetSystemInfo(sysInfo);
        CACHED_PAGE_SIZE.get().write(WordFactory.unsigned(sysInfo.dwPageSize()));
        CACHED_ALLOC_GRAN.get().write(WordFactory.unsigned(sysInfo.dwAllocationGranularity()));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getPageSize() {
        UnsignedWord value = CACHED_PAGE_SIZE.get().read();
        if (value.equal(WordFactory.zero())) {
            initCaches();
            value = CACHED_PAGE_SIZE.get().read();
        }
        return value;
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getAllocationGranularity() {
        UnsignedWord value = CACHED_ALLOC_GRAN.get().read();
        if (value.equal(WordFactory.zero())) {
            initCaches();
            value = CACHED_ALLOC_GRAN.get().read();
        }
        return value;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getGranularity() {
        return getPageSize();
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getAlignment() {
        return getAllocationGranularity();
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static int accessAsProt(int access) {
        if (access == Access.NONE) {
            return MemoryAPI.PAGE_NOACCESS();
        }

        if ((access & Access.EXECUTE) != 0) {
            if ((access & Access.READ) != 0) {
                if ((access & Access.WRITE) != 0) {
                    return MemoryAPI.PAGE_EXECUTE_READWRITE();
                } else {
                    return MemoryAPI.PAGE_EXECUTE_READ();
                }
            }
            if ((access & Access.WRITE) != 0) {
                return MemoryAPI.PAGE_EXECUTE_READWRITE();
            }
            return MemoryAPI.PAGE_EXECUTE();
        } else {
            if ((access & Access.READ) != 0) {
                if ((access & Access.WRITE) != 0) {
                    return MemoryAPI.PAGE_READWRITE();
                } else {
                    return MemoryAPI.PAGE_READONLY();
                }
            }
            if ((access & Access.WRITE) != 0) {
                return MemoryAPI.PAGE_READWRITE();
            }
            return MemoryAPI.PAGE_NOACCESS();
        }
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer reserve(UnsignedWord nbytes, UnsignedWord alignment) {
        if (UnsignedUtils.isAMultiple(getAllocationGranularity(), alignment)) {
            return reserve(nbytes);
        }
        /* Reserve a container that is large enough for the requested size *and* the alignment. */
        Pointer reservedStart = reserve(nbytes.add(alignment));
        if (reservedStart.isNull()) {
            return WordFactory.nullPointer();
        }
        return PointerUtils.roundUp(reservedStart, alignment);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer reserve(UnsignedWord nbytes) {
        return MemoryAPI.VirtualAlloc(WordFactory.nullPointer(), nbytes, MemoryAPI.MEM_RESERVE(), MemoryAPI.PAGE_NOACCESS());
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer mapFile(PointerBase start, UnsignedWord nbytes, WordBase fileHandle, UnsignedWord offset, int access) {
        return WordFactory.nullPointer();
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer commit(PointerBase start, UnsignedWord nbytes, int access) {
        return MemoryAPI.VirtualAlloc(start, nbytes, MemoryAPI.MEM_COMMIT(), accessAsProt(access));
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int protect(PointerBase start, UnsignedWord nbytes, int access) {
        CIntPointer oldProt = StackValue.get(CIntPointer.class);
        int result = MemoryAPI.VirtualProtect(start, nbytes, accessAsProt(access), oldProt);
        return (result != 0) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int uncommit(PointerBase start, UnsignedWord nbytes) {
        int result = MemoryAPI.VirtualFree(start, nbytes, MemoryAPI.MEM_DECOMMIT());
        return (result != 0) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int free(PointerBase start, UnsignedWord nbytes) {
        /* Retrieve the start of the enclosing container that was originally reserved. */
        MemoryAPI.MEMORY_BASIC_INFORMATION memoryInfo = StackValue.get(MemoryAPI.MEMORY_BASIC_INFORMATION.class);
        if (MemoryAPI.VirtualQuery(start, memoryInfo, SizeOf.unsigned(MemoryAPI.MEMORY_BASIC_INFORMATION.class)).equal(0)) {
            return -1;
        }
        assert start.equal(memoryInfo.BaseAddress()) : "Invalid memory block start";
        int result = MemoryAPI.VirtualFree(memoryInfo.AllocationBase(), WordFactory.zero(), MemoryAPI.MEM_RELEASE());
        return (result != 0) ? 0 : -1;
    }
}
