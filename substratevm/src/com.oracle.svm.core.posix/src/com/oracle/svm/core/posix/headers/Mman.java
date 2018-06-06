/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/mman.h.
 */
@CContext(PosixDirectives.class)
public class Mman {

    /*
     * Protections are chosen from these bits, OR'd together. The implementation does not
     * necessarily support PROT_EXEC or PROT_WRITE without PROT_READ. The only guarantees are that
     * no writing will be allowed without PROT_WRITE and no access will be allowed for PROT_NONE.
     */

    /** Page can be read. */
    @CConstant
    public static native int PROT_READ();

    /** Page can be written. */
    @CConstant
    public static native int PROT_WRITE();

    /** Page can be executed. */
    @CConstant
    public static native int PROT_EXEC();

    /** Page can not be accessed. */
    @CConstant
    public static native int PROT_NONE();

    /** Extend change to start of growsdown vma (mprotect only). */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PROT_GROWSDOWN();

    /** Extend change to start of growsup vma (mprotect only). */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int PROT_GROWSUP();

    /* Sharing types (must choose one and only one of these). */

    /** Share changes. */
    @CConstant
    public static native int MAP_SHARED();

    /** Changes are private. */
    @CConstant
    public static native int MAP_PRIVATE();

    /** Mask for type of mapping. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_TYPE();

    /* Other flags. */

    /** Interpret addr exactly. */
    @CConstant
    public static native int MAP_FIXED();

    @CConstant
    public static native int MAP_FILE();

    /** Don't use a file. */
    public static int MAP_ANONYMOUS() {
        return MAP_ANON();
    }

    @CConstant
    public static native int MAP_ANON();

    /** Only give out 32-bit addresses. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_32BIT();

    /* These are Linux-specific. */

    /** Stack-like segment. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_GROWSDOWN();

    /** ETXTBSY */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_DENYWRITE();

    /** Mark it as an executable. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_EXECUTABLE();

    /** Lock the mapping. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_LOCKED();

    /** Don't check for reservations. */
    @CConstant
    public static native int MAP_NORESERVE();

    /** Populate (prefault) pagetables. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_POPULATE();

    /** Do not block on IO. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MAP_NONBLOCK();

    // [not present on old Linux systems]
    // /** Allocation is for a stack. */
    // @CConstant
    // public static native int MAP_STACK();
    //
    // /** Create huge page mapping. */
    // @CConstant
    // public static native int MAP_HUGETLB();

    /* Flags to `msync'. */

    /** Sync memory asynchronously. */
    @CConstant
    public static native int MS_ASYNC();

    /** Synchronous memory sync. */
    @CConstant
    public static native int MS_SYNC();

    /** Invalidate the caches. */
    @CConstant
    public static native int MS_INVALIDATE();

    /* Flags for `mlockall'. */

    /** Lock all currently mapped pages. */
    @CConstant
    public static native int MCL_CURRENT();

    /** Lock all additions to address space. */
    @CConstant
    public static native int MCL_FUTURE();

    /* Flags for `mremap'. */

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MREMAP_MAYMOVE();

    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int MREMAP_FIXED();

    /* Advice to `madvise'. */

    /** No further special treatment. */
    @CConstant
    public static native int MADV_NORMAL();

    /** Expect random page references. */
    @CConstant
    public static native int MADV_RANDOM();

    /** Expect sequential page references. */
    @CConstant
    public static native int MADV_SEQUENTIAL();

    /** Will need these pages. */
    @CConstant
    public static native int MADV_WILLNEED();

    /** Don't need these pages. */
    @CConstant
    public static native int MADV_DONTNEED();

    // [not present on old Linux systems]
    // /** Remove these pages and resources. */
    // @CConstant
    // public static native int MADV_REMOVE();
    //
    // /** Do not inherit across fork. */
    // @CConstant
    // public static native int MADV_DONTFORK();
    //
    // /** Do inherit across fork. */
    // @CConstant
    // public static native int MADV_DOFORK();
    //
    // /** KSM may merge identical pages. */
    // @CConstant
    // public static native int MADV_MERGEABLE();
    //
    // /** KSM may not merge identical pages. */
    // @CConstant
    // public static native int MADV_UNMERGEABLE();
    //
    // /** Worth backing with hugepages. */
    // @CConstant
    // public static native int MADV_HUGEPAGE();
    //
    // /** Not worth backing with hugepages. */
    // @CConstant
    // public static native int MADV_NOHUGEPAGE();
    //
    // /** Explicity exclude from the core dump, overrides the coredump filter bits. */
    // @CConstant
    // public static native int MADV_DONTDUMP();
    //
    // /** Clear the MADV_DONTDUMP flag. */
    // @CConstant
    // public static native int MADV_DODUMP();
    //
    // /** Poison a page for testing. */
    // @CConstant
    // public static native int MADV_HWPOISON();

    /* The POSIX people had to invent similar names for the same things. */

