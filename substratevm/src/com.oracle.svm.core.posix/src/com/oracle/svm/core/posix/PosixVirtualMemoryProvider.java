/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.posix.headers.Mman.MAP_ANON;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FIXED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_NORESERVE;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.PROT_EXEC;
import static com.oracle.svm.core.posix.headers.Mman.PROT_NONE;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.mmap;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.mprotect;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.munmap;
import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
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
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;

@AutomaticFeature
class PosixVirtualMemoryProviderFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(VirtualMemoryProvider.class)) {
            ImageSingletons.add(VirtualMemoryProvider.class, new PosixVirtualMemoryProvider());
        }
    }
}

public class PosixVirtualMemoryProvider implements VirtualMemoryProvider {
    protected static final int NO_FD = -1;
    protected static final int NO_FD_OFFSET = 0;

    private static final CGlobalData<WordPointer> CACHED_PAGE_SIZE = CGlobalDataFactory.createWord();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getPageSize() {
        Word value = CACHED_PAGE_SIZE.get().read();
        if (value.equal(WordFactory.zero())) {
            long queried = Unistd.NoTransitions.sysconf(Unistd._SC_PAGE_SIZE());
            value = WordFactory.unsigned(queried);
            CACHED_PAGE_SIZE.get().write(value);
        }
        return value;
    }

    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    protected static int accessAsProt(int access) {
        int prot = 0;
        if ((access & Access.READ) != 0) {
            prot |= PROT_READ();
        }
        if ((access & Access.WRITE) != 0) {
            prot |= PROT_WRITE();
        }
        if ((access & Access.EXECUTE) != 0) {
            prot |= PROT_EXEC();
        }
        return prot;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getGranularity() {
        return getPageSize();
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer reserve(UnsignedWord nbytes, UnsignedWord alignment) {
        UnsignedWord granularity = getGranularity();
        boolean customAlignment = !UnsignedUtils.isAMultiple(granularity, alignment);
        UnsignedWord mappingSize = nbytes;
        if (customAlignment) {
            mappingSize = mappingSize.add(alignment);
        }
        mappingSize = UnsignedUtils.roundUp(mappingSize, granularity);
        Pointer mappingBegin = mmap(nullPointer(), mappingSize, PROT_NONE(), MAP_ANON() | MAP_PRIVATE() | MAP_NORESERVE(), NO_FD, NO_FD_OFFSET);
        if (mappingBegin.equal(MAP_FAILED())) {
            return nullPointer();
        }
        if (!customAlignment) {
            return mappingBegin;
        }
        Pointer begin = PointerUtils.roundUp(mappingBegin, alignment);
        UnsignedWord clippedBegin = begin.subtract(mappingBegin);
        if (clippedBegin.aboveOrEqual(granularity)) {
            munmap(mappingBegin, UnsignedUtils.roundDown(clippedBegin, granularity));
        }
        Pointer mappingEnd = mappingBegin.add(mappingSize);
        UnsignedWord clippedEnd = mappingEnd.subtract(begin.add(nbytes));
        if (clippedEnd.aboveOrEqual(granularity)) {
            UnsignedWord rounded = UnsignedUtils.roundDown(clippedEnd, granularity);
            munmap(mappingEnd.subtract(rounded), rounded);
        }
        return begin;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer mapFile(PointerBase start, UnsignedWord nbytes, WordBase fileHandle, UnsignedWord offset, int access) {
        int flags = MAP_PRIVATE();
        if (start.isNonNull()) {
            flags |= MAP_FIXED();
        }
        int fd = (int) fileHandle.rawValue();
        Pointer result = mmap(start, nbytes, accessAsProt(access), flags, fd, offset.rawValue());
        return result.notEqual(MAP_FAILED()) ? result : WordFactory.nullPointer();
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer commit(PointerBase start, UnsignedWord nbytes, int access) {
        int flags = MAP_ANON() | MAP_PRIVATE();
        if (start.isNonNull()) {
            flags |= MAP_FIXED();
        }
        final Pointer result = mmap(start, nbytes, accessAsProt(access), flags, NO_FD, NO_FD_OFFSET);
        return result.notEqual(MAP_FAILED()) ? result : nullPointer();
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int protect(PointerBase start, UnsignedWord nbytes, int access) {
        return mprotect(start, nbytes, accessAsProt(access));
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int uncommit(PointerBase start, UnsignedWord nbytes) {
        final Pointer result = mmap(start, nbytes, PROT_NONE(), MAP_FIXED() | MAP_ANON() | MAP_PRIVATE() | MAP_NORESERVE(), NO_FD, NO_FD_OFFSET);
        return result.notEqual(MAP_FAILED()) ? 0 : -1;
    }

    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public int free(PointerBase start, UnsignedWord nbytes) {
        UnsignedWord granularity = getGranularity();
        Pointer mappingBegin = PointerUtils.roundDown(start, granularity);
        UnsignedWord mappingSize = UnsignedUtils.roundUp(nbytes, granularity);
        return munmap(mappingBegin, mappingSize);
    }
}
