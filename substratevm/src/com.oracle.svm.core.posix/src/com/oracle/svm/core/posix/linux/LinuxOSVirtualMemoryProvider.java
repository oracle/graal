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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.core.posix.headers.Mman.MAP_32BIT;
import static com.oracle.svm.core.posix.headers.Mman.MAP_ANON;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.PROT_EXEC;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;
import static com.oracle.svm.core.posix.headers.Mman.mmap;

import org.graalvm.nativeimage.Platform.LINUX;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.posix.PosixOSVirtualMemoryProvider;

@Platforms(LINUX.class)
public class LinuxOSVirtualMemoryProvider extends PosixOSVirtualMemoryProvider {

    @Override
    public Pointer allocateVirtualMemory(UnsignedWord size, boolean executable) {
        trackVirtualMemory(size);
        int protect = PROT_READ() | PROT_WRITE();
        int flags = MAP_ANON() | MAP_PRIVATE();
        if (executable) {
            protect |= PROT_EXEC();

            /*
             * First try to allocate executable memory in the 32 bit address space (which is not
             * done by default on linux!). This is to get 32-bit displacements for calls in runtime
             * compiled code to image compiled code.
             */
            final Pointer result = mmap(WordFactory.nullPointer(), size, protect, flags | MAP_32BIT(), -1, 0);
            if (!result.equal(MAP_FAILED())) {
                return result;
            }
        }
        final Pointer result = mmap(WordFactory.nullPointer(), size, protect, flags, -1, 0);
        if (result.equal(MAP_FAILED())) {
            // Turn the mmap failure into a null Pointer.
            return WordFactory.nullPointer();
        }
        return result;
    }
}
