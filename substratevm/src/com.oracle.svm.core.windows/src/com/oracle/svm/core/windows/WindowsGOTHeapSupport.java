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
package com.oracle.svm.core.windows;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.pltgot.GOTHeapSupport;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.windows.headers.MemoryAPI;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HANDLE;

public class WindowsGOTHeapSupport extends GOTHeapSupport {

    private static final CGlobalData<WordPointer> GOT_MAPPING_HANDLE = CGlobalDataFactory.createWord();

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int initialize(WordPointer gotStartAddress) {
        UnsignedWord alignedGotSize = getPageAlignedGotSize();

        HANDLE gotMappingHandle = MemoryAPI.CreateFileMappingW(
                        WinBase.INVALID_HANDLE_VALUE(), // in-memory
                        Word.nullPointer(),
                        MemoryAPI.PAGE_READWRITE(),
                        0,
                        UnsignedUtils.safeToInt(alignedGotSize),
                        Word.nullPointer() // anonymous
        );

        if (gotMappingHandle.isNull()) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_CREATE_FAILED;
        }

        Pointer gotMappedAddress = MemoryAPI.MapViewOfFile(
                        gotMappingHandle,
                        MemoryAPI.FILE_MAP_READ() | MemoryAPI.FILE_MAP_WRITE(),
                        0,
                        0,
                        Word.zero() // map it whole
        );

        if (gotMappedAddress.isNull()) {
            WinBase.CloseHandle(gotMappingHandle);
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_MAP_FAILED;
        }

        Pointer gotStartInMemory = gotMappedAddress.add(getGotOffsetFromStartOfMapping());
        LibC.memcpy(gotStartInMemory, IMAGE_GOT_BEGIN.get(), getGotSectionSize());

        // Keep the initial GOT mapping for writing.

        GOT_MAPPING_HANDLE.get().write(gotMappingHandle);
        gotStartAddress.write(gotMappedAddress);

        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int mapGot(Pointer address) {
        HANDLE gotMappingHandle = GOT_MAPPING_HANDLE.get().read();
        if (gotMappingHandle.isNull()) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_INVALID;
        }

        Pointer mappedAddress = VirtualMemoryProvider.get().mapFile(
                        address,
                        getPageAlignedGotSize(),
                        gotMappingHandle,
                        Word.zero(),
                        Access.READ);

        if (mappedAddress.isNull()) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_MMAP_FAILED;
        }

        return CEntryPointErrors.NO_ERROR;
    }
}
