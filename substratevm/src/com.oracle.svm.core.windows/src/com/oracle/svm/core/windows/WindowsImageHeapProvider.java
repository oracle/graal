/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Isolates.IMAGE_HEAP_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_BEGIN;
import static com.oracle.svm.core.Isolates.IMAGE_HEAP_RELOCATABLE_END;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.os.AbstractCopyingImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.windows.headers.FileAPI;
import com.oracle.svm.core.windows.headers.LibLoaderAPI;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;
import com.oracle.svm.core.windows.headers.WindowsLibC.WCharPointer;

/**
 * An image heap provider for Windows that creates image heaps that are copy-on-write clones of the
 * loaded image heap.
 */
public class WindowsImageHeapProvider extends AbstractCopyingImageHeapProvider {
    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int commitAndCopyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        HANDLE imageHeapFileMapping = getImageHeapFileMapping();
        if (imageHeapFileMapping.isNull()) {
            /* Fall back to copying from memory. */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        /* Map a copy-on-write view of the image heap. */
        if (VirtualMemoryProvider.get().mapFile(newImageHeap, imageHeapSize, imageHeapFileMapping, getImageHeapFileOffset(),
                        Access.READ | Access.WRITE).isNull()) {
            /* Fall back to copying from memory. */
            return super.commitAndCopyMemory(loadedImageHeap, imageHeapSize, newImageHeap);
        }

        /* Copy relocatable pages. */
        return copyMemory(IMAGE_HEAP_RELOCATABLE_BEGIN.get(), IMAGE_HEAP_RELOCATABLE_END.get().subtract(IMAGE_HEAP_RELOCATABLE_BEGIN.get()),
                        newImageHeap.add(IMAGE_HEAP_RELOCATABLE_BEGIN.get().subtract(IMAGE_HEAP_BEGIN.get())));
    }

    @Override
    @Uninterruptible(reason = "Called during isolate initialization.")
    protected int copyMemory(Pointer loadedImageHeap, UnsignedWord imageHeapSize, Pointer newImageHeap) {
        LibC.memcpy(newImageHeap, loadedImageHeap, imageHeapSize);
        return CEntryPointErrors.NO_ERROR;
    }

    /**
     * The pseudovariable __ImageBase provided by the MSVC linker represents the DOS header of the
     * module, which is what a Win32 module begins with. In other words, it is the base address of
     * the module and is the same as its HMODULE.
     */
    private static final CGlobalData<PointerBase> IMAGE_BASE = CGlobalDataFactory.forSymbol("__ImageBase");

    /** The cached handle of the image heap file mapping that closes when the process exits. */
    private static final CGlobalData<WordPointer> IMAGE_HEAP_FILE_MAPPING = CGlobalDataFactory.createWord(WindowsUtils.UNINITIALIZED_HANDLE);

    @Uninterruptible(reason = "Called during isolate initialization.", mayBeInlined = true)
    private static HANDLE getImageHeapFileMapping() {
        HANDLE value = IMAGE_HEAP_FILE_MAPPING.get().read();
        if (value.equal(WindowsUtils.UNINITIALIZED_HANDLE)) {
            HANDLE fileMapping = createImageHeapFileMapping();
            HANDLE existingMapping = (HANDLE) ((Pointer) IMAGE_HEAP_FILE_MAPPING.get()).compareAndSwapWord(0,
                            WindowsUtils.UNINITIALIZED_HANDLE, fileMapping, LocationIdentity.ANY_LOCATION);

            if (existingMapping.equal(WindowsUtils.UNINITIALIZED_HANDLE)) {
                value = fileMapping;
            } else {
                /* Another thread has already created the mapping, so use that. */
                value = existingMapping;
                WinBase.CloseHandle(fileMapping);
            }
        }
        return value;
    }

    /** Returns a handle to the image heap file mapping or the null pointer in case of an error. */
    @Uninterruptible(reason = "Called during isolate initialization.", mayBeInlined = true)
    private static HANDLE createImageHeapFileMapping() {
        /* Get the path of the file that contains the image heap. */
        WCharPointer filePath = StackValue.get(WinBase.MAX_PATH, WCharPointer.class);
        int length = LibLoaderAPI.GetModuleFileNameW((HMODULE) IMAGE_BASE.get(), filePath, WinBase.MAX_PATH);
        if (length == 0 || length == WinBase.MAX_PATH) {
            return WordFactory.nullPointer();
        }

        /* Open the file for mapping. */
        HANDLE fileHandle = FileAPI.CreateFileW(filePath, FileAPI.GENERIC_READ(), FileAPI.FILE_SHARE_READ() | FileAPI.FILE_SHARE_DELETE(),
                        WordFactory.nullPointer(), FileAPI.OPEN_EXISTING(), 0, WordFactory.nullPointer());
        if (fileHandle.equal(WinBase.INVALID_HANDLE_VALUE())) {
            return WordFactory.nullPointer();
        }

        /* Create the mapping and close the file. */
        HANDLE fileMapping = MemoryAPI.CreateFileMappingW(fileHandle, WordFactory.nullPointer(), MemoryAPI.PAGE_READONLY(),
                        0, 0, WordFactory.nullPointer());
        WinBase.CloseHandle(fileHandle);
        return fileMapping;
    }

