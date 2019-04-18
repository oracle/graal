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

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.windows.headers.WinBase;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.os.VirtualMemoryProvider;

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
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
        WinBase.SYSTEM_INFO sysInfo = StackValue.get(WinBase.SYSTEM_INFO.class);
        WinBase.GetSystemInfo(sysInfo);
        int pageSize = sysInfo.dwPageSize();
        Word value = WordFactory.unsigned(pageSize);
        CACHED_PAGE_SIZE.get().write(value);
        int granularity = sysInfo.dwAllocationGranularity();
        value = WordFactory.unsigned(granularity);
        CACHED_ALLOC_GRAN.get().write(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getPageSize() {
        Word value = CACHED_PAGE_SIZE.get().read();
        if (value.equal(WordFactory.zero())) {
            initCaches();
            value = CACHED_PAGE_SIZE.get().read();
        }
        return value;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getGranularity() {
        Word value = CACHED_ALLOC_GRAN.get().read();
        if (value.equal(WordFactory.zero())) {
            initCaches();
            value = CACHED_ALLOC_GRAN.get().read();
        }
        return value;
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static int accessAsProt(int access) {
        if (access == Access.NONE) {
            return WinBase.PAGE_NOACCESS();
        }

        if ((access & Access.EXECUTE) != 0) {
            if ((access & Access.READ) != 0) {
                if ((access & Access.WRITE) != 0) {
                    return WinBase.PAGE_EXECUTE_READWRITE();
                } else {
                    return WinBase.PAGE_EXECUTE_READ();
                }
            }
            if ((access & Access.WRITE) != 0) {
                return WinBase.PAGE_EXECUTE_READWRITE();
            }
            return WinBase.PAGE_EXECUTE();
        } else {
            if ((access & Access.READ) != 0) {
                if ((access & Access.WRITE) != 0) {
                    return WinBase.PAGE_READWRITE();
                } else {
                    return WinBase.PAGE_READONLY();
                }
            }
            if ((access & Access.WRITE) != 0) {
                return WinBase.PAGE_READWRITE();
            }
            return WinBase.PAGE_NOACCESS();
        }
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    private static int accessForMap(int access) {
        int prot = 0;

        if ((access & Access.EXECUTE) != 0) {
            prot |= WinBase.FILE_MAP_EXECUTE();
        }
        if ((access & Access.WRITE) != 0) {
            prot |= WinBase.FILE_MAP_WRITE();
        }
        if ((access & Access.READ) != 0) {
            prot |= WinBase.FILE_MAP_READ();
        }

        return prot;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer reserve(UnsignedWord nbytes) {
        return WinBase.VirtualAlloc(WordFactory.nullPointer(), nbytes, WinBase.MEM_RESERVE(), WinBase.PAGE_READWRITE());
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer mapFile(PointerBase start, UnsignedWord nbytes, WordBase fileHandle, UnsignedWord offset, int access) {
        long fHandle = fileHandle.rawValue();
        int prot = accessAsProt(access);
        int sizeHi = (int) (nbytes.rawValue() >>> 32);
        int sizeLo = (int) (nbytes.rawValue() & 0xFFFFFFFF);

        Pointer fileMapping = WinBase.CreateFileMapping(fHandle, null, prot, sizeHi, sizeLo, null);
        if (fileMapping.isNull()) {
            return WordFactory.nullPointer();
        }

        int offsetHi = (int) (offset.rawValue() >>> 32);
        int offsetLo = (int) (offset.rawValue() & 0xFFFFFFFF);
        Pointer addr = WinBase.MapViewOfFile(fileMapping, accessForMap(access), offsetHi, offsetLo, nbytes);

        return fileMapping.isNull() ? WordFactory.nullPointer() : addr;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer commit(PointerBase start, UnsignedWord nbytes, int access) {
        Pointer addr = WinBase.VirtualAlloc(start, nbytes, WinBase.MEM_COMMIT(), accessAsProt(access));
        return addr.isNull() ? WordFactory.nullPointer() : addr;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int protect(PointerBase start, UnsignedWord nbytes, int access) {
        CIntPointer oldProt = StackValue.get(CIntPointer.class);
        int result = WinBase.VirtualProtect(start, nbytes, accessAsProt(access), oldProt);
        return (result != 0) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int uncommit(PointerBase start, UnsignedWord nbytes) {
        int result = WinBase.VirtualFree(start, nbytes, WinBase.MEM_DECOMMIT());
        return (result != 0) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int free(PointerBase start, UnsignedWord nbytes) {
        return WinBase.VirtualFree(start, nbytes, WinBase.MEM_RELEASE());
    }
}
