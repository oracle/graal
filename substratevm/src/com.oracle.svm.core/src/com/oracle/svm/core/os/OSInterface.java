/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import java.io.FileDescriptor;
import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;

public abstract class OSInterface {

    /**
     * An explicit default constructor.
     *
     * An instance of a concrete subclass is constructed in NativeImageGenerator.createOSInterface
     * from the class of the concrete subclass. The instantiation must happen during native image
     * construction.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public OSInterface() {
        super();
    }

    public void writeBytes(FileDescriptor descriptor, byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException();
        }
        if (bytes.length != 0) {
            if (!writeBytes0(descriptor, bytes, 0, bytes.length, true)) {
                throw new IOException("Write failed");
            }
        }
    }

    protected abstract boolean writeBytes0(FileDescriptor descriptor, byte[] bytes, int off, int len, boolean append);

    /**
     * Writes the raw bytes to the specified file descriptor.
     *
     * @return true if output was successful, false otherwise.
     */
    @Uninterruptible(reason = "Bytes might be from an Object.")
    public abstract boolean writeBytesUninterruptibly(FileDescriptor descriptor, CCharPointer bytes, UnsignedWord length);

    /**
     * Flushes all modified in-memory data written to the file descriptor to its permanent storage
     * device.
     *
     * @param descriptor a file descriptor
     * @return true if the operation succeeds
     */
    public abstract boolean flush(FileDescriptor descriptor);

    /** Like flush, but it is uninterruptible. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public abstract boolean flushUninterruptibly(FileDescriptor descriptor);

    /** Return the current position in a file descriptor. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public abstract long lseekCurUninterruptibly(FileDescriptor descriptor, long offset);

    /** lseek uninterruptibly to an offset of a file descriptor. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public abstract long lseekSetUninterruptibly(FileDescriptor descriptor, long offset);

    /** lseek uninterruptibly to an offset of a file descriptor. */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public abstract long lseekEndUninterruptibly(FileDescriptor descriptor, long offset);

    public abstract void exit(int status);

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public abstract void abort();

    public enum StandardIO {
        IN(FileDescriptor.in),
        OUT(FileDescriptor.out),
        ERR(FileDescriptor.err);

        public final FileDescriptor fd;

        StandardIO(FileDescriptor fd) {
            this.fd = fd;
        }
    }

    public abstract FileDescriptor redirectStandardIO(StandardIO stdio, int nativeFD);
}
