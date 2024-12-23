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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.core.posix.headers.Mman.MAP_SHARED;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.mmap;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.shm_open;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.shm_unlink;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider.Access;
import com.oracle.svm.core.pltgot.GOTHeapSupport;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.thread.VMThreads;

public class LinuxGOTHeapSupport extends GOTHeapSupport {

    private static final String FILE_NAME_PREFIX = "/ni-got-";
    private static final int FILE_NAME_PREFIX_LEN = FILE_NAME_PREFIX.length();
    private static final CGlobalData<WordPointer> memoryViewFd = CGlobalDataFactory.createWord((WordBase) Word.signed(-1));
    private static final CGlobalData<CCharPointer> fileNamePrefix = CGlobalDataFactory.createCString(FILE_NAME_PREFIX);

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected int initialize(WordPointer gotStartAddress) {
        int pid = Unistd.NoTransitions.getpid();
        int pidLen = 0;
        int temp = pid;
        while (temp != 0) {
            pidLen++;
            temp /= 10;
        }

        /* GOT shared memory file name format: fileNamePrefix-pid */
        CCharPointer nameStr = StackValue.get(64 * SizeOf.get(CCharPointer.class));
        LibC.memcpy(nameStr, fileNamePrefix.get(), Word.unsigned(FILE_NAME_PREFIX_LEN));

        int iter = FILE_NAME_PREFIX_LEN + pidLen;
        nameStr.write(iter, (byte) '\0');
        temp = pid;

        while (temp != 0) {
            iter--;
            nameStr.write(iter, (byte) ('0' + (temp % 10)));
            temp /= 10;
        }

        int fd = -1;
        for (int i = 0; i < 10; ++i) {
            fd = shm_open(nameStr, Fcntl.O_CREAT() | Fcntl.O_EXCL() | Fcntl.O_RDWR(), 0);
            if (fd == -1) {
                int errno = LibC.errno();
                if (errno != Errno.EEXIST()) {
                    return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_CREATE_FAILED;
                }
                VMThreads.singleton().nativeSleep(5);
            } else {
                shm_unlink(nameStr);
                break;
            }
        }

        if (fd == -1) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_UNIQUE_FILE_CREATE_FAILED;
        }

        UnsignedWord gotPageAlignedSize = getPageAlignedGotSize();

        if (Unistd.NoTransitions.ftruncate(fd, Word.signed(gotPageAlignedSize.rawValue())) != 0) {
            Unistd.NoTransitions.close(fd);
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_RESIZE_FAILED;
        }

        Pointer gotMemory = mmap(Word.nullPointer(), gotPageAlignedSize, PROT_READ() | PROT_WRITE(), MAP_SHARED(), fd, 0);
        if (gotMemory.isNull()) {
            Unistd.NoTransitions.close(fd);
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_MAP_FAILED;
        }

        Pointer gotStartInMemory = gotMemory.add(getGotOffsetFromStartOfMapping());
        LibC.memcpy(gotStartInMemory, IMAGE_GOT_BEGIN.get(), getGotSectionSize());

        /* Keep the initial GOT mapping for writing. */

        memoryViewFd.get().write(Word.signed(fd));
        gotStartAddress.write(gotMemory);

        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int mapGot(Pointer start) {
        SignedWord memViewFd = memoryViewFd.get().read();
        if (memViewFd.lessThan(0)) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_FD_INVALID;
        }

        Pointer mappedAddress = VirtualMemoryProvider.get().mapFile(
                        start,
                        getPageAlignedGotSize(),
                        memViewFd,
                        Word.zero(),
                        Access.READ);

        if (mappedAddress.isNull()) {
            return CEntryPointErrors.DYNAMIC_METHOD_ADDRESS_RESOLUTION_GOT_MMAP_FAILED;
        }

        return CEntryPointErrors.NO_ERROR;
    }
}
