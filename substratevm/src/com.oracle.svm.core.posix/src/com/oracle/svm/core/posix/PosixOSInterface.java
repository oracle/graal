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
import static com.oracle.svm.core.posix.headers.Mman.MAP_ANON;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.PROT_EXEC;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;
import static com.oracle.svm.core.posix.headers.Mman.mmap;
import static com.oracle.svm.core.posix.headers.Mman.munmap;

import java.io.FileDescriptor;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
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
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.OSInterface;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.UnistdNoTransitions;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;

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

    @Override
    public Pointer allocateVirtualMemory(UnsignedWord size, boolean executable) {
        trackVirtualMemory(size);
        int protect = PROT_READ() | PROT_WRITE() | (executable ? PROT_EXEC() : 0);
        int flags = MAP_ANON() | MAP_PRIVATE();
        final Pointer result = mmap(WordFactory.nullPointer(), size, protect, flags, -1, 0);
        if (result.equal(MAP_FAILED())) {
            // Turn the mmap failure into a null Pointer.
            return WordFactory.nullPointer();
        }
        return result;
    }

    @Override
    public boolean freeVirtualMemory(PointerBase start, UnsignedWord size) {
        untrackVirtualMemory(size);
        final int unmapResult = munmap(start, size);
        // Turn the munmap result into a boolean.
        return (unmapResult == 0);
    }

    /**
     * Allocate the requested amount of virtual memory at the requested alignment.
     *
     * @return A Pointer to the aligned memory, or a null Pointer.
     */
    @Override
    public Pointer allocateVirtualMemoryAligned(UnsignedWord size, UnsignedWord alignment) {
        // This happens in stages:
        // (1) Reserve a container that is large enough for the requested size *and* the alignment.
        // (2) Locate the result at the requested alignment within the container.
        // (3) Clean up any over-allocated prefix and suffix pages.

        // All communication with mmap and munmap happen in terms of page_sized objects.
        final UnsignedWord pageSize = getPageSize();
        // (1) Reserve a container that is large enough for the requested size *and* the alignment.
        // - The container occupies the open-right interval [containerStart .. containerEnd).
        // - This will be too big, but I'll give back the extra later.
        final UnsignedWord containerSize = alignment.add(size);
        final UnsignedWord pagedContainerSize = UnsignedUtils.roundUp(containerSize, pageSize);
        final Pointer containerStart = allocateVirtualMemory(pagedContainerSize, false);
        if (containerStart.isNull()) {
            // No exception is needed: this is just a failure to reserve the virtual address space.
            return WordFactory.nullPointer();
        }
        final Pointer containerEnd = containerStart.add(pagedContainerSize);
        // (2) Locate the result at the requested alignment within the container.
        // - The result occupies [start .. end).
        final Pointer start = PointerUtils.roundUp(containerStart, alignment);
        final Pointer end = start.add(size);
        if (virtualMemoryVerboseDebugging) {
            Log.log().string("allocateVirtualMemoryAligned(size: ").unsigned(size).string(" ").hex(size).string(", alignment: ").unsigned(alignment).string(" ").hex(alignment).string(")").newline();
            Log.log().string("  container:   [").hex(containerStart).string(" .. ").hex(containerEnd).string(")").newline();
            Log.log().string("  result:      [").hex(start).string(" .. ").hex(end).string(")").newline();
        }
        // (3) Clean up any over-allocated prefix and suffix pages.
        // - The prefix occupies [containerStart .. pagedStart).
        final Pointer pagedStart = PointerUtils.roundDown(start, pageSize);
        final Pointer prefixStart = containerStart;
        final Pointer prefixEnd = pagedStart;
        final UnsignedWord prefixSize = prefixEnd.subtract(prefixStart);
        if (prefixSize.aboveOrEqual(pageSize)) {
            if (virtualMemoryVerboseDebugging) {
                Log.log().string("  prefix:      [").hex(prefixStart).string(" .. ").hex(prefixEnd).string(")").newline();
            }
            final boolean prefixUnmap = freeVirtualMemory(prefixStart, prefixSize);
            if (!prefixUnmap) {
                // Throwing an exception would be better.
                // If this unmap fails, I will have reserved virtual address space
                // that I won't be able to give back.
                return WordFactory.nullPointer();
            }
        }
        // - The suffix occupies [pagedEnd .. containerEnd).
        final Pointer pagedEnd = PointerUtils.roundUp(end, pageSize);
        final Pointer suffixStart = pagedEnd;
        final Pointer suffixEnd = containerEnd;
        final UnsignedWord suffixSize = suffixEnd.subtract(suffixStart);
        if (suffixSize.aboveOrEqual(pageSize)) {
            if (virtualMemoryVerboseDebugging) {
                Log.log().string("  suffix:      [").hex(suffixStart).string(" .. ").hex(suffixEnd).string(")").newline();
            }
            final boolean suffixUnmap = freeVirtualMemory(suffixStart, suffixSize);
            if (!suffixUnmap) {
                // Throwing an exception would be better.
                // If this unmap fails, I will have reserved virtual address space
                // that I won't be able to give back.
                return WordFactory.nullPointer();
            }
        }
        return start;
    }

    @Override
    public boolean freeVirtualMemoryAligned(PointerBase start, UnsignedWord size, UnsignedWord alignment) {
        final UnsignedWord pageSize = getPageSize();
        // Re-discover the paged-aligned ends of the memory region.
        final Pointer end = ((Pointer) start).add(size);
        final Pointer pagedStart = PointerUtils.roundDown(start, pageSize);
        final Pointer pagedEnd = PointerUtils.roundUp(end, pageSize);
        final UnsignedWord pagedSize = pagedEnd.subtract(pagedStart);
        // Return that virtual address space to the operating system.
        return freeVirtualMemory(pagedStart, pagedSize);
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

    protected void trackVirtualMemory(UnsignedWord size) {
        tracker.track(size);
    }

    protected void untrackVirtualMemory(UnsignedWord size) {
        tracker.untrack(size);
    }

    // Immutable state.
    private final VirtualMemoryTracker tracker = new VirtualMemoryTracker();
    // Verbose debugging.
    private static final boolean virtualMemoryVerboseDebugging = false;

    /**
     * Make sure that allocation does not go completely wild.
     *
     * These methods do not allocate a new OutOfMemoryError instance and throw it. Rather they work
     * with {@linkplain SnippetRuntime#THROW_CACHED_OUT_OF_MEMORY_ERROR} to throw a cached
     * OutOfMemoryError instance.
     */
    protected static class VirtualMemoryTracker {

        // Mutable state.
        private UnsignedWord totalAllocated;

        protected VirtualMemoryTracker() {
            this.totalAllocated = WordFactory.zero();
        }

        public void track(UnsignedWord size) {
            totalAllocated = totalAllocated.add(size);
        }

        public void untrack(UnsignedWord size) {
            totalAllocated = totalAllocated.subtract(size);
        }
    }

}
