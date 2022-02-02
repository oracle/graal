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

import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
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
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.windows.WindowsUtils.CFunctionPointerPointer;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.SysinfoAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;

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
        if ((access & Access.EXECUTE) != 0) {
            if ((access & Access.WRITE) != 0) {
                return MemoryAPI.PAGE_EXECUTE_READWRITE();
            } else if ((access & Access.READ) != 0) {
                return MemoryAPI.PAGE_EXECUTE_READ();
            }
            return MemoryAPI.PAGE_EXECUTE();
        }

        if ((access & Access.WRITE) != 0) {
            return MemoryAPI.PAGE_READWRITE();
        } else if ((access & Access.READ) != 0) {
            return MemoryAPI.PAGE_READONLY();
        }
        return MemoryAPI.PAGE_NOACCESS();
    }

    /** Sentinel value indicating that no special alignment is required. */
    private static final UnsignedWord UNALIGNED = WordFactory.zero();

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer reserve(UnsignedWord nbytes, UnsignedWord alignment) {
        if (nbytes.equal(0)) {
            return WordFactory.nullPointer();
        }

        UnsignedWord requiredAlignment = alignment;
        if (UnsignedUtils.isAMultiple(getAllocationGranularity(), requiredAlignment)) {
            requiredAlignment = UNALIGNED;
        }

        /*
         * For memory mapping to an already reserved address range to work, the address range must
         * be reserved as a placeholder. So we first try to reserve a placeholder and ...
         */
        Pointer reservedPlaceholder = reservePlaceholder(nbytes, requiredAlignment);
        if (reservedPlaceholder.isNonNull()) {
            /*
             * ... replace it with a normal allocation as the rest of the system is unaware of
             * placeholders. This effectively makes the use of placeholders transparent.
             */
            replacePlaceholder(reservedPlaceholder, nbytes);
            return reservedPlaceholder;
        }

        /*
         * If that fails, it's most likely because the OS doesn't support placeholders, so we
         * continue without support for memory mapping to a reserved address range.
         */
        assert reservedPlaceholder.isNull();

        /* Reserve a container that is large enough for the requested size *and* the alignment. */
        Pointer reserved = MemoryAPI.VirtualAlloc(WordFactory.nullPointer(), nbytes.add(requiredAlignment), MemoryAPI.MEM_RESERVE(), MemoryAPI.PAGE_NOACCESS());
        if (reserved.isNull()) {
            return WordFactory.nullPointer();
        }
        return requiredAlignment.equal(UNALIGNED) ? reserved : PointerUtils.roundUp(reserved, requiredAlignment);
    }

    private static final int MEM_RESERVE_PLACEHOLDER = 0x00040000;

    /** Reserves a placeholder, which is a type of reserved memory region. */
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static Pointer reservePlaceholder(UnsignedWord size, UnsignedWord alignment) {
        int allocationType = MemoryAPI.MEM_RESERVE() | MEM_RESERVE_PLACEHOLDER;
        return invokeVirtualAlloc2(WordFactory.nullPointer(), size, allocationType, MemoryAPI.PAGE_NOACCESS(), alignment);
    }

    private static final int MEM_REPLACE_PLACEHOLDER = 0x00004000;
    private static final CGlobalData<CCharPointer> REPLACE_PLACEHOLDER_ERROR_MESSAGE = CGlobalDataFactory.createCString("Failed to replace a placeholder.");

    /** Replaces a placeholder with a normal private allocation. */
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static void replacePlaceholder(PointerBase placeholder, UnsignedWord size) {
        int allocationType = MemoryAPI.MEM_RESERVE() | MEM_REPLACE_PLACEHOLDER;
        if (invokeVirtualAlloc2(placeholder, size, allocationType, MemoryAPI.PAGE_NOACCESS(), WordFactory.zero()).isNull()) {
            CEntryPointActions.failFatally(WinBase.GetLastError(), REPLACE_PLACEHOLDER_ERROR_MESSAGE.get());
        }
    }

    private static final CGlobalData<CCharPointer> KERNELBASE_DLL = CGlobalDataFactory.createCString("kernelbase.dll");

    private static final CGlobalData<CCharPointer> VIRTUAL_ALLOC_2 = CGlobalDataFactory.createCString("VirtualAlloc2");
    private static final CGlobalData<CFunctionPointerPointer<VirtualAlloc2>> VIRTUAL_ALLOC_2_POINTER = CGlobalDataFactory.createWord(WindowsUtils.UNINITIALIZED_POINTER);

    /**
     * Like VirtualAlloc, but with additional support for placeholders and alignment specification.
     * We only use VirtualAlloc2 in places where the additional features are necessary (it is fine
     * to mix VirtualAlloc and VirtualAlloc2 calls).
     *
     * If the OS does not provide VirtualAlloc2, the null pointer is returned.
     */
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static Pointer invokeVirtualAlloc2(PointerBase baseAddress, UnsignedWord size, int allocationType, int pageProtection, UnsignedWord alignment) {
        VirtualAlloc2 virtualAlloc2 = WindowsUtils.getAndCacheFunctionPointer(VIRTUAL_ALLOC_2_POINTER.get(), KERNELBASE_DLL.get(), VIRTUAL_ALLOC_2.get());
        if (virtualAlloc2.isNull()) {
            /* The OS does not provide VirtualAlloc2 (nor placeholders). */
            return WordFactory.nullPointer();
        }
        MEM_EXTENDED_PARAMETER extendedParameter = StackValue.get(MEM_EXTENDED_PARAMETER.class);
        specifyAlignment(extendedParameter, StackValue.get(MEM_ADDRESS_REQUIREMENTS.class), alignment);
        return virtualAlloc2.invoke(WordFactory.nullPointer(), baseAddress, size, allocationType, pageProtection, extendedParameter, 1);
    }

    private interface VirtualAlloc2 extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        Pointer invoke(HANDLE process, PointerBase baseAddress, UnsignedWord size, int allocationType, int pageProtection,
                        PointerBase extendedParameters, int parameterCount);
    }

    /** Specifies the alignment for the new memory allocation. */
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static void specifyAlignment(MEM_EXTENDED_PARAMETER extendedParameter, MEM_ADDRESS_REQUIREMENTS addressRequirements, UnsignedWord alignment) {
        /*
         * Alignment is specified using two structures: MEM_EXTENDED_PARAMETER and
         * MEM_ADDRESS_REQUIREMENTS. Normally, we would import them using @CStruct, but not all
         * versions of MSVC have them, so we define them below using @RawStructure instead.
         *
         * Note that it is generally not safe to pass @RawStructure arguments to native code due to
         * a lack of control over their memory layout. However, in this particular case this is not
         * a problem, as both structures contain only fields of the same size. Therefore, to achieve
         * the memory layout that the native code expects, it is sufficient to ensure the correct
         * order of the fields, which is easily done by prefixing the field names.
         */
        extendedParameter.f1Type(MemExtendedParameterAddressRequirements);
        extendedParameter.f2Pointer(addressRequirements.rawValue());
        addressRequirements.f1LowestStartingAddress(WordFactory.nullPointer());
        addressRequirements.f2HighestEndingAddress(WordFactory.nullPointer());
        addressRequirements.f3Alignment(alignment);
    }

    /** Represents an extended parameter for a function that manages virtual memory. */
    @RawStructure
    private interface MEM_EXTENDED_PARAMETER extends PointerBase {
        /* This structure must exactly match the memory layout expected by the native code. */
        @RawField
        void f1Type(long value);

        @RawField
        void f2Pointer(long value);
    }

    /** MEM_EXTENDED_PARAMETER_TYPE enumeration Constants. */
    private static final int MemExtendedParameterAddressRequirements = 1;

    /**
     * Specifies a lowest and highest base address and alignment as part of an extended parameter to
     * a function that manages virtual memory.
     */
    @RawStructure
    private interface MEM_ADDRESS_REQUIREMENTS extends PointerBase {
        /* This structure must exactly match the memory layout expected by the native code. */
        @RawField
        void f1LowestStartingAddress(PointerBase value);

        @RawField
        void f2HighestEndingAddress(PointerBase value);

        @RawField
        void f3Alignment(UnsignedWord value);
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer mapFile(PointerBase start, UnsignedWord nbytes, WordBase fileHandle, UnsignedWord offset, int access) {
        if ((start.isNonNull() && !isAligned(start)) || nbytes.equal(0)) {
            return WordFactory.nullPointer();
        }

        /*
         * On Windows, we map views of file mappings into the address range, so we assume that the
         * `fileHandle` is actually a handle returned by the `MemoryAPI.CreateFileMappingW`.
         */
        if (start.isNull()) {
            /*
             * Memory mapping to an unreserved address range imposes an additional restriction on
             * the alignment of the `offset`, which we do not currently support.
             */
            return WordFactory.nullPointer();
        }

        /* First split off a placeholder from the reserved address range ... */
        if (!splitPlaceholder(start, nbytes)) {
            /* The OS does not support placeholders. */
            return WordFactory.nullPointer();
        }

        /* ... and then map a view into the placeholder. */
        int pageProtection = (access & Access.WRITE) != 0 ? MemoryAPI.PAGE_WRITECOPY() : MemoryAPI.PAGE_READONLY();
        Pointer fileView = invokeMapViewOfFile3((HANDLE) fileHandle, start, offset.rawValue(), nbytes, pageProtection);
        if (fileView.isNull()) {
            /* Restore a normal allocation as the caller is unaware of placeholders. */
            replacePlaceholder(start, nbytes);
        }
        return fileView;
    }

    private static final int MEM_PRESERVE_PLACEHOLDER = 0x00000002;

    /** Splits off a placeholder from the existing one. */
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static boolean splitPlaceholder(PointerBase start, UnsignedWord size) {
        return MemoryAPI.VirtualFree(start, size, MemoryAPI.MEM_RELEASE() | MEM_PRESERVE_PLACEHOLDER) != 0;
    }

    private static final CGlobalData<CCharPointer> MAP_VIEW_OF_FILE_3 = CGlobalDataFactory.createCString("MapViewOfFile3");
    private static final CGlobalData<CFunctionPointerPointer<MapViewOfFile3>> MAP_VIEW_OF_FILE_3_POINTER = CGlobalDataFactory.createWord(WindowsUtils.UNINITIALIZED_POINTER);

    /**
     * Like MapViewOfFile, but with additional support for placeholders and alignment specification.
     *
     * If the OS does not provide MapViewOfFile3, the null pointer is returned.
     */
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static Pointer invokeMapViewOfFile3(HANDLE fileMapping, PointerBase baseAddress, long offset, UnsignedWord viewSize, int pageProtection) {
        MapViewOfFile3 mapViewOfFile3 = WindowsUtils.getAndCacheFunctionPointer(MAP_VIEW_OF_FILE_3_POINTER.get(), KERNELBASE_DLL.get(), MAP_VIEW_OF_FILE_3.get());
        if (mapViewOfFile3.isNull()) {
            /* The OS does not provide MapViewOfFile3 (nor placeholders). */
            return WordFactory.nullPointer();
        }
        return mapViewOfFile3.invoke(fileMapping, WordFactory.nullPointer(), baseAddress, offset, viewSize, MEM_REPLACE_PLACEHOLDER,
                        pageProtection, WordFactory.nullPointer(), 0);
    }

    private interface MapViewOfFile3 extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        Pointer invoke(HANDLE fileMapping, HANDLE process, PointerBase baseAddress, long offset, UnsignedWord viewSize,
                        int allocationType, int pageProtection, PointerBase extendedParameters, int parameterCount);
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer commit(PointerBase start, UnsignedWord nbytes, int access) {
        if ((start.isNonNull() && !isAligned(start)) || nbytes.equal(0)) {
            return WordFactory.nullPointer();
        }

        /*
         * VirtualAlloc only guarantees the zeroing for freshly committed pages (i.e., the content
         * of pages that were already committed earlier won't be touched).
         */
        return MemoryAPI.VirtualAlloc(start, nbytes, MemoryAPI.MEM_COMMIT(), accessAsProt(access));
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int protect(PointerBase start, UnsignedWord nbytes, int access) {
        if (start.isNull() || !isAligned(start) || nbytes.equal(0)) {
            return -1;
        }

        CIntPointer oldProt = StackValue.get(CIntPointer.class);
        int result = MemoryAPI.VirtualProtect(start, nbytes, accessAsProt(access), oldProt);
        return (result != 0) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int uncommit(PointerBase start, UnsignedWord nbytes) {
        if (start.isNull() || !isAligned(start) || nbytes.equal(0)) {
            return -1;
        }

        int result = MemoryAPI.VirtualFree(start, nbytes, MemoryAPI.MEM_DECOMMIT());
        return (result != 0) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int free(PointerBase start, UnsignedWord nbytes) {
        if (start.isNull() || !isAligned(start) || nbytes.equal(0)) {
            return -1;
        }

        /*
         * The reserved address range may have been split into multiple placeholders (some of which
         * are memory mapped), so we start from the end of the range and work our way backwards.
         */
        MemoryAPI.MEMORY_BASIC_INFORMATION memoryInfo = StackValue.get(MemoryAPI.MEMORY_BASIC_INFORMATION.class);
        Pointer end = ((Pointer) start).add(nbytes).subtract(1);
        while (end.aboveThan((Pointer) start)) {
            if (MemoryAPI.VirtualQuery(end, memoryInfo, SizeOf.unsigned(MemoryAPI.MEMORY_BASIC_INFORMATION.class)).equal(0)) {
                return -1;
            }

            if (!free(memoryInfo.AllocationBase(), memoryInfo.Type() == MemoryAPI.MEM_MAPPED())) {
                return -1;
            }
            end = ((Pointer) memoryInfo.AllocationBase()).subtract(1);
        }
        return 0;
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static boolean free(PointerBase allocationBase, boolean isMemoryMapped) {
        if (isMemoryMapped) {
            return MemoryAPI.UnmapViewOfFile(allocationBase) != 0;
        } else {
            return MemoryAPI.VirtualFree(allocationBase, WordFactory.zero(), MemoryAPI.MEM_RELEASE()) != 0;
        }
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private boolean isAligned(PointerBase ptr) {
        return ptr.isNonNull() && UnsignedUtils.isAMultiple((UnsignedWord) ptr, getGranularity());
    }
}
