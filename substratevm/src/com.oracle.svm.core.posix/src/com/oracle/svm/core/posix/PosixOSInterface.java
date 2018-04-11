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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.posix.headers.Errno.errno;
import static com.oracle.svm.core.posix.headers.Errno.strerror;

import java.io.FileDescriptor;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.os.OSInterface;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.UnistdNoTransitions;
import com.oracle.svm.core.snippets.KnownIntrinsics;

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public abstract class PosixOSInterface extends OSInterface {

    /**
     * An explicit default constructor.
     *
     * See the note on {@linkplain OSInterface#OSInterface}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixOSInterface() {
        super();
    }

    /**
     * This contains simply the signal names and numbers defined by POSIX <i>and that are
     * architecture-independent</i>. See signal(2) or signal(7) or POSIX.1-1990.
     */
    public enum Signal {
        unused0,
        HUP {

            {
                assert getValue() == 1;
            }
        },
        INT {

            {
                assert getValue() == 2;
            }
        },
        QUIT {

            {
                assert getValue() == 3;
            }
        },
        ILL {

            {
                assert getValue() == 4;
            }
        },
        unused5,
        ABRT {

            {
                assert getValue() == 6;
            }
        },
        unused7,
        FPE {

            {
                assert getValue() == 8;
            }
        },
        KILL {

            {
                assert getValue() == 9;
            }
        },
        unused10,
        SEGV {

            {
                assert getValue() == 11;
            }
        },
        unused12,
        PIPE {

            {
                assert getValue() == 13;
            }
        },
        ALRM {

            {
                assert getValue() == 14;
            }
        },
        TERM {

            {
                assert getValue() == 15;
            }
        };

        public int getValue() {
            /*
             * Can't refer to TERM.ordinal() symbolically during class initialization, when we will
             * get called from the assertions above, so just write '15'.
             */
            assert ordinal() <= 15;
            return ordinal();
        }

        public static Signal fromValue(int value) {
            assert value <= TERM.ordinal();
            return values()[value];
        }

        /**
         * Return the exit status that a program terminating with this signal will return on a POSIX
         * platform.
         * <p>
         * FIXME: is this consistently defined across all POSIXes and arches?
         */
        public int toExitStatus() {
            return 128 + getValue();
        }

        /*
         * TODO: could support the arch-dependent values by taking an extra argument describing the
         * arch.
         */
    }

    @Override
    public void exit(int status) {
        JavaMainSupport.executeShutdownHooks();
        LibC.exit(status);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void abort() {
        LibC.abort();
    }

    @TargetClass(java.io.FileDescriptor.class)
    public static final class Target_java_io_FileDescriptor {

        @Alias int fd;
    }

    public static final class Util_java_io_FileDescriptor {

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int getFD(FileDescriptor descriptor) {
            return KnownIntrinsics.unsafeCast(descriptor, Target_java_io_FileDescriptor.class).fd;
        }

        public static void setFD(FileDescriptor descriptor, int fd) {
            KnownIntrinsics.unsafeCast(descriptor, Target_java_io_FileDescriptor.class).fd = fd;
        }
    }

    @Override
    public FileDescriptor redirectStandardIO(StandardIO stdio, int nativeFD) {
        if (nativeFD == -1) {
            return null; // for now. Throwing an exception would be better
        }
        int currentNativeFD = Util_java_io_FileDescriptor.getFD(stdio.fd);
        if (currentNativeFD == nativeFD) {
            return null;
        }
        FileDescriptor fd = new FileDescriptor();
        Util_java_io_FileDescriptor.setFD(fd, currentNativeFD);
        Util_java_io_FileDescriptor.setFD(stdio.fd, nativeFD);
        return fd;
    }

    // @formatter:off
    //    @Override
    //    protected void writeBytes0(FileDescriptor descriptor, byte[] bytes, int off, int len, boolean append) throws IOException {
    //        try (PinnedObject bytesPin = PinnedObject.open(bytes)) {
    //            CCharPointer curBuf = bytesPin.addressOfArrayElement(off);
    //            Unsigned curLen = WordFactory.unsigned(len);
    //            while (curLen.notEqual(0)) {
    //                int fd = FileDescriptorAlias.getFD(descriptor);
    //                if (fd == -1) {
    //                    throw new IOException("Stream Closed");
    //                }
    //
    //                Signed n = write(fd, curBuf, curLen);
    //
    //                if (n.equal(-1)) {
    //                    throw new IOException(lastErrorString("Write error"));
    //                }
    //                curBuf = curBuf.addressOf(n);
    //                curLen = curLen.subtract((Unsigned) n);
    //            }
    //        }
    //    }
    //@formatter:on

    /*
     * TODO Temporarily removed the pinning of 'bytes' to allow it to be region allocation. See
     * above.
     */
    @Override
    protected boolean writeBytes0(FileDescriptor descriptor, byte[] bytes, int off, int len, boolean append) {
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(bytes);
        final UnsignedWord offsetOfArrayElement = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), off);
        final UnsignedWord length = WordFactory.unsigned(len);
        return writeBytes0Uninterruptibly(descriptor, bytes, length, offsetOfArrayElement);
    }

    @Uninterruptible(reason = "byte[] accessed without pinning")
    private boolean writeBytes0Uninterruptibly(FileDescriptor descriptor, byte[] bytes, UnsignedWord length, UnsignedWord offsetOfArrayElement) {
        final Pointer addressOfObject = Word.objectToUntrackedPointer(bytes);
        final CCharPointer bytePointer = (CCharPointer) addressOfObject.add(offsetOfArrayElement);
        return writeBytesUninterruptibly(descriptor, bytePointer, length);
    }

    @Override
    @Uninterruptible(reason = "Bytes might be from an Object.")
    public boolean writeBytesUninterruptibly(FileDescriptor descriptor, CCharPointer bytes, UnsignedWord length) {
        CCharPointer curBuf = bytes;
        UnsignedWord curLen = length;
        while (curLen.notEqual(0)) {
            int fd = Util_java_io_FileDescriptor.getFD(descriptor);
            if (fd == -1) {
                return false;
            }

            SignedWord n = UnistdNoTransitions.write(fd, curBuf, curLen);

            if (n.equal(-1)) {
                return false;
            }
            curBuf = curBuf.addressOf(n);
            curLen = curLen.subtract((UnsignedWord) n);
        }
        return true;
    }

    @Override
    public boolean flush(FileDescriptor descriptor) {
        int fd = Util_java_io_FileDescriptor.getFD(descriptor);
        return Unistd.fsync(fd) == 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean flushUninterruptibly(FileDescriptor descriptor) {
        int fd = Util_java_io_FileDescriptor.getFD(descriptor);
        return UnistdNoTransitions.fsync(fd) == 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public long lseekCurUninterruptibly(FileDescriptor descriptor, long offset) {
        int fd = Util_java_io_FileDescriptor.getFD(descriptor);
        return UnistdNoTransitions.lseek(fd, WordFactory.signed(offset), Unistd.SEEK_CUR()).rawValue();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public long lseekSetUninterruptibly(FileDescriptor descriptor, long offset) {
        int fd = Util_java_io_FileDescriptor.getFD(descriptor);
        return UnistdNoTransitions.lseek(fd, WordFactory.signed(offset), Unistd.SEEK_SET()).rawValue();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public long lseekEndUninterruptibly(FileDescriptor descriptor, long offset) {
        int fd = Util_java_io_FileDescriptor.getFD(descriptor);
        return UnistdNoTransitions.lseek(fd, WordFactory.signed(offset), Unistd.SEEK_END()).rawValue();
    }

    protected static String lastErrorString(String defaultMsg) {
        String result = "";
        int errno = errno();
        if (errno != 0) {
            result = CTypeConversion.toJavaString(strerror(errno));
        }
        return result.length() != 0 ? result : defaultMsg;
    }
}