    /** No further special treatment. */
    @CConstant
    public static native int POSIX_MADV_NORMAL();

    /** Expect random page references. */
    @CConstant
    public static native int POSIX_MADV_RANDOM();

    /** Expect sequential page references. */
    @CConstant
    public static native int POSIX_MADV_SEQUENTIAL();

    /** Will need these pages. */
    @CConstant
    public static native int POSIX_MADV_WILLNEED();

    /** Don't need these pages. */
    @CConstant
    public static native int POSIX_MADV_DONTNEED();

    /** Return value of `mmap' in case of an error. */
    @CConstant
    public static native PointerBase MAP_FAILED();

    /**
     * Map addresses starting near ADDR and extending for LEN bytes. from OFFSET into the file FD
     * describes according to PROT and FLAGS. If ADDR is nonzero, it is the desired mapping address.
     * If the MAP_FIXED bit is set in FLAGS, the mapping will be at ADDR exactly (which must be
     * page-aligned); otherwise the system chooses a convenient nearby address. The return value is
     * the actual mapping address chosen or MAP_FAILED for errors (in which case `errno' is set). A
     * successful `mmap' call deallocates any previous mapping for the affected region.
     */
    @CFunction
    public static native Pointer mmap(PointerBase addr, UnsignedWord len, int prot, int flags, int fd, long offset);

    /**
     * Deallocate any mapping for the region starting at ADDR and extending LEN bytes. Returns 0 if
     * successful, -1 for errors (and sets errno).
     */
    @CFunction
    public static native int munmap(PointerBase addr, UnsignedWord len);

    /**
     * Change the memory protection of the region starting at ADDR and extending LEN bytes to PROT.
     * Returns 0 if successful, -1 for errors (and sets errno).
     */
    @CFunction
    public static native int mprotect(PointerBase addr, UnsignedWord len, int prot);

    /**
     * Synchronize the region starting at ADDR and extending LEN bytes with the file it maps.
     * Filesystem operations on a file being mapped are unpredictable before this is done. Flags are
     * from the MS_* set.
     */
    @CFunction
    public static native int msync(PointerBase addr, UnsignedWord len, int flags);

    /**
     * Advise the system about particular usage patterns the program follows for the region starting
     * at ADDR and extending LEN bytes.
     */
    @CFunction
    public static native int madvise(PointerBase addr, UnsignedWord len, int advice);

    /** This is the POSIX name for this function. */
    @CFunction
    public static native int posix_madvise(PointerBase addr, UnsignedWord len, int advice);

    /**
     * Guarantee all whole pages mapped by the range [ADDR,ADDR+LEN) to be memory resident.
     */
    @CFunction
    public static native int mlock(PointerBase addr, UnsignedWord len);

    /** Unlock whole pages previously mapped by the range [ADDR,ADDR+LEN). */
    @CFunction
    public static native int munlock(PointerBase addr, UnsignedWord len);

    /**
     * Cause all currently mapped pages of the process to be memory resident until unlocked by a
     * call to the `munlockall', until the process exits, or until the process calls `execve'.
     */
    @CFunction
    public static native int mlockall(int flags);

    /** All currently mapped pages of the process' address space become unlocked. */
    @CFunction
    public static native int munlockall();

    /**
     * mincore returns the memory residency status of the pages in the current process's address
     * space specified by [start, start + len). The status is returned in a vector of bytes. The
     * least significant bit of each byte is 1 if the referenced page is in memory, otherwise it is
     * zero.
     */
    @CFunction
    public static native int mincore(PointerBase start, UnsignedWord len, CCharPointer vec);

    /**
     * Remap pages mapped by the range [ADDR,ADDR+OLD_LEN) to new length NEW_LEN. If MREMAP_MAYMOVE
     * is set in FLAGS the returned address may differ from ADDR. If MREMAP_FIXED is set in FLAGS
     * the function takes another paramter which is a fixed address at which the block resides after
     * a successful call.
     */
    @CFunction
    public static native PointerBase mremap(PointerBase addr, UnsignedWord old_len, UnsignedWord new_len, int flags, PointerBase new_address);

    /** Remap arbitrary pages of a shared backing store within an existing VMA. */
    @CFunction
    public static native int remap_file_pages(PointerBase start, UnsignedWord size, int prot, UnsignedWord pgoff, int flags);

    /** Open shared memory segment. */
    @CFunction
    public static native int shm_open(CCharPointer name, int oflag, int mode);

    /** Remove shared memory segment. */
    @CFunction
    public static native int shm_unlink(CCharPointer name);

    public static class NoTransitions {
        @CFunction(transition = Transition.NO_TRANSITION)
        public static native Pointer mmap(PointerBase addr, UnsignedWord len, int prot, int flags, int fd, long offset);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int munmap(PointerBase addr, UnsignedWord len);

        @CFunction(transition = Transition.NO_TRANSITION)
        public static native int mprotect(PointerBase addr, UnsignedWord len, int prot);
    }
}
