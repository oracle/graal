/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.heapdump;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heapdump.HeapDumpWriterImpl.AllocationFreeFileOutputStream;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.VMError;

/**
 * Posix implementation of allocation-free output stream created from FileOutputStream.
 *
 * The limitation to Linux and Darwin is necessary because the implementation currently uses
 * posix-dependent low-level code. See GR-9725.
 */
@AutomaticallyRegisteredImageSingleton(AllocationFreeFileOutputStream.class)
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
final class AllocationFreeFileOutputStreamPosix extends AllocationFreeFileOutputStream {

    /**
     * Pre-allocated exceptions, for throwing from code that must not allocate.
     */
    private static final IOException preallocatedIOException = new IOException("Write failed.");
    private static final ArrayIndexOutOfBoundsException preallocatedArrayIndexOutOfBoundsException = new ArrayIndexOutOfBoundsException();

    private FileOutputStream fos;
    private FileDescriptor fileDescriptor;

    AllocationFreeFileOutputStreamPosix() {

    }

    private AllocationFreeFileOutputStreamPosix(FileOutputStream fileOutputStream) throws IOException {
        fos = fileOutputStream;
        fileDescriptor = fos.getFD();

        if (!(Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class))) {
            /* See GR-9725 */
            throw VMError.unsupportedFeature("Heap dump writing currently contains Posix specific code");
        }
    }

    @Override
    public AllocationFreeFileOutputStream newStreamFor(FileOutputStream fileOutputStream) throws IOException {
        return new AllocationFreeFileOutputStreamPosix(fileOutputStream);
    }

    @Override
    public void write(int b) throws IOException {
        final CCharPointer buffer = UnsafeStackValue.get(CCharPointer.class);
        buffer.write((byte) b);
        final boolean writeResult = PosixUtils.writeBytes(fileDescriptor, buffer, WordFactory.unsigned(1));
        if (!writeResult) {
            throw preallocatedIOException;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        /* Sanity check the arguments. */
        if ((b == null) || ((off < 0) || (len < 0) || ((b.length - off) < len))) {
            throw preallocatedArrayIndexOutOfBoundsException;
        }

        /*
         * Stack allocation needs an allocation size that is a compile time constant, so we split
         * the byte array up in multiple chunks and write them separately.
         */
        final int chunkSize = 256;
        final CCharPointer bytes = UnsafeStackValue.get(chunkSize);

        int chunkOffset = off;
        int inputLength = len;
        while (inputLength > 0) {
            int chunkLength = Math.min(inputLength, chunkSize);

            for (int i = 0; i < chunkLength; i++) {
                bytes.write(i, b[chunkOffset + i]);
            }

            if (!PosixUtils.writeBytes(fileDescriptor, bytes, WordFactory.unsigned(chunkLength))) {
                throw preallocatedIOException;
            }

            chunkOffset += chunkLength;
            inputLength -= chunkLength;
        }
    }

    @Override
    public void close() throws IOException {
        fos.close();
    }

    @Override
    public void flush() throws IOException {
    }

    /**
     * Read the current position in a file descriptor.
     */
    @Override
    protected long position() {
        int fd = PosixUtils.getFD(fileDescriptor);
        return Unistd.lseek(fd, WordFactory.zero(), Unistd.SEEK_CUR()).rawValue();
    }

    /**
     * Set the current position in a file descriptor.
     */
    @Override
    protected long position(long offset) {
        int fd = PosixUtils.getFD(fileDescriptor);
        return Unistd.lseek(fd, WordFactory.signed(offset), Unistd.SEEK_SET()).rawValue();
    }
}
