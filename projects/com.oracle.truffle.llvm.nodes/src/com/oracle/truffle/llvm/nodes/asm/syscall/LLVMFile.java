/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.asm.syscall;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import sun.misc.SharedSecrets;

public class LLVMFile {
    public static final int O_ACCMODE = 00000003;
    public static final int O_RDONLY = 00000000;
    public static final int O_WRONLY = 00000001;
    public static final int O_RDWR = 00000002;
    public static final int O_CREAT = 00000100;
    public static final int O_EXCL = 00000200;
    public static final int O_NOCTTY = 00000400;
    public static final int O_TRUNC = 00001000;
    public static final int O_APPEND = 00002000;
    public static final int O_NONBLOCK = 00004000;
    public static final int O_DSYNC = 00010000;
    public static final int FASYNC = 00020000;
    public static final int O_DIRECT = 00040000;
    public static final int O_LARGEFILE = 00100000;
    public static final int O_DIRECTORY = 00200000;
    public static final int O_NOFOLLOW = 00400000;
    public static final int O_NOATIME = 01000000;
    public static final int O_CLOEXEC = 02000000;
    public static final int O_TMPFILE = 020000000;

    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    private static FileDescriptor getFd(int fd) {
        FileDescriptor f = new FileDescriptor();
        SharedSecrets.getJavaIOFileDescriptorAccess().set(f, fd);
        return f;
    }

    private static FileChannel getOutputChannel(int fd) {
        return new FileOutputStream(getFd(fd)).getChannel();
    }

    @TruffleBoundary
    public static long lseek(int fd, long offset, int whence) {
        FileDescriptor f = getFd(fd);
        FileInputStream in = new FileInputStream(f);
        FileChannel chan = in.getChannel();
        try {
            long pos;
            switch (whence) {
                case SEEK_SET:
                    pos = offset;
                    break;
                case SEEK_CUR:
                    pos = chan.position() + offset;
                    break;
                case SEEK_END:
                    pos = chan.size() + offset;
                    break;
                default:
                    return -LLVMAMD64Error.EINVAL;
            }
            if (offset > 0 && pos < 0) {
                return -LLVMAMD64Error.EOVERFLOW;
            }
            if (pos < 0) {
                return -LLVMAMD64Error.EINVAL;
            }
            chan.position(pos);
            return pos;
        } catch (IOException e) {
            return -LLVMAMD64Error.EBADF;
        }
    }

    @TruffleBoundary
    public static int ftruncate(int fd, long length) {
        FileChannel chan = getOutputChannel(fd);
        try {
            chan.truncate(length);
        } catch (IOException e) {
            return -LLVMAMD64Error.EBADF;
        }
        return 0;
    }
}
