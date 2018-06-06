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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.core.posix.headers.Mman.MAP_32BIT;
import static com.oracle.svm.core.posix.headers.Mman.MAP_ANON;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.NoTransitions.mmap;
import static org.graalvm.word.WordFactory.nullPointer;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.PosixVirtualMemoryProvider;

public class LinuxVirtualMemoryProvider extends PosixVirtualMemoryProvider {
    @Override
    @Uninterruptible(reason = "May be called from uninterruptible code.", mayBeInlined = true)
    public Pointer commit(PointerBase start, UnsignedWord nbytes, int access) {
        if (start.isNull() && (access & Access.EXECUTE) != 0) {
            /*
             * First try to allocate executable memory in the 32-bit address space (which is not
             * done by default on Linux!). This is to get 32-bit displacements for calls in runtime
             * compiled code to image compiled code.
             */
            int flags = MAP_ANON() | MAP_PRIVATE() | MAP_32BIT();
            final Pointer result = mmap(nullPointer(), nbytes, accessAsProt(access), flags, NO_FD, NO_FD_OFFSET);
            if (result.notEqual(MAP_FAILED())) {
                return result;
            }
        }
        return super.commit(start, nbytes, access);
    }
}
