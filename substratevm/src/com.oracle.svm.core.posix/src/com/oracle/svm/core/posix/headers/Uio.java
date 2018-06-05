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
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/uio.h.
 */
@CContext(PosixDirectives.class)
public class Uio {

    /** Size of object which can be written atomically. */
    @CConstant
    @Platforms(Platform.LINUX.class)
    public static native int UIO_MAXIOV();

    /** Structure for scatter/gather I/O. */
    @CStruct("struct iovec")
    public interface iovec extends PointerBase {

        /** Pointer to data. */
        @CField
        PointerBase iov_base();

        @CField
        void iov_base(PointerBase value);

        /** Length of data. */
        @CField
        UnsignedWord iov_len();

        @CField
        void iov_len(UnsignedWord value);
    }

    /** Read from another process' address space. */
    @CFunction
    public static native SignedWord process_vm_readv(int pid, iovec lvec, UnsignedWord liovcnt, iovec rvec, UnsignedWord riovcnt, UnsignedWord flags);

    /** Write to another process' address space. */
    @CFunction
    public static native SignedWord process_vm_writev(int pid, iovec lvec, UnsignedWord liovcnt, iovec rvec, UnsignedWord riovcnt, UnsignedWord flags);

    /**
     * Read data from file descriptor FD, and put the result in the buffers described by IOVEC,
     * which is a vector of COUNT 'struct iovec's. The buffers are filled in the order specified.
     * Operates just like 'read' (see <unistd.h>) except that data are put in IOVEC instead of a
     * contiguous buffer.
     *
     * This function is a cancellation point and therefore not marked with THROW.
     */
    @CFunction
    public static native SignedWord readv(int fd, iovec iovec, int count);

    /**
     * Write data pointed by the buffers described by IOVEC, which is a vector of COUNT 'struct
     * iovec's, to file descriptor FD. The data is written in the order specified. Operates just
     * like 'write' (see <unistd.h>) except that the data are taken from IOVEC instead of a
     * contiguous buffer.
     *
     * This function is a cancellation point and therefore not marked with THROW.
     */
    @CFunction
    public static native SignedWord writev(int fd, iovec iovec, int count);

    /**
     * Read data from file descriptor FD at the given position OFFSET without change the file
     * pointer, and put the result in the buffers described by IOVEC, which is a vector of COUNT
     * 'struct iovec's. The buffers are filled in the order specified. Operates just like 'pread'
     * (see <unistd.h>) except that data are put in IOVEC instead of a contiguous buffer.
     */
    @CFunction
    public static native SignedWord preadv(int fd, iovec iovec, int count, long offset);

    /**
     * Write data pointed by the buffers described by IOVEC, which is a vector of COUNT 'struct
     * iovec's, to file descriptor FD at the given position OFFSET without change the file pointer.
     * The data is written in the order specified. Operates just like 'pwrite' (see <unistd.h>)
     * except that the data are taken from IOVEC instead of a contiguous buffer.
     */
    @CFunction
    public static native SignedWord pwritev(int fd, iovec iovec, int count, long offset);
}