    private static final CGlobalData<WordPointer> IMAGE_HEAP_FILE_OFFSET = CGlobalDataFactory.createWord();

    @Uninterruptible(reason = "Called during isolate initialization.", mayBeInlined = true)
    private static UnsignedWord getImageHeapFileOffset() {
        /*
         * The file offset of a relative virtual address (RVA) in a PE image can be determined by
         * inspecting the PE image headers.
         *
         * We do this using two helper functions from ntdll.dll: RtlImageNtHeader and
         * RtlAddressInSectionTable. Unfortunately, they are not publicly documented, but I think it
         * is unlikely that this will pose a problem (and it would definitely not go unnoticed).
         *
         * Alternatively, we could use equivalent functions from dbghelp.dll (ImageNtHeader and
         * ImageRvaToVa), but this would introduce an additional DLL dependency (which we would like
         * to avoid). Or we could even do it manually. The tricky part would be finding the header
         * of the section containing the RVA.
         */
        UnsignedWord value = IMAGE_HEAP_FILE_OFFSET.get().read();
        if (value.equal(0)) {
            /* Get the NT header of the module that contains the image heap. */
            PointerBase ntHeader = invokeRtlImageNtHeader(IMAGE_BASE.get());
            /* Compute the RVA of the image heap. */
            int rva = (int) (IMAGE_HEAP_BEGIN.get().rawValue() - IMAGE_BASE.get().rawValue());
            /* Get the file offset of the image heap. */
            value = invokeRtlAddressInSectionTable(ntHeader, rva);
            IMAGE_HEAP_FILE_OFFSET.get().write(value);
        }
        return value;
    }

    private static final CGlobalData<CCharPointer> NTDLL_DLL = CGlobalDataFactory.createCString("ntdll.dll");
    private static final CGlobalData<CCharPointer> RTL_IMAGE_NT_HEADER = CGlobalDataFactory.createCString("RtlImageNtHeader");
    private static final CGlobalData<CCharPointer> RTL_ADDRESS_IN_SECTION_TABLE = CGlobalDataFactory.createCString("RtlAddressInSectionTable");

    private static final int ERROR_BAD_EXE_FORMAT = 0xC1;

    /** Locates the IMAGE_NT_HEADERS structure in a PE image and returns a pointer to the data. */
    @Uninterruptible(reason = "Called during isolate initialization.", mayBeInlined = true)
    private static PointerBase invokeRtlImageNtHeader(PointerBase imageBase) {
        RtlImageNtHeader rtlImageNtHeader = WindowsUtils.getFunctionPointer(NTDLL_DLL.get(), RTL_IMAGE_NT_HEADER.get(), true);
        PointerBase ntHeader = rtlImageNtHeader.invoke(imageBase);
        if (ntHeader.isNull()) {
            CEntryPointActions.failFatally(ERROR_BAD_EXE_FORMAT, RTL_IMAGE_NT_HEADER.get());
        }
        return ntHeader;
    }

    private interface RtlImageNtHeader extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        PointerBase invoke(PointerBase imageBase);
    }

    /**
     * Locates a relative virtual address (RVA) within the image header of a file that is mapped as
     * a file and returns the offset of the corresponding byte in the file.
     */
    @Uninterruptible(reason = "Called during isolate initialization.", mayBeInlined = true)
    private static UnsignedWord invokeRtlAddressInSectionTable(PointerBase ntHeader, int rva) {
        RtlAddressInSectionTable rtlAddressInSectionTable = WindowsUtils.getFunctionPointer(NTDLL_DLL.get(), RTL_ADDRESS_IN_SECTION_TABLE.get(), true);
        UnsignedWord offset = (UnsignedWord) rtlAddressInSectionTable.invoke(ntHeader, WordFactory.nullPointer(), rva);
        if (offset.equal(0)) {
            CEntryPointActions.failFatally(ERROR_BAD_EXE_FORMAT, RTL_ADDRESS_IN_SECTION_TABLE.get());
        }
        return offset;
    }

    private interface RtlAddressInSectionTable extends CFunctionPointer {
        @InvokeCFunctionPointer(transition = NO_TRANSITION)
        PointerBase invoke(PointerBase ntHeader, PointerBase base, int rva);
    }
}
