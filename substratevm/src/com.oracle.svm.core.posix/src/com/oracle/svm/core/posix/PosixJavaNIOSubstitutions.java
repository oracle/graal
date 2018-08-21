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
package com.oracle.svm.core.posix;

import static com.oracle.svm.core.posix.headers.Dirent.opendir;
import static com.oracle.svm.core.posix.headers.Dirent.readdir_r;
import static com.oracle.svm.core.posix.headers.Dlfcn.RTLD_DEFAULT;
import static com.oracle.svm.core.posix.headers.Errno.EACCES;
import static com.oracle.svm.core.posix.headers.Errno.EAGAIN;
import static com.oracle.svm.core.posix.headers.Errno.ECANCELED;
import static com.oracle.svm.core.posix.headers.Errno.EINTR;
import static com.oracle.svm.core.posix.headers.Errno.EINVAL;
import static com.oracle.svm.core.posix.headers.Errno.ENOENT;
import static com.oracle.svm.core.posix.headers.Errno.ENOMEM;
import static com.oracle.svm.core.posix.headers.Errno.ENOTCONN;
import static com.oracle.svm.core.posix.headers.Errno.ENOTSOCK;
import static com.oracle.svm.core.posix.headers.Errno.ENOTSUP;
import static com.oracle.svm.core.posix.headers.Errno.EOPNOTSUPP;
import static com.oracle.svm.core.posix.headers.Errno.ERANGE;
import static com.oracle.svm.core.posix.headers.Errno.ESRCH;
import static com.oracle.svm.core.posix.headers.Errno.errno;
import static com.oracle.svm.core.posix.headers.Fcntl.F_GETFL;
import static com.oracle.svm.core.posix.headers.Fcntl.F_RDLCK;
import static com.oracle.svm.core.posix.headers.Fcntl.F_SETFL;
import static com.oracle.svm.core.posix.headers.Fcntl.F_SETLK;
import static com.oracle.svm.core.posix.headers.Fcntl.F_SETLKW;
import static com.oracle.svm.core.posix.headers.Fcntl.F_UNLCK;
import static com.oracle.svm.core.posix.headers.Fcntl.F_WRLCK;
import static com.oracle.svm.core.posix.headers.Fcntl.O_NONBLOCK;
import static com.oracle.svm.core.posix.headers.Fcntl.fcntl;
import static com.oracle.svm.core.posix.headers.Fcntl.open;
import static com.oracle.svm.core.posix.headers.Grp.getgrgid_r;
import static com.oracle.svm.core.posix.headers.Grp.getgrnam_r;
import static com.oracle.svm.core.posix.headers.Limits.PATH_MAX;
import static com.oracle.svm.core.posix.headers.Mman.MAP_FAILED;
import static com.oracle.svm.core.posix.headers.Mman.MAP_PRIVATE;
import static com.oracle.svm.core.posix.headers.Mman.MAP_SHARED;
import static com.oracle.svm.core.posix.headers.Mman.PROT_READ;
import static com.oracle.svm.core.posix.headers.Mman.PROT_WRITE;
import static com.oracle.svm.core.posix.headers.Mman.mmap;
import static com.oracle.svm.core.posix.headers.Mman.munmap;
import static com.oracle.svm.core.posix.headers.Pwd.getpwnam_r;
import static com.oracle.svm.core.posix.headers.Pwd.getpwuid_r;
import static com.oracle.svm.core.posix.headers.Resource.RLIMIT_NOFILE;
import static com.oracle.svm.core.posix.headers.Resource.getrlimit;
import static com.oracle.svm.core.posix.headers.Stat.chmod;
import static com.oracle.svm.core.posix.headers.Stat.fstat;
import static com.oracle.svm.core.posix.headers.Stat.lstat;
import static com.oracle.svm.core.posix.headers.Stat.mkdir;
import static com.oracle.svm.core.posix.headers.Stat.mknod;
import static com.oracle.svm.core.posix.headers.Stdio.EOF;
import static com.oracle.svm.core.posix.headers.Stdio.fopen;
import static com.oracle.svm.core.posix.headers.Uio.readv;
import static com.oracle.svm.core.posix.headers.Uio.writev;
import static com.oracle.svm.core.posix.headers.Unistd.SEEK_CUR;
import static com.oracle.svm.core.posix.headers.Unistd.SEEK_SET;
import static com.oracle.svm.core.posix.headers.Unistd._SC_GETGR_R_SIZE_MAX;
import static com.oracle.svm.core.posix.headers.Unistd._SC_GETPW_R_SIZE_MAX;
import static com.oracle.svm.core.posix.headers.Unistd._SC_IOV_MAX;
import static com.oracle.svm.core.posix.headers.Unistd.access;
import static com.oracle.svm.core.posix.headers.Unistd.chown;
import static com.oracle.svm.core.posix.headers.Unistd.close;
import static com.oracle.svm.core.posix.headers.Unistd.dup2;
import static com.oracle.svm.core.posix.headers.Unistd.fdatasync;
import static com.oracle.svm.core.posix.headers.Unistd.fsync;
import static com.oracle.svm.core.posix.headers.Unistd.ftruncate;
import static com.oracle.svm.core.posix.headers.Unistd.lchown;
import static com.oracle.svm.core.posix.headers.Unistd.link;
import static com.oracle.svm.core.posix.headers.Unistd.lseek;
import static com.oracle.svm.core.posix.headers.Unistd.pathconf;
import static com.oracle.svm.core.posix.headers.Unistd.pipe;
import static com.oracle.svm.core.posix.headers.Unistd.pread;
import static com.oracle.svm.core.posix.headers.Unistd.pwrite;
import static com.oracle.svm.core.posix.headers.Unistd.read;
import static com.oracle.svm.core.posix.headers.Unistd.readlink;
import static com.oracle.svm.core.posix.headers.Unistd.rmdir;
import static com.oracle.svm.core.posix.headers.Unistd.symlink;
import static com.oracle.svm.core.posix.headers.Unistd.sysconf;
import static com.oracle.svm.core.posix.headers.Unistd.unlink;
import static com.oracle.svm.core.posix.headers.Unistd.write;
import static com.oracle.svm.core.posix.headers.darwin.CoreFoundation.CFRelease;
import static com.oracle.svm.core.posix.headers.darwin.DarwinSendfile.sendfile;
import static com.oracle.svm.core.posix.headers.linux.LinuxSendfile.sendfile;
import static com.oracle.svm.core.posix.headers.linux.Mntent.getmntent_r;
import static com.oracle.svm.core.posix.headers.linux.Mntent.setmntent;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.spi.FileSystemProvider;
import java.util.function.Predicate;

import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.IsDefined;
import com.oracle.svm.core.posix.darwin.DarwinCoreFoundationUtils;
import com.oracle.svm.core.posix.headers.Dirent;
import com.oracle.svm.core.posix.headers.Dirent.DIR;
import com.oracle.svm.core.posix.headers.Dirent.dirent;
import com.oracle.svm.core.posix.headers.Dirent.direntPointer;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Fcntl;
import com.oracle.svm.core.posix.headers.Fcntl.flock;
import com.oracle.svm.core.posix.headers.Grp.group;
import com.oracle.svm.core.posix.headers.Grp.groupPointer;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.Poll;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Pwd.passwd;
import com.oracle.svm.core.posix.headers.Pwd.passwdPointer;
import com.oracle.svm.core.posix.headers.Resource.rlimit;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Signal.SignalDispatcher;
import com.oracle.svm.core.posix.headers.Socket;
import com.oracle.svm.core.posix.headers.Socket.sockaddr;
import com.oracle.svm.core.posix.headers.Stat;
import com.oracle.svm.core.posix.headers.Statvfs;
import com.oracle.svm.core.posix.headers.Stdio;
import com.oracle.svm.core.posix.headers.Stdio.FILE;
import com.oracle.svm.core.posix.headers.Stdlib;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Time.timeval;
import com.oracle.svm.core.posix.headers.Uio.iovec;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.darwin.CoreFoundation;
import com.oracle.svm.core.posix.headers.linux.Mntent;
import com.oracle.svm.core.posix.headers.linux.Mntent.mntent;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.vm.ci.meta.JavaKind;

public final class PosixJavaNIOSubstitutions {

    // Checkstyle: stop
    @TargetClass(className = "sun.nio.ch.IOStatus")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_IOStatus {
        @Alias @TargetElement(name = "EOF")//
        protected static int IOS_EOF;
        @Alias @TargetElement(name = "UNAVAILABLE")//
        protected static int IOS_UNAVAILABLE;
        @Alias @TargetElement(name = "INTERRUPTED")//
        protected static int IOS_INTERRUPTED;
        @Alias @TargetElement(name = "UNSUPPORTED")//
        protected static int IOS_UNSUPPORTED;
        @Alias @TargetElement(name = "THROWN")//
        protected static int IOS_THROWN;
        @Alias @TargetElement(name = "UNSUPPORTED_CASE")//
        protected static int IOS_UNSUPPORTED_CASE;
    }

    @TargetClass(className = "sun.nio.ch.FileDispatcher")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_FileDispatcher {
        @Alias @TargetElement(name = "NO_LOCK")//
        protected static int FD_NO_LOCK;
        @Alias @TargetElement(name = "LOCKED")//
        protected static int FD_LOCKED;
        @Alias @TargetElement(name = "RET_EX_LOCK")//
        protected static int FD_RET_EX_LOCK;
        @Alias @TargetElement(name = "INTERRUPTED")//
        protected static int FD_INTERRUPTED;
    }

    // Checkstyle: resume

    protected static IOException throwIOExceptionWithLastError(String defaultMsg) throws IOException {
        throw new IOException(PosixUtils.lastErrorString(defaultMsg));
    }

    protected static int handle(int rv, String msg) throws IOException {
        if (rv >= 0) {
            return rv;
        }
        if (errno() == EINTR()) {
            return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
        }
        throw throwIOExceptionWithLastError(msg);
    }

    protected static long handle(long rv, String msg) throws IOException {
        if (rv >= 0) {
            return rv;
        }
        if (errno() == EINTR()) {
            return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
        }
        throw throwIOExceptionWithLastError(msg);
    }

    protected static int convertReturnVal(WordBase n, boolean reading) throws IOException {
        return convertReturnVal((int) n.rawValue(), reading);
    }

    protected static int convertReturnVal(int n, boolean reading) throws IOException {
        if (n > 0) {
            /* Number of bytes written */
            return n;
        } else if (n == 0) {
            if (reading) {
                return Target_sun_nio_ch_IOStatus.IOS_EOF; /* EOF is -1 in javaland */
            } else {
                return 0;
            }
        } else if (errno() == EAGAIN()) {
            return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
        } else if (errno() == EINTR()) {
            return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
        } else {
            String msg = reading ? "Read failed" : "Write failed";
            throwIOExceptionWithLastError(msg);
            return Target_sun_nio_ch_IOStatus.IOS_THROWN;
        }
    }

    protected static long convertLongReturnVal(WordBase n, boolean reading) throws IOException {
        return convertLongReturnVal(n.rawValue(), reading);
    }

    protected static long convertLongReturnVal(long n, boolean reading) throws IOException {
        if (n > 0) {
            /* Number of bytes written */
            return n;
        } else if (n == 0) {
            if (reading) {
                return Target_sun_nio_ch_IOStatus.IOS_EOF; /* EOF is -1 in javaland */
            } else {
                return 0;
            }
        } else if (errno() == EAGAIN()) {
            return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
        } else if (errno() == EINTR()) {
            return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
        } else {
            String msg = reading ? "Read failed" : "Write failed";
            throwIOExceptionWithLastError(msg);
            return Target_sun_nio_ch_IOStatus.IOS_THROWN;
        }
    }

    protected static int fdval(FileDescriptor fdo) {
        return PosixUtils.getFD(fdo);
    }

    protected static <T extends PointerBase> T dlsym(PointerBase handle, String name) {
        try (CCharPointerHolder namePin = CTypeConversion.toCString(name)) {
            CCharPointer namePtr = namePin.get();
            return Dlfcn.dlsym(handle, namePtr);
        }
    }

    @TargetClass(className = "sun.nio.fs.Cancellable")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_Cancellable {
        @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Manual)//
        private long pollingAddress;
    }

    /** See the comments on {@link sun.nio.ch.NativeThread}. */
    /* Translated from: jdk/src/solaris/native/sun/nio/ch/NativeThread.c?v=Java_1.8.0_40_b10 */
    @TargetClass(className = "sun.nio.ch.NativeThread")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_NativeThread {
        /* { Do not re-format commented code: @formatter:off */

        //  077 JNIEXPORT jlong JNICALL
        //  078 Java_sun_nio_ch_NativeThread_current(JNIEnv *env, jclass cl)
        //  079 {
        @Substitute
        static long current() {
            if (SubstrateOptions.MultiThreaded.getValue()) {
                //  080 #ifdef __solaris__
                //  081     return (jlong)thr_self();
                //  082 #else
                //  083     return (jlong)pthread_self();
                //  084 #endif
                return Pthread.pthread_self().rawValue();
            } else {
                return -1L;
            }
        }

        // 087 JNIEXPORT void JNICALL
        // 088 Java_sun_nio_ch_NativeThread_signal(JNIEnv *env, jclass cl, jlong thread)
        // 089 {
        @Substitute
        private static void signal(long thread) throws IOException {
            if (SubstrateOptions.MultiThreaded.getValue()) {
                Util_sun_nio_ch_NativeThread.ensureInitialized();
                // 090     int ret;
                int ret;
                // 091 #ifdef __solaris__
                // 092     ret = thr_kill((thread_t)thread, INTERRUPT_SIGNAL);
                // 093 #else
                // 094     ret = pthread_kill((pthread_t)thread, INTERRUPT_SIGNAL);
                ret = Pthread.pthread_kill(WordFactory.pointer(thread), Util_sun_nio_ch_NativeThread.INTERRUPT_SIGNAL);
                // 095 #endif
                // 096     if (ret != 0)
                if (ret != 0) {
                    // 097         JNU_ThrowIOExceptionWithLastError(env, "Thread signal failed");
                    throw new IOException("Thread signal failed");
                }
            }
        }

        /** See {@link Util_sun_nio_ch_NativeThread#ensureInitialized()}. */
        @Substitute
        private static void init() {
            throw new InternalError("init() is only called from static initializers, so not reachable in Substrate VM");
        }

        /* } Do not re-format commented code: @formatter:on */
    }

    static final class Util_sun_nio_ch_NativeThread {

        /**
         * The initialization of {@link sun.nio.ch.NativeThread} is in a static block that gets run
         * during image building. I need to initialize the signal handler at run time. I am not
         * worried about races, as they will all register the same signal handler.
         */
        static boolean initialized = false;

        /* { Do not re-format commented code: @formatter:off */
        // 035 #ifdef __linux__
        // 036   #include <pthread.h>
        // 037   #include <sys/signal.h>
        // 038   /* Also defined in net/linux_close.c */
        // 039   #define INTERRUPT_SIGNAL (__SIGRTMAX - 2)
        // 040 #elif __solaris__
        // 041   #include <thread.h>
        // 042   #include <signal.h>
        // 043   #define INTERRUPT_SIGNAL (SIGRTMAX - 2)
        // 044 #elif _ALLBSD_SOURCE
        // 045   #include <pthread.h>
        // 046   #include <signal.h>
        // 047   /* Also defined in net/bsd_close.c */
        // 048   #define INTERRUPT_SIGNAL SIGIO
        // 049 #else
        // 050   #error "missing platform-specific definition here"
        // 051 #endif
        static final Signal.SignalEnum INTERRUPT_SIGNAL = Signal.SignalEnum.SIGIO;
        /* } Do not re-format commented code: @formatter:on */

        /* Translated from jdk/src/solaris/native/sun/nio/ch/NativeThread.c?v=Java_1.8.0_40_b10. */
        // 053 static void
        // 054 nullHandler(int sig)
        // 055 {
        // 056 }
        @CEntryPoint
        @CEntryPointOptions(prologue = NoPrologue.class, epilogue = NoEpilogue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
        @Uninterruptible(reason = "Can not check for safepoints because I am running on a borrowed thread.")
        private static void nullHandler(@SuppressWarnings("unused") int signalNumber) {
        }

        /** The address of the null signal handler. */
        private static final CEntryPointLiteral<SignalDispatcher> nullDispatcher = CEntryPointLiteral.create(Util_sun_nio_ch_NativeThread.class, "nullHandler", int.class);

        static void ensureInitialized() throws IOException {
            if (!initialized) {
                /* { Do not re-format commented code: @formatter:off */
                // 061     /* Install the null handler for INTERRUPT_SIGNAL.  This might overwrite the
                // 062      * handler previously installed by java/net/linux_close.c, but that's okay
                // 063      * since neither handler actually does anything.  We install our own
                // 064      * handler here simply out of paranoia; ultimately the two mechanisms
                // 065      * should somehow be unified, perhaps within the VM.
                // 066      */
                // 067
                // 068     sigset_t ss;
                // 069     struct sigaction sa, osa;
                Signal.sigaction saPointer = StackValue.get(Signal.sigaction.class);
                Signal.sigaction osaPointer = StackValue.get(Signal.sigaction.class);
                // 070     sa.sa_handler = nullHandler;
                saPointer.sa_handler(Util_sun_nio_ch_NativeThread.nullDispatcher.getFunctionPointer());
                // 071     sa.sa_flags = 0;
                saPointer.sa_flags(0);
                // 072     sigemptyset(&sa.sa_mask);
                Signal.sigemptyset(saPointer.sa_mask());
                // 073     if (sigaction(INTERRUPT_SIGNAL, &sa, &osa) < 0)
                if (Signal.sigaction(INTERRUPT_SIGNAL, saPointer, osaPointer) < 0) {
                    // 074         JNU_ThrowIOExceptionWithLastError(env, "sigaction");
                    throw new IOException("sigaction");
                }
                /* } Do not re-format commented code: @formatter:on */
                initialized = true;
            }
        }
    }

    /*
     * Converted from JDK 7 update 40 C source file: src/solaris/native/sun/nio/ch/IOUtil.c
     */

    @TargetClass(className = "sun.nio.ch.IOUtil")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_IOUtil {

        @Substitute
        @SuppressWarnings("unused")
        private static boolean randomBytes(byte[] someBytes) {
            throw new UnsupportedOperationException();
        }

        @Substitute
        private static int fdVal(FileDescriptor fd) {
            return PosixUtils.getFD(fd);
        }

        @Substitute
        private static void setfdVal(FileDescriptor fd, int value) {
            PosixUtils.setFD(fd, value);
        }

        @Substitute
        private static void configureBlocking(FileDescriptor fdo, boolean blocking) throws IOException {
            if (Util_sun_nio_ch_IOUtil.configureBlocking(fdval(fdo), blocking) < 0) {
                throwIOExceptionWithLastError("Configure blocking failed");
            }
        }

        /**
         * Returns two file descriptors for a pipe encoded in a long. The read end of the pipe is
         * returned in the high 32 bits, while the write end is returned in the low 32 bits.
         */
        @Substitute
        private static long makePipe(boolean blocking) throws IOException {
            CIntPointer fd = StackValue.get(2, CIntPointer.class);

            if (pipe(fd) < 0) {
                throwIOExceptionWithLastError("Pipe failed");
                return 0;
            }
            if (blocking == false) {
                if ((Util_sun_nio_ch_IOUtil.configureBlocking(fd.read(0), false) < 0) || (Util_sun_nio_ch_IOUtil.configureBlocking(fd.read(1), false) < 0)) {
                    throwIOExceptionWithLastError("Configure blocking failed");
                    close(fd.read(0));
                    close(fd.read(1));
                    return 0;
                }
            }
            return ((long) fd.read(0) << 32) | fd.read(1);
        }

        @Substitute
        private static boolean drain(int fd) throws IOException {
            final int bufsize = 128;
            CCharPointer buf = StackValue.get(bufsize, CCharPointer.class);
            int tn = 0;

            for (;;) {
                int n = (int) read(fd, buf, WordFactory.unsigned(bufsize)).rawValue();
                tn += n;
                if ((n < 0) && (errno() != EAGAIN())) {
                    throwIOExceptionWithLastError("Drain");
                }
                if (n == bufsize) {
                    continue;
                }
                return tn > 0;
            }
        }

        /* open/src/java.base/unix/native/libnio/ch/IOUtil.c */
        @Substitute
        @TargetElement(onlyWith = JDK9OrLater.class)
        // 131 JNIEXPORT jint JNICALL
        // 132 Java_sun_nio_ch_IOUtil_drain1(JNIEnv *env, jclass cl, jint fd)
        // 133 {
        static int drain1(int fd) throws IOException {
            // 134 int res;
            int res;
            // 135 char buf[1];
            CCharPointer bufPointer = StackValue.get(1, CCharPointer.class);
            // 136
            // 137 res = read(fd, buf, 1);
            res = (int) Unistd.read(fd, bufPointer, WordFactory.unsigned(1)).rawValue();
            // 138 if (res < 0) {
            if (res < 0) {
                // 139 if (errno == EAGAIN || errno == EWOULDBLOCK) {
                if (Errno.errno() == Errno.EAGAIN() || Errno.errno() == Errno.EWOULDBLOCK()) {
                    // 140 res = 0;
                    res = 0;
                    // 141 } else if (errno == EINTR) {
                } else if (Errno.errno() == Errno.EINTR()) {
                    // 142 return IOS_INTERRUPTED;
                    return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                    // 143 } else {
                } else {
                    // 144 JNU_ThrowIOExceptionWithLastError(env, "read");
                    throw throwIOExceptionWithLastError("read");
                    // 145 return IOS_THROWN;
                    /* Unreachable! */
                }
            }
            // 148 return res;
            return res;
        }

        @Substitute
        private static int iovMax() {
            long iovmax = sysconf(_SC_IOV_MAX());
            if (iovmax == -1) {
                iovmax = 16;
            }
            return (int) iovmax;
        }

        @Substitute
        private static int fdLimit() throws IOException {
            rlimit rlp = StackValue.get(rlimit.class);
            if (getrlimit(RLIMIT_NOFILE(), rlp) < 0) {
                throw throwIOExceptionWithLastError("getrlimit failed");
            }
            if (rlp.rlim_max() < 0 || rlp.rlim_max() > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else {
                return (int) rlp.rlim_max();
            }
        }
    }

    static final class Util_sun_nio_ch_IOUtil {
        static int configureBlocking(int fd, boolean blocking) {
            int flags = fcntl(fd, F_GETFL());
            int newflags = blocking ? (flags & ~O_NONBLOCK()) : (flags | O_NONBLOCK());

            return (flags == newflags) ? 0 : fcntl(fd, F_SETFL(), newflags);
        }
    }

    /** Translations of src/solaris/native/sun/nio/ch/Net.c?v=Java_1.8.0_40_b10. */
    @TargetClass(className = "sun.nio.ch.Net")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_Net {

    /* Do not re-format commented-out code: @formatter:off */
        /* Allow methods with non-standard names: Checkstyle: stop */

        @Substitute
        // 333 JNIEXPORT jint JNICALL
        // 334 Java_sun_nio_ch_Net_connect0(JNIEnv *env, jclass clazz, jboolean preferIPv6,
        // 335                              jobject fdo, jobject iao, jint port)
        // 336 {
        static int connect0(boolean preferIPv6, FileDescriptor fdo, InetAddress iao, int port) throws IOException {
            // 337     SOCKADDR sa;
            sockaddr sa_Pointer = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 338     int sa_len = SOCKADDR_LEN;
            CIntPointer sa_len_Pointer = StackValue.get(CIntPointer.class);
            sa_len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 339     int rv;
            int rv;
            // 341     if (NET_InetAddressToSockaddr(env, iao, port, (struct sockaddr *) &sa,
            // 342                                   &sa_len, preferIPv6) != 0)
            // 343     {
            if (JavaNetNetUtilMD.NET_InetAddressToSockaddr(iao, port, sa_Pointer, sa_len_Pointer, preferIPv6) != 0) {
                // 344       return IOS_THROWN;
                return Target_sun_nio_ch_IOStatus.IOS_THROWN;
            }
            // 347     rv = connect(fdval(env, fdo), (struct sockaddr *)&sa, sa_len);
            rv = Socket.connect(PosixJavaNIOSubstitutions.fdval(fdo), sa_Pointer, sa_len_Pointer.read());
            // 348     if (rv != 0) {
            if (rv != 0) {
                // 349         if (errno == EINPROGRESS) {
                if (Errno.errno() == Errno.EINPROGRESS()) {
                    // 350             return IOS_UNAVAILABLE;
                    return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
                    // 351         } else if (errno == EINTR) {
                } else if (Errno.errno() == Errno.EINTR()) {
                    // 352             return IOS_INTERRUPTED;
                    return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                }
            // 354         return handleSocketError(env, errno);
                return Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
            }
            // 356     return 1;
            return 1;
        }

        @Substitute
        // 200 JNIEXPORT jboolean JNICALL
        // 201 Java_sun_nio_ch_Net_isIPv6Available0(JNIEnv* env, jclass cl)
        // 202 {
        static boolean isIPv6Available0() {
            // 203     return (ipv6_available()) ? JNI_TRUE : JNI_FALSE;
            return JavaNetNetUtil.ipv6_available();
        }

        @Substitute
        // 393 JNIEXPORT jobject JNICALL
        // 394 Java_sun_nio_ch_Net_localInetAddress(JNIEnv *env, jclass clazz, jobject fdo)
        // 395 {
        static InetAddress localInetAddress(FileDescriptor fdo) throws IOException {
            // 396     SOCKADDR sa;
            sockaddr sa_Pointer = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 397     socklen_t sa_len = SOCKADDR_LEN;
            CIntPointer sa_len_Pointer = StackValue.get(CIntPointer.class);
            sa_len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 398     int port;
            CIntPointer port_Pointer = StackValue.get(CIntPointer.class);
            // 399     if (getsockname(fdval(env, fdo), (struct sockaddr *)&sa, &sa_len) < 0) {
            if (Socket.getsockname(PosixJavaNIOSubstitutions.fdval(fdo), sa_Pointer, sa_len_Pointer) < 0) {
                /* TODO: Assuming #undef_ALLBSD_SOURCE */
                // 400 #ifdef _ALLBSD_SOURCE
                // 401         /*
                // 402          * XXXBSD:
                // 403          * ECONNRESET is specific to the BSDs. We can not return an error,
                // 404          * as the calling Java code with raise a java.lang.Error with the expectation
                // 405          * that getsockname() will never fail. According to the Single UNIX Specification,
                // 406          * it shouldn't fail. As such, we just fill in generic Linux-compatible values.
                // 407          */
                // 408         if (errno == ECONNRESET) {
                // 409             struct sockaddr_in *sin;
                // 410             sin = (struct sockaddr_in *) &sa;
                // 411             bzero(sin, sizeof(*sin));
                // 412             sin->sin_len  = sizeof(struct sockaddr_in);
                // 413             sin->sin_family = AF_INET;
                // 414             sin->sin_port = htonl(0);
                // 415             sin->sin_addr.s_addr = INADDR_ANY;
                // 416         } else {
                // 417             handleSocketError(env, errno);
                // 418             return NULL;
                // 419         }
                // 420 #else /* _ALLBSD_SOURCE */
                // 421         handleSocketError(env, errno);
                Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
                // 422         return NULL;
                return null;
                // 423 #endif /* _ALLBSD_SOURCE */
            }
            // 425     return NET_SockaddrToInetAddress(env, (struct sockaddr *)&sa, &port);
            return JavaNetNetUtil.NET_SockaddrToInetAddress(sa_Pointer, port_Pointer);
        }

        // 359 JNIEXPORT jint JNICALL
        // 360 Java_sun_nio_ch_Net_localPort(JNIEnv *env, jclass clazz, jobject fdo)
        // 361 {
        @Substitute
        static int localPort(FileDescriptor fdo) throws IOException {
            // 362 SOCKADDR sa;
            sockaddr sa_Pointer = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 363 socklen_t sa_len = SOCKADDR_LEN;
            CIntPointer sa_len_Pointer = StackValue.get(CIntPointer.class);
            sa_len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 364 if (getsockname(fdval(env, fdo), (struct sockaddr *)&sa, &sa_len) < 0) {
            if (Socket.getsockname(PosixJavaNIOSubstitutions.fdval(fdo), sa_Pointer, sa_len_Pointer) < 0) {
                // TODO: Assuming #undef _ALLBSD_SOURCE
                // 365 #ifdef _ALLBSD_SOURCE
                // 366         /*
                // 367          * XXXBSD:
                // 368          * ECONNRESET is specific to the BSDs. We can not return an error,
                // 369          * as the calling Java code with raise a java.lang.Error given the expectation
                // 370          * that getsockname() will never fail. According to the Single UNIX Specification,
                // 371          * it shouldn't fail. As such, we just fill in generic Linux-compatible values.
                // 372          */
                // 373         if (errno == ECONNRESET) {
                // 374             struct sockaddr_in *sin;
                // 375             sin = (struct sockaddr_in *) &sa;
                // 376             bzero(sin, sizeof(*sin));
                // 377             sin->sin_len  = sizeof(struct sockaddr_in);
                // 378             sin->sin_family = AF_INET;
                // 379             sin->sin_port = htonl(0);
                // 380             sin->sin_addr.s_addr = INADDR_ANY;
                // 381         } else {
                // 382             handleSocketError(env, errno);
                // 383             return -1;
                // 384         }
                // 385 #else /* _ALLBSD_SOURCE */
                // 386         handleSocketError(env, errno);
                Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
                // 387         return -1;
                return -1;
                // 388 #endif /* _ALLBSD_SOURCE */
            }
            // 390 return NET_GetPortFromSockaddr((struct sockaddr *)&sa);
            return JavaNetNetUtilMD.NET_GetPortFromSockaddr(sa_Pointer);
        }

        // 753 JNIEXPORT jint JNICALL
        // 754 Java_sun_nio_ch_Net_poll(JNIEnv* env, jclass this, jobject fdo, jint events, jlong timeout)
        // 755 {
        @Substitute
        static int poll(FileDescriptor fdo, int events, long timeout) throws IOException {
            // 756     struct pollfd pfd;
            Poll.pollfd pfd = StackValue.get(Poll.pollfd.class);
            // 757     int rv;
            int rv;
            // 758     pfd.fd = fdval(env, fdo);
            pfd.set_fd(PosixJavaNIOSubstitutions.fdval(fdo));
            // 759     pfd.events = events;
            pfd.set_events((short) events);
            // 760     rv = poll(&pfd, 1, timeout);
            rv = Poll.poll(pfd, 1, (int) timeout);
            // 762     if (rv >= 0) {
            if (rv >= 0) {
                // 763         return pfd.revents;
                return pfd.events();
                // 764     } else if (errno == EINTR) {
            } else if (Errno.errno() == Errno.EINTR()) {
                // 765         return IOS_INTERRUPTED;
                return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
            } else {
                // 767         handleSocketError(env, errno);
                Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
                // 768         return IOS_THROWN;
                return Target_sun_nio_ch_IOStatus.IOS_THROWN;
            }
        }

        // 232 JNIEXPORT int JNICALL
        // 233 Java_sun_nio_ch_Net_socket0(JNIEnv *env, jclass cl, jboolean preferIPv6,
        // 234                             jboolean stream, jboolean reuse)
        // 235 {
        @SuppressWarnings("finally")
        @Substitute
        static int socket0(boolean preferIPv6, boolean stream, boolean reuse, @SuppressWarnings("unused") boolean fastLoopback) throws IOException {
            // 236     int fd;
            int fd;
            // 237     int type = (stream ? SOCK_STREAM : SOCK_DGRAM);
            int type = (stream ? Socket.SOCK_STREAM() : Socket.SOCK_DGRAM());
            // 238 #ifdef AF_INET6
            // 239     int domain = (ipv6_available() && preferIPv6) ? AF_INET6 : AF_INET;
            int domain = (JavaNetNetUtil.ipv6_available() && preferIPv6) ? Socket.AF_INET6() : Socket.AF_INET();
            // 240 #else
            // 241     int domain = AF_INET;
            // 242 #endif
            // 244     fd = socket(domain, type, 0);
            fd = Socket.socket(domain, type, 0);
            // 245     if (fd < 0) {
            if (fd < 0) {
                // 246         return handleSocketError(env, errno);
                return Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
            }
            // 249 #ifdef AF_INET6
            // 250     /* Disable IPV6_V6ONLY to ensure dual-socket support */
            // 251     if (domain == AF_INET6) {
            if (domain == Socket.AF_INET6()) {
                // 252         int arg = 0;
                CIntPointer arg_Pointer = StackValue.get(CIntPointer.class);
                arg_Pointer.write(0);
                // 253         if (setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, (char*)&arg,
                // 254                        sizeof(int)) < 0) {
                if (Socket.setsockopt(fd, NetinetIn.IPPROTO_IPV6(), NetinetIn.IPV6_V6ONLY(), arg_Pointer, SizeOf.get(CIntPointer.class)) < 0) {
                    try {
                        // 255             JNU_ThrowByNameWithLastError(env,
                        // 256                                          JNU_JAVANETPKG "SocketException",
                        // 257                                          "Unable to set IPV6_V6ONLY");
                        throw new java.net.SocketException(PosixUtils.lastErrorString("Unable to set IPV6_V6ONLY"));
                    } finally {
                        // 258             close(fd);
                        Unistd.close(fd);
                        // 259             return -1;
                        return -1;
                    }
                }
            }
            // 262 #endif
            // 264     if (reuse) {
            if (reuse) {
                // 265         int arg = 1;
                CIntPointer arg_Pointer = StackValue.get(CIntPointer.class);
                arg_Pointer.write(1);
                // 266         if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg,
                // 267                        sizeof(arg)) < 0) {
                if (Socket.setsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_REUSEADDR(), arg_Pointer, SizeOf.get(CIntPointer.class)) < 0) {
                    try {
                        // 268             JNU_ThrowByNameWithLastError(env,
                        // 269                                          JNU_JAVANETPKG "SocketException",
                        // 270                                          "Unable to set SO_REUSEADDR");
                        throw new java.net.SocketException(PosixUtils.lastErrorString("Unable to set SO_REUSEADDR"));
                    } finally {
                        // 271             close(fd);
                        Unistd.close(fd);
                        // 272             return -1;
                        return -1;
                    }
                }
            }
            // 276 #if defined(__linux__)
            if (Platform.includedIn(Platform.LINUX.class)) {
                // 277     if (type == SOCK_DGRAM) {
                if (type == Socket.SOCK_DGRAM()) {
                    // 278         int arg = 0;
                    CIntPointer arg_Pointer = StackValue.get(CIntPointer.class);
                    arg_Pointer.write(0);
                    // 279         int level = (domain == AF_INET6) ? IPPROTO_IPV6 : IPPROTO_IP;
                    int level = (domain == Socket.AF_INET6()) ? NetinetIn.IPPROTO_IPV6() : NetinetIn.IPPROTO_IP();
                    // 280         if ((setsockopt(fd, level, IP_MULTICAST_ALL, (char*)&arg, sizeof(arg)) < 0) &&
                    // 281             (errno != ENOPROTOOPT)) {
                    if ((Socket.setsockopt(fd, level, NetinetIn.IP_MULTICAST_ALL(), arg_Pointer, SizeOf.get(CIntPointer.class)) < 0) &&
                                    (Errno.errno() != Errno.ENOPROTOOPT())) {

                        try {
                            // 282             JNU_ThrowByNameWithLastError(env,
                            // 283                                          JNU_JAVANETPKG "SocketException",
                            // 284                                          "Unable to set IP_MULTICAST_ALL");
                            throw new java.net.SocketException(PosixUtils.lastErrorString("Unable to set IP_MULTICAST_ALL"));
                        } finally {
                            // 285             close(fd);
                            Unistd.close(fd);
                            // 286             return -1;
                            return -1;
                        }
                    }
                }
            }
            // 289 #endif
            // 291 #if defined(__linux__) && defined(AF_INET6)
            if (Platform.includedIn(Platform.LINUX.class)) {
                // 292     /* By default, Linux uses the route default */
                // 293     if (domain == AF_INET6 && type == SOCK_DGRAM) {
                if ((domain == Socket.AF_INET6()) && (type == Socket.SOCK_DGRAM())) {
                    // 294         int arg = 1;
                    CIntPointer arg_Pointer = StackValue.get(CIntPointer.class);
                    arg_Pointer.write(1);
                    // 295         if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, &arg,
                    // 296                        sizeof(arg)) < 0) {
                    if (Socket.setsockopt(fd, NetinetIn.IPPROTO_IPV6(), NetinetIn.IPV6_MULTICAST_HOPS(), arg_Pointer, SizeOf.get(CIntPointer.class)) < 0) {
                        try {
                            // 297             JNU_ThrowByNameWithLastError(env,
                            // 298                                          JNU_JAVANETPKG "SocketException",
                            // 299                                          "Unable to set IPV6_MULTICAST_HOPS");
                            throw new java.net.SocketException(PosixUtils.lastErrorString("Unable to set IPV6_MULTICAST_HOPS"));
                        } finally {
                            // 300             close(fd);
                            Unistd.close(fd);
                            // 301             return -1;
                            return -1;
                        }
                    }
                }
            }
            // 304 #endif
            // 305     return fd;
            return fd;
        }

        // jdk/src/share/classes/sun/nio/ch/Net.java?v=Java_1.8.0_40_b10
        // private static native void bind0(FileDescriptor fd, boolean preferIPv6,
        //                                  boolean useExclBind, InetAddress addr,
        //                                  int port)
        //     throws IOException;
        //
        // jdk/src/solaris/native/sun/nio/ch/Net.c?v=Java_1.8.0_40_b10
        // 265 JNIEXPORT void JNICALL
        // 266 Java_sun_nio_ch_Net_bind0(JNIEnv *env, jclass clazz, jobject fdo, jboolean preferIPv6,
        // 267                           jboolean useExclBind, jobject iao, int port)
        @SuppressWarnings({"unused"})
        @Substitute
        static void bind0(FileDescriptor fd,
                        boolean preferIPv6,
                        boolean useExclBind,
                        InetAddress addr,
                        int port) throws IOException {
            // 269     SOCKADDR sa;
            Socket.sockaddr sa = StackValue.get(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 270     int sa_len = SOCKADDR_LEN;
            CIntPointer sa_len_Pointer = StackValue.get(CIntPointer.class);
            sa_len_Pointer.write(JavaNetNetUtilMD.SOCKADDR_LEN());
            // 271     int rv = 0;
            int rv = 0;
            // 272
            // 273     if (NET_InetAddressToSockaddr(env, iao, port, (struct sockaddr *)&sa, &sa_len, preferIPv6) != 0) {
            if (JavaNetNetUtilMD.NET_InetAddressToSockaddr(addr, port, sa, sa_len_Pointer, preferIPv6) != 0) {
            // 274       return;
                return;
            }
            // 276
            // 277     rv = NET_Bind(fdval(env, fdo), (struct sockaddr *)&sa, sa_len);
            rv = JavaNetNetUtilMD.NET_Bind(PosixUtils.getFD(fd), sa, sa_len_Pointer.read());
            // 278     if (rv != 0) {
            if (rv != 0) {
            // 279         handleSocketError(env, errno);
                Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
            }
        }

        // jdk/src/share/classes/sun/nio/ch/Net.java?v=Java_1.8.0_40_b10
        // static native void listen(FileDescriptor fd, int backlog) throws IOException;
        //
        // jdk/src/share/classes/sun/nio/ch/Net.java?v=Java_1.8.0_40_b10
        // 283 JNIEXPORT void JNICALL
        // 284 Java_sun_nio_ch_Net_listen(JNIEnv *env, jclass cl, jobject fdo, jint backlog)
        @Substitute
        static void listen(FileDescriptor fdo, int backlog) throws IOException {
            // 286     if (listen(fdval(env, fdo), backlog) < 0)
            if (Target_os.listen(PosixUtils.getFD(fdo), backlog) < 0) {
                // 287         handleSocketError(env, errno);
                Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
            }
        }

        // jdk/src/share/classes/sun/nio/ch/Net.java?v=Java_1.8.0_40_b10
        // 498 private static native int getIntOption0(FileDescriptor fd, boolean mayNeedConversion,
        // 499                                         int level, int opt)
        // 500     throws IOException;
        //
        // jdk/src/solaris/native/sun/nio/ch/Net.c?v=Java_1.8.0_40_b10
        // 428 JNIEXPORT jint JNICALL
        // 429 Java_sun_nio_ch_Net_getIntOption0(JNIEnv *env, jclass clazz, jobject fdo,
        // 430                                   jboolean mayNeedConversion, jint level, jint opt)
        // 431 {
        @Substitute
        static int getIntOption0(FileDescriptor fdo, boolean mayNeedConversion, int level, int opt) throws IOException {
            // 432     int result;
            CIntPointer result_Pointer = StackValue.get(CIntPointer.class);
            // 433     struct linger linger;
            Socket.linger linger = StackValue.get(Socket.linger.class);
            // 434     u_char carg;
            CCharPointer carg_Pointer = StackValue.get(CCharPointer.class);
            // 435     void *arg;
            VoidPointer arg;
            // 436     socklen_t arglen;
            CIntPointer arglen_Pointer = StackValue.get(CIntPointer.class);
            // 437     int n;
            int n;
            // 438
            // 439     /* Option value is an int except for a few specific cases */
            // 440
            // 441     arg = (void *)&result;
            arg = (VoidPointer) result_Pointer;
            // 442     arglen = sizeof(result);
            arglen_Pointer.write(SizeOf.get(CIntPointer.class));
            // 443
            // 444     if (level == IPPROTO_IP &&
            // 445         (opt == IP_MULTICAST_TTL || opt == IP_MULTICAST_LOOP)) {
            if (level == NetinetIn.IPPROTO_IP() &&
                            (opt == NetinetIn.IP_MULTICAST_TTL() || opt == NetinetIn.IP_MULTICAST_LOOP())) {
                // 446         arg = (void*)&carg;
                arg = (VoidPointer) carg_Pointer;
                // 447         arglen = sizeof(carg);
                arglen_Pointer.write(SizeOf.get(CCharPointer.class));
            }
            // 449
            // 450     if (level == SOL_SOCKET && opt == SO_LINGER) {
            if (level == Socket.SOL_SOCKET() && opt == Socket.SO_LINGER()) {
                // 451         arg = (void *)&linger;
                arg = (VoidPointer) linger;
                // 452         arglen = sizeof(linger);
                arglen_Pointer.write(SizeOf.get(Socket.linger.class));
            }
            // 454
            // 455     if (mayNeedConversion) {
            if (mayNeedConversion) {
                // 456         n = NET_GetSockOpt(fdval(env, fdo), level, opt, arg, (int*)&arglen);
                n = JavaNetNetUtilMD.NET_GetSockOpt(fdval(fdo), level, opt, arg, arglen_Pointer);
            } else {
                // 458         n = getsockopt(fdval(env, fdo), level, opt, arg, &arglen);
                n = Socket.getsockopt(fdval(fdo), level, opt, arg, arglen_Pointer);
            }
            // 460     if (n < 0) {
            if (n < 0) {
                // 461         JNU_ThrowByNameWithLastError(env,
                // 462                                      JNU_JAVANETPKG "SocketException",
                // 463                                      "sun.nio.ch.Net.getIntOption");
                throw new SocketException(PosixUtils.lastErrorString("sun.nio.ch.Net.getIntOption"));
                // 464         return -1;
            }
            // 466
            // 467     if (level == IPPROTO_IP &&
            // 468         (opt == IP_MULTICAST_TTL || opt == IP_MULTICAST_LOOP))
            // 469     {
            if (level == NetinetIn.IPPROTO_IP() &&
                            (opt == NetinetIn.IP_MULTICAST_TTL() || opt == NetinetIn.IP_MULTICAST_LOOP())) {
                // 470         return (jint)carg;
                return carg_Pointer.read();
            }
            // 472
            // 473     if (level == SOL_SOCKET && opt == SO_LINGER)
            if (level == Socket.SOL_SOCKET() && opt == Socket.SO_LINGER()) {
                // 474         return linger.l_onoff ? (jint)linger.l_linger : (jint)-1;
                return CTypeConversion.toBoolean(linger.l_onoff()) ? linger.l_linger() : -1;
            }
            // 475
            // 476     return (jint)result;
            return result_Pointer.read();
        }

        // jdk/src/share/classes/sun/nio/ch/Net.java?v=Java_1.8.0_40_b10
        // private static native void setIntOption0(FileDescriptor fd, boolean mayNeedConversion,
        //                                          int level, int opt, int arg, boolean isIPv6)
        //     throws IOException;
        //
        // jdk/src/solaris/native/sun/nio/ch/Net.c?v=Java_1.8.0_40_b10
        // 479 JNIEXPORT void JNICALL
        //         480 Java_sun_nio_ch_Net_setIntOption0(JNIEnv *env, jclass clazz, jobject fdo,
        //         481                                   jboolean mayNeedConversion, jint level,
        //         482                                   jint opt, jint arg, jboolean isIPv6)
        //         483 {
        @Substitute
        static void setIntOption0(FileDescriptor fdo,
                                  boolean        mayNeedConversion,
                                  int            level,
                                  int            opt,
                                  int            arg,
                                  boolean        isIPv6) throws IOException {
            /* Make a local copy of arg so I can get the address of it. */
            CIntPointer local_arg = StackValue.get(CIntPointer.class);
            local_arg.write(arg);
            //         484     int result;
            //         485     struct linger linger;
            Socket.linger linger = StackValue.get(Socket.linger.class);
            //         486     u_char carg;
            CCharPointer carg_Pointer = StackValue.get(CCharPointer.class);
            //         487     void *parg;
            WordPointer parg;
            //         488     socklen_t arglen;
            long arglen;
            //         489     int n;
            int n;
            //         490
            //         491     /* Option value is an int except for a few specific cases */
            //         492
            //         493     parg = (void*)&arg;
            parg = (WordPointer) local_arg;
            //         494     arglen = sizeof(arg);
            arglen = SizeOf.get(CIntPointer.class);
            //         495
            //         496     if (level == IPPROTO_IP &&
            //         497         (opt == IP_MULTICAST_TTL || opt == IP_MULTICAST_LOOP)) {
            if (level == NetinetIn.IPPROTO_IP() &&
                            (opt == NetinetIn.IP_MULTICAST_TTL() || opt == NetinetIn.IP_MULTICAST_LOOP())) {
                //         498         parg = (void*)&carg;
                parg = (WordPointer) carg_Pointer;
                //         499         arglen = sizeof(carg);
                arglen = SizeOf.get(CCharPointer.class);
                //         500         carg = (u_char)arg;
                carg_Pointer.write((byte) arg);
            }
            //         502
            //         503     if (level == SOL_SOCKET && opt == SO_LINGER) {
            if (level == Socket.SOL_SOCKET() && opt == Socket.SO_LINGER()) {
                //         504         parg = (void *)&linger;
                parg = (WordPointer) linger;
                //         505         arglen = sizeof(linger);
                arglen = SizeOf.get(Socket.linger.class);
                //         506         if (arg >= 0) {
                if (arg >= 0) {
                    //         507             linger.l_onoff = 1;
                    linger.set_l_onoff(1);
                    //         508             linger.l_linger = arg;
                    linger.set_l_linger(arg);
                } else {
                    //         510             linger.l_onoff = 0;
                    linger.set_l_onoff(0);
                    //         511             linger.l_linger = 0;
                    linger.set_l_linger(0);
                }
            }
            //         514
            //         515     if (mayNeedConversion) {
            if (mayNeedConversion) {
                //         516         n = NET_SetSockOpt(fdval(env, fdo), level, opt, parg, arglen);
                n = JavaNetNetUtilMD.NET_SetSockOpt(fdval(fdo), level, opt, parg, (int) arglen);
            } else {
                //         518         n = setsockopt(fdval(env, fdo), level, opt, parg, arglen);
                n = Socket.setsockopt(fdval(fdo), level, opt, parg, (int) arglen);
            }
            try {
                //         520     if (n < 0) {
                if (n < 0) {
                    //         521         JNU_ThrowByNameWithLastError(env,
                    //         522                                      JNU_JAVANETPKG "SocketException",
                    //         523                                      "sun.nio.ch.Net.setIntOption");
                    throw new SocketException(PosixUtils.lastErrorString("sun.nio.ch.Net.setIntOption"));
                }
            } finally {
                //         525 #ifdef __linux__
                if (IsDefined.__linux__()) {
                    //         526     if (level == IPPROTO_IPV6 && opt == IPV6_TCLASS && isIPv6) {
                    if (level == NetinetIn.IPPROTO_IPV6() && opt == NetinetIn.IPV6_TCLASS() && isIPv6) {
                        //         527         // set the V4 option also
                        //         528         setsockopt(fdval(env, fdo), IPPROTO_IP, IP_TOS, parg, arglen);
                        Socket.setsockopt(fdval(fdo), NetinetIn.IPPROTO_IP(), NetinetIn.IP_TOS(), parg, (int) arglen);
                        //         529     }
                        //         530 #endif
                    }
                }
            }
        }

        // jdk/src/share/classes/sun/nio/ch/Net.java?v=Java_1.8.0_40_b10
        // 472     static native void shutdown(FileDescriptor fd, int how) throws IOException;
        //
        // jdk/src/solaris/native/sun/nio/ch/Net.c?v=Java_1.8.0_40_b10
        // 744 JNIEXPORT void JNICALL
        // 745 Java_sun_nio_ch_Net_shutdown(JNIEnv *env, jclass cl, jobject fdo, jint jhow)
        // 746 {
        @Substitute
        static void shutdown(FileDescriptor fdo, int jhow) throws IOException {
            // 747     int how = (jhow == sun_nio_ch_Net_SHUT_RD) ? SHUT_RD :
            int how = (jhow == sun.nio.ch.Net.SHUT_RD) ? sun.nio.ch.Net.SHUT_RD :
                // 748         (jhow == sun_nio_ch_Net_SHUT_WR) ? SHUT_WR : SHUT_RDWR;
                (jhow == sun.nio.ch.Net.SHUT_WR) ? sun.nio.ch.Net.SHUT_WR : sun.nio.ch.Net.SHUT_RDWR;
            // 749     if ((shutdown(fdval(env, fdo), how) < 0) && (errno != ENOTCONN))
            if ((Socket.shutdown(fdval(fdo), how) < 0) && Errno.errno() != Errno.ENOTCONN()) {
                // 750         handleSocketError(env, errno);
                Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
            }
        }
    }

    @TargetClass(className = "sun.nio.ch.ServerSocketChannelImpl")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_ServerSocketChannelImpl {

        // jdk/src/share/classes/sun/nio/ch/ServerSocketChannelImpl.java?v=Java_1.8.0_40_b10
        // 415     // Accepts a new connection, setting the given file descriptor to refer to
        // 416     // the new socket and setting isaa[0] to the socket's remote address.
        // 417     // Returns 1 on success, or IOStatus.UNAVAILABLE (if non-blocking and no
        // 418     // connections are pending) or IOStatus.INTERRUPTED.
        // 419     //
        // 420     private native int accept0(FileDescriptor ssfd, FileDescriptor newfd,
        // 421                                InetSocketAddress[] isaa)
        // 422         throws IOException;
        //
        // jdk/src/solaris/native/sun/nio/ch/ServerSocketChannelImpl.c?v=Java_1.8.0_40_b10
        //        068 JNIEXPORT jint JNICALL
        //        069 Java_sun_nio_ch_ServerSocketChannelImpl_accept0(JNIEnv *env, jobject this,
        //        070                                                 jobject ssfdo, jobject newfdo,
        //        071                                                 jobjectArray isaa)
        @Substitute
        @SuppressWarnings({"static-method"})
        int accept0(FileDescriptor ssfdo, FileDescriptor newfdo, InetSocketAddress[] isaa) throws IOException {
            /* Ignore the receiver. */
            return Util_sun_nio_ch_ServerSocketChannelImpl.accept0(ssfdo, newfdo, isaa);
        }
    }

    static final class Util_sun_nio_ch_ServerSocketChannelImpl {

        /** A {@code static} method that can be called from
         * {@link Target_sun_nio_ch_ServerSocketChannelImpl#accept0(FileDescriptor, FileDescriptor, InetSocketAddress[])}
         * and
         * {@link Target_sun_nio_ch_UnixAsynchronousServerSocketChannelImpl#accept0(FileDescriptor, FileDescriptor, InetSocketAddress[])}
         * because it does not need the {@code this} parameter that is the receiver of those calls.
         */
        // jdk/src/solaris/native/sun/nio/ch/ServerSocketChannelImpl.c?v=Java_1.8.0_40_b10
        static int accept0(FileDescriptor ssfdo, FileDescriptor newfdo, InetSocketAddress[] isaa) throws IOException {
            //        073     jint ssfd = (*env)->GetIntField(env, ssfdo, fd_fdID);
            int ssfd = fdval(ssfdo);
            //        074     jint newfd;
            int newfd;
            //        075     struct sockaddr *sa;
            Socket.sockaddrPointer sa_Pointer = StackValue.get(Socket.sockaddrPointer.class);
            //        076     int alloc_len;
            CIntPointer alloc_len_Pointer = StackValue.get(CIntPointer.class);
            //        077     jobject remote_ia = 0;
            InetAddress remote_ia = null;
            //        078     jobject isa;
            InetSocketAddress isa;
            //        079     jint remote_port;
            CIntPointer remote_port_Pointer = StackValue.get(CIntPointer.class);
            //        080
            //        081     NET_AllocSockaddr(&sa, &alloc_len);
            JavaNetNetUtilMD.NET_AllocSockaddr(sa_Pointer, alloc_len_Pointer);
            //        082
            //        083     /*
            //        084      * accept connection but ignore ECONNABORTED indicating that
            //        085      * a connection was eagerly accepted but was reset before
            //        086      * accept() was called.
            //        087      */
            //        088     for (;;) {
            for (;;) {
                //        089         socklen_t sa_len = alloc_len;
                CIntPointer sa_len_Pointer = StackValue.get(CIntPointer.class);
                sa_len_Pointer.write(alloc_len_Pointer.read());
                //        090         newfd = accept(ssfd, sa, &sa_len);
                newfd = Socket.accept(ssfd, sa_Pointer.read(), sa_len_Pointer);
                //        091         if (newfd >= 0) {
                if (newfd >= 0) {
                    //        092             break;
                    break;
                }
                //        094         if (errno != ECONNABORTED) {
                if (Errno.errno() != Errno.ECONNABORTED()) {
                    //        095             break;
                    break;
                }
                //        097         /* ECONNABORTED => restart accept */
            }
            //        099
            //        100     if (newfd < 0) {
            if (newfd < 0) {
                //        101         free((void *)sa);
                LibC.free(sa_Pointer.read());
                //        102         if (errno == EAGAIN)
                if (Errno.errno() == Errno.EAGAIN()) {
                    //        103             return IOS_UNAVAILABLE;
                    return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
                }
                //        104         if (errno == EINTR)
                if (Errno.errno() == Errno.EINTR()) {
                    //        105             return IOS_INTERRUPTED;
                    return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                }
                //        106         JNU_ThrowIOExceptionWithLastError(env, "Accept failed");
                //        107         return IOS_THROWN;
                throw new IOException("Accept failed");
            }
            //        109
            //        110     (*env)->SetIntField(env, newfdo, fd_fdID, newfd);
            PosixUtils.setFD(newfdo, newfd);
            //        111     remote_ia = NET_SockaddrToInetAddress(env, sa, (int *)&remote_port);
            remote_ia = JavaNetNetUtil.NET_SockaddrToInetAddress(sa_Pointer.read(), remote_port_Pointer);
            //        112     free((void *)sa);
            LibC.free(sa_Pointer.read());
            //        113     isa = (*env)->NewObject(env, isa_class, isa_ctorID,
            //        114                             remote_ia, remote_port);
            isa = new InetSocketAddress(remote_ia, remote_port_Pointer.read());
            //        115     (*env)->SetObjectArrayElement(env, isaa, 0, isa);
            isaa[0] = isa;
            //        116     return 1;
            return 1;
        }
    }

    static final class Util_sun_nio_ch_Net {
        // 811 jint
        // 812 handleSocketError(JNIEnv *env, jint errorValue)
        // 813 {
        static int handleSocketError(int errorValue) throws IOException {
            // 814     char *xn;
            IOException xn;
            final String exceptionString = "NioSocketError";
            // 815     switch (errorValue) {
            // 816         case EINPROGRESS:       /* Non-blocking connect */
            if (errorValue == Errno.EINPROGRESS()) {
                // 817             return 0;
                return 0;
                // 818 #ifdef EPROTO
                // 819         case EPROTO:
            } else if (errorValue == Errno.EPROTO()) {
                // 820             xn = JNU_JAVANETPKG "ProtocolException";
                xn = new java.net.ProtocolException(PosixUtils.errorString(errorValue, exceptionString));
                // 821             break;
                // 822 #endif
                // 823         case ECONNREFUSED:
            } else if (errorValue == Errno.ECONNREFUSED()) {
                // 824             xn = JNU_JAVANETPKG "ConnectException";
                xn = new java.net.ConnectException(PosixUtils.errorString(errorValue, exceptionString));
                // 825             break;
                // 826         case ETIMEDOUT:
            } else if (errorValue == Errno.ETIMEDOUT()) {
                // 827             xn = JNU_JAVANETPKG "ConnectException";
                xn = new java.net.ConnectException(PosixUtils.errorString(errorValue, exceptionString));
                // 828             break;
                // 829         case EHOSTUNREACH:
            } else if (errorValue == Errno.EHOSTUNREACH()) {
                // 830             xn = JNU_JAVANETPKG "NoRouteToHostException";
                xn = new java.net.NoRouteToHostException(PosixUtils.errorString(errorValue, exceptionString));
                // 831             break;
                // 832         case EADDRINUSE:  /* Fall through */
                // 833         case EADDRNOTAVAIL:
            } else if ((errorValue == Errno.EADDRINUSE()) || (errorValue == Errno.EADDRNOTAVAIL())) {
                // 834             xn = JNU_JAVANETPKG "BindException";
                xn = new java.net.BindException(PosixUtils.errorString(errorValue, exceptionString));
                // 835             break;
                // 836         default:
            } else {
                // 837             xn = JNU_JAVANETPKG "SocketException";
                xn = new java.net.SocketException(PosixUtils.errorString(errorValue, exceptionString));
                // 838             break;
            }
            // 840     errno = errorValue;
            Errno.set_errno(errorValue);
            // 841     JNU_ThrowByNameWithLastError(env, xn, "NioSocketError");
            throw xn;
            // 842     return IOS_THROWN;
        }
        /* Checkstyle: resume */
        /* @formatter:on */
    }

    /**
     * Call this to throw an internal UnixException when a system/library call fails.
     */
    private static Exception throwUnixException(int errnum) throws Exception {
        throw KnownIntrinsics.unsafeCast(new Target_sun_nio_fs_UnixException(errnum), Exception.class);
    }

    private static OutOfMemoryError throwOutOfMemoryError(String msg) {
        throw new OutOfMemoryError(msg);
    }

    private static InternalError throwInternalError(String msg) {
        throw new InternalError(msg);
    }

    /** Translations of src/solaris/native/sun/nio/ch/FileDispatcherImpl.c?v=Java_1.8.0_40_b10. */
    @TargetClass(className = "sun.nio.ch.FileDispatcherImpl")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_FileDispatcherImpl {

        @Substitute
        private static int read0(FileDescriptor fdo, long address, int len) throws IOException {
            int fd = fdval(fdo);
            PointerBase buf = WordFactory.pointer(address);
            return convertReturnVal(read(fd, buf, WordFactory.unsigned(len)), true);
        }

        @Substitute
        private static int pread0(FileDescriptor fdo, long address, int len, long offset) throws IOException {
            int fd = fdval(fdo);
            PointerBase buf = WordFactory.pointer(address);
            return convertReturnVal(pread(fd, buf, WordFactory.unsigned(len), offset), true);
        }

        @Substitute
        private static long readv0(FileDescriptor fdo, long address, int len) throws IOException {
            int fd = fdval(fdo);
            iovec iov = WordFactory.pointer(address);
            return convertLongReturnVal(readv(fd, iov, len), true);
        }

        @Substitute
        private static int write0(FileDescriptor fdo, long address, int len) throws IOException {
            int fd = fdval(fdo);
            PointerBase buf = WordFactory.unsigned(address);
            return convertReturnVal(write(fd, buf, WordFactory.unsigned(len)), false);
        }

        @Substitute
        private static int pwrite0(FileDescriptor fdo, long address, int len, long offset) throws IOException {
            int fd = fdval(fdo);
            PointerBase buf = WordFactory.unsigned(address);
            return convertReturnVal(pwrite(fd, buf, WordFactory.unsigned(len), offset), false);
        }

        @Substitute
        private static long writev0(FileDescriptor fdo, long address, int len) throws IOException {
            int fd = fdval(fdo);
            iovec iov = WordFactory.pointer(address);
            return convertLongReturnVal(writev(fd, iov, len), false);
        }

        @Substitute
        private static int force0(FileDescriptor fdo, boolean metaData) throws IOException {
            int fd = fdval(fdo);
            int result = 0;

            if (metaData == false) {
                result = fdatasync(fd);
            } else {
                result = fsync(fd);
            }
            return handle(result, "Force failed");
        }

        /* Translated from src/java.base/unix/native/libnio/ch/FileDispatcherImpl.c */
        /* { Do not re-format commented out C code: @formatter:off. */
        // JNIEXPORT jint JNICALL
        // Java_sun_nio_ch_FileDispatcherImpl_allocate0(JNIEnv *env, jobject this,
        //                                              jobject fdo, jlong size) {
        @Substitute @TargetElement(onlyWith = ContainsAllocate0.class /* Introduced in JDK 8u162. */)
        private static int allocate0(FileDescriptor fd, long size) throws IOException {
            // #if defined(__linux__)
            if (IsDefined.__linux__()) {
                //     /*
                //      * On Linux, if the file size is being increased, then ftruncate64()
                //      * will modify the metadata value of the size without actually allocating
                //      * any blocks which can cause a SIGBUS error if the file is subsequently
                //      * memory-mapped.
                //      */
                //     return handle(env,
                //                   fallocate64(fdval(env, fdo), 0, 0, size),
                //                   "Allocation failed");
                return handle(Fcntl.fallocate(fdval(fd), 0, WordFactory.zero(), WordFactory.signed(size)),
                                "Allocation failed");
            } else {
                //     return handle(env,
                //                   ftruncate64(fdval(env, fdo), size),
                //                   "Truncation failed");
                return handle(Unistd.ftruncate(fdval(fd), size),
                                "Truncation failed");
            }
        }
        /* } Do not re-format commented out C code: @formatter:on. */

        static class ContainsAllocate0 implements Predicate<Class<?>> {
            @Override
            public boolean test(Class<?> originalClass) {
                try {
                    originalClass.getDeclaredMethod("allocate0", FileDescriptor.class, long.class);
                    return true;
                } catch (NoSuchMethodException ex) {
                    return false;
                }
            }
        }

        @Substitute
        private static int truncate0(FileDescriptor fdo, long size) throws IOException {
            return handle(ftruncate(fdval(fdo), size), "Truncation failed");

        }

        @Substitute
        private static long size0(FileDescriptor fdo) throws IOException {
            Stat.stat fbuf = StackValue.get(Stat.stat.class);

            if (fstat(fdval(fdo), fbuf) < 0) {
                return handle(-1, "Size failed");
            }
            return fbuf.st_size();
        }

        @Substitute
        private static int lock0(FileDescriptor fdo, boolean blocking, long pos, long size, boolean shared) throws IOException {
            int fd = fdval(fdo);
            int lockResult = 0;
            int cmd = 0;
            flock fl = StackValue.get(flock.class);

            fl.set_l_whence(SEEK_SET());
            if (size == Long.MAX_VALUE) {
                fl.set_l_len(WordFactory.zero());
            } else {
                fl.set_l_len(WordFactory.signed(size));
            }
            fl.set_l_start(WordFactory.signed(pos));
            if (shared == true) {
                fl.set_l_type(F_RDLCK());
            } else {
                fl.set_l_type(F_WRLCK());
            }
            if (blocking == true) {
                cmd = F_SETLKW();
            } else {
                cmd = F_SETLK();
            }
            lockResult = fcntl(fd, cmd, fl);
            if (lockResult < 0) {
                if ((cmd == F_SETLK()) && (errno() == EAGAIN() || errno() == EACCES())) {
                    return Target_sun_nio_ch_FileDispatcher.FD_NO_LOCK;
                }
                if (errno() == EINTR()) {
                    return Target_sun_nio_ch_FileDispatcher.FD_INTERRUPTED;
                }
                throwIOExceptionWithLastError("Lock failed");
            }
            return 0;
        }

        @Substitute
        private static void release0(FileDescriptor fdo, long pos, long size) throws IOException {
            int fd = fdval(fdo);
            int lockResult = 0;
            flock fl = StackValue.get(flock.class);
            int cmd = F_SETLK();

            fl.set_l_whence(SEEK_SET());
            if (size == Long.MAX_VALUE) {
                fl.set_l_len(WordFactory.zero());
            } else {
                fl.set_l_len(WordFactory.signed(size));
            }
            fl.set_l_start(WordFactory.signed(pos));
            fl.set_l_type(F_UNLCK());
            lockResult = fcntl(fd, cmd, fl);
            if (lockResult < 0) {
                throw throwIOExceptionWithLastError("Release failed");
            }
        }

        @Substitute
        private static void close0(FileDescriptor fdo) throws IOException {
            int fd = fdval(fdo);
            Util_sun_nio_ch_FileDispatcherImpl.closeFileDescriptor(fd);
        }

        @Substitute
        private static void preClose0(FileDescriptor fdo) throws IOException {
            if (!Util_sun_nio_ch_FileDispatcherImpl.initialized) {
                Util_sun_nio_ch_FileDispatcherImpl.initialize();
            }

            int fd = fdval(fdo);
            if (Util_sun_nio_ch_FileDispatcherImpl.preCloseFD >= 0) {
                if (dup2(Util_sun_nio_ch_FileDispatcherImpl.preCloseFD, fd) < 0) {
                    throwIOExceptionWithLastError("dup2 failed");
                }
            }
        }

        @Substitute
        private static void closeIntFD(int fd) throws IOException {
            Util_sun_nio_ch_FileDispatcherImpl.closeFileDescriptor(fd);
        }

        @Substitute
        private static void init() {
            throw new InternalError("init() is only called from static initializers, so not reachable in Substrate VM");
        }
    }

    static final class Util_sun_nio_ch_FileDispatcherImpl {

        static void closeFileDescriptor(int fd) throws IOException {
            if (fd != -1) {
                int result = close(fd);
                if (result < 0) {
                    throwIOExceptionWithLastError("Close failed");
                }
            }
        }

        /** File descriptor to which we dup other fd's before closing them for real. */
        static int preCloseFD = -1;

        static volatile boolean initialized;

        static void initialize() {
            CIntPointer sp = StackValue.get(2, CIntPointer.class);
            if (Socket.socketpair(Socket.PF_UNIX(), Socket.SOCK_STREAM(), 0, sp) == 0) {
                preCloseFD = sp.read(0);
                close(sp.read(1));
            }

            initialized = true;
        }
    }

    @TargetClass(className = "sun.nio.fs.UnixException")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @SuppressWarnings({"unused"})
    static final class Target_sun_nio_fs_UnixException {
        @Alias
        protected Target_sun_nio_fs_UnixException(int errno) {
        }
    }

    // Checkstyle: stop
    @TargetClass(className = "sun.nio.fs.UnixFileAttributes")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixFileAttributes {
        @Alias int st_mode;
        @Alias long st_ino;
        @Alias long st_dev;
        @Alias long st_rdev;
        @Alias int st_nlink;
        @Alias int st_uid;
        @Alias int st_gid;
        @Alias long st_size;
        @Alias long st_atime_sec;
        @Alias long st_atime_nsec;
        @Alias long st_mtime_sec;
        @Alias long st_mtime_nsec;
        @Alias long st_ctime_sec;
        @Alias long st_ctime_nsec;
        @Alias long st_birthtime_sec;

    }

    @TargetClass(className = "sun.nio.fs.UnixFileStoreAttributes")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixFileStoreAttributes {
        @Alias long f_frsize;
        @Alias long f_blocks;
        @Alias long f_bfree;
        @Alias long f_bavail;
    }

    @TargetClass(className = "sun.nio.fs.UnixMountEntry")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixMountEntry {
        @Alias byte[] name;
        @Alias byte[] dir;
        @Alias byte[] fstype;
        @Alias byte[] opts;
        @Alias long dev;
    }

    // Checkstyle: resume

    protected static byte[] toByteArray(CCharPointer cstr) {
        UnsignedWord len = SubstrateUtil.strlen(cstr);
        byte[] result = new byte[(int) len.rawValue()];
        for (int i = 0; i < result.length; i++) {
            result[i] = cstr.read(i);
        }
        return result;
    }

    /*
     * Converted from JDK 7 update 40 C source file:
     * src/solaris/native/sun/nio/fs/UnixNativeDispatcher.c
     */

    /*
     * System calls that may not be available at run time.
     */

    // Checkstyle: stop
    interface openat64_func extends CFunctionPointer {
        /**
         * @see Fcntl#openat
         */
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer pathname, int flags, int mode);
    }

    interface fstatat64_func extends CFunctionPointer {
        /**
         * @see Stat#fstatat
         */
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer file, Stat.stat buf, int flag);
    }

    interface unlinkat_func extends CFunctionPointer {
        /**
         * @see Unistd#unlinkat
         */
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer name, int flag);
    }

    interface renameat_func extends CFunctionPointer {
        /**
         * @see Stdio#renameat
         */
        @InvokeCFunctionPointer
        int invoke(int oldfd, CCharPointer old, int newfd, CCharPointer _new);
    }

    interface futimesat_func extends CFunctionPointer {
        /**
         * @see Time#futimesat
         */
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer file, timeval tvp);
    }

    interface fdopendir_func extends CFunctionPointer {
        /**
         * @see Dirent#fdopendir
         */
        @InvokeCFunctionPointer
        DIR invoke(int fd);
    }

    // Checkstyle: resume

    @TargetClass(className = "sun.nio.fs.UnixNativeDispatcher")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixNativeDispatcher {

        @Substitute
        private static byte[] getcwd() throws Exception {
            byte[] result;
            int bufsize = PATH_MAX() + 1;
            CCharPointer buf = StackValue.get(bufsize, CCharPointer.class);

            /* EINTR not listed as a possible error */
            CCharPointer cwd = Unistd.getcwd(buf, WordFactory.unsigned(bufsize));
            if (cwd.isNull()) {
                throw throwUnixException(errno());
            } else {
                result = toByteArray(buf);
            }
            return result;
        }

        @Substitute
        private static int dup(int fd) throws Exception {
            int res = -1;

            do {
                res = Unistd.dup(fd);
            } while ((res == -1) && (errno() == EINTR()));

            if (res == -1) {
                throwUnixException(errno());
            }
            return res;
        }

        @Substitute
        private static int open0(long pathAddress, int flags, int mode) throws Exception {
            int fd;
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                fd = open(path, flags, mode);
            } while ((fd == -1) && (errno() == EINTR()));

            if (fd == -1) {
                throwUnixException(errno());
            }
            return fd;
        }

        @Substitute
        private static int openat0(int dfd, long pathAddress, int flags, int mode) throws Exception {
            int fd;
            CCharPointer path = WordFactory.pointer(pathAddress);

            if (Util_sun_nio_fs_UnixNativeDispatcher.my_openat64_func.isNull()) {
                throw throwInternalError("should not reach here");
            }

            do {
                fd = Util_sun_nio_fs_UnixNativeDispatcher.my_openat64_func.invoke(dfd, path, flags, mode);
            } while ((fd == -1) && (errno() == EINTR()));

            if (fd == -1) {
                throwUnixException(errno());
            }
            return fd;
        }

        @Substitute
        private static void close(int fd) {
            int err;
            /* TDB - need to decide if EIO and other errors should cause exception */
            do {
                err = Unistd.close(fd);
            } while ((err == -1) && (errno() == EINTR()));
        }

        @Substitute
        private static long fopen0(long pathAddress, long modeAddress) throws Exception {
            FILE fp = WordFactory.nullPointer();
            CCharPointer path = WordFactory.pointer(pathAddress);
            CCharPointer mode = WordFactory.pointer(modeAddress);

            do {
                fp = fopen(path, mode);
            } while (fp.isNull() && errno() == EINTR());

            if (fp.isNull()) {
                throwUnixException(errno());
            }

            return fp.rawValue();
        }

        @Substitute
        private static void fclose(long stream) throws Exception {
            int res;
            FILE fp = WordFactory.pointer(stream);

            do {
                res = Stdio.fclose(fp);
            } while (res == EOF() && errno() == EINTR());
            if (res == EOF()) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void link0(long existingAddress, long newAddress) throws Exception {
            int err;
            CCharPointer existing = WordFactory.pointer(existingAddress);
            CCharPointer newname = WordFactory.pointer(newAddress);

            do {
                err = link(existing, newname);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void unlink0(long pathAddress) throws Exception {
            CCharPointer path = WordFactory.pointer(pathAddress);

            /* EINTR not listed as a possible error */
            if (unlink(path) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void unlinkat0(int dfd, long pathAddress, int flags) throws Exception {
            CCharPointer path = WordFactory.pointer(pathAddress);

            if (Util_sun_nio_fs_UnixNativeDispatcher.my_unlinkat_func.isNull()) {
                throw throwInternalError("should not reach here");
            }

            /* EINTR not listed as a possible error */
            if (Util_sun_nio_fs_UnixNativeDispatcher.my_unlinkat_func.invoke(dfd, path, flags) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void mknod0(long pathAddress, int mode, long dev) throws Exception {
            int err;
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = mknod(path, mode, dev);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void rename0(long fromAddress, long toAddress) throws Exception {
            CCharPointer from = WordFactory.pointer(fromAddress);
            CCharPointer to = WordFactory.pointer(toAddress);

            /* EINTR not listed as a possible error */
            if (Stdio.rename(from, to) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void renameat0(int fromfd, long fromAddress, int tofd, long toAddress) throws Exception {
            CCharPointer from = WordFactory.pointer(fromAddress);
            CCharPointer to = WordFactory.pointer(toAddress);

            if (Util_sun_nio_fs_UnixNativeDispatcher.my_renameat_func.isNull()) {
                throw throwInternalError("should not reach here");
            }

            /* EINTR not listed as a possible error */
            if (Util_sun_nio_fs_UnixNativeDispatcher.my_renameat_func.invoke(fromfd, from, tofd, to) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void mkdir0(long pathAddress, int mode) throws Exception {
            CCharPointer path = WordFactory.pointer(pathAddress);

            /* EINTR not listed as a possible error */
            if (mkdir(path, mode) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void rmdir0(long pathAddress) throws Exception {
            CCharPointer path = WordFactory.pointer(pathAddress);

            /* EINTR not listed as a possible error */
            if (rmdir(path) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static byte[] readlink0(long pathAddress) throws Exception {
            byte[] result;
            int targetsize = PATH_MAX() + 1;
            CCharPointer target = StackValue.get(targetsize, CCharPointer.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            /* EINTR not listed as a possible error */
            int n = (int) readlink(path, target, WordFactory.unsigned(targetsize)).rawValue();
            if (n == -1) {
                throw throwUnixException(errno());
            } else {
                if (n == targetsize) {
                    n--;
                }
                target.write(n, (byte) 0);
                result = toByteArray(target);
            }
            return result;
        }

        @Substitute
        private static byte[] realpath0(long pathAddress) throws Exception {
            byte[] result;
            CCharPointer resolved = StackValue.get(PATH_MAX() + 1, CCharPointer.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            /* EINTR not listed as a possible error */
            if (Stdlib.realpath(path, resolved).isNull()) {
                throw throwUnixException(errno());
            } else {
                result = toByteArray(resolved);
            }
            return result;
        }

        @Substitute
        private static void symlink0(long targetAddress, long linkAddress) throws Exception {
            CCharPointer target = WordFactory.pointer(targetAddress);
            CCharPointer link = WordFactory.pointer(linkAddress);

            /* EINTR not listed as a possible error */
            if (symlink(target, link) == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void stat0(long pathAddress, Target_sun_nio_fs_UnixFileAttributes attrs) throws Exception {
            int err;
            Stat.stat buf = StackValue.get(Stat.stat.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = Stat.stat(path, buf);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            } else {
                Util_sun_nio_fs_UnixNativeDispatcher.prepAttributes(buf, attrs);
            }
        }

        @Substitute
        private static void lstat0(long pathAddress, Target_sun_nio_fs_UnixFileAttributes attrs) throws Exception {
            int err;
            Stat.stat buf = StackValue.get(Stat.stat.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = lstat(path, buf);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            } else {
                Util_sun_nio_fs_UnixNativeDispatcher.prepAttributes(buf, attrs);
            }
        }

        @Substitute
        private static void fstat(int fd, Target_sun_nio_fs_UnixFileAttributes attrs) throws Exception {
            int err;
            Stat.stat buf = StackValue.get(Stat.stat.class);

            do {
                err = Stat.fstat(fd, buf);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            } else {
                Util_sun_nio_fs_UnixNativeDispatcher.prepAttributes(buf, attrs);
            }
        }

        @Substitute
        private static void fstatat0(int dfd, long pathAddress, int flag, Target_sun_nio_fs_UnixFileAttributes attrs) throws Exception {
            int err;
            Stat.stat buf = StackValue.get(Stat.stat.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            if (Util_sun_nio_fs_UnixNativeDispatcher.my_fstatat64_func.isNull()) {
                throw throwInternalError("should not reach here");
            }
            do {
                err = Util_sun_nio_fs_UnixNativeDispatcher.my_fstatat64_func.invoke(dfd, path, buf, flag);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            } else {
                Util_sun_nio_fs_UnixNativeDispatcher.prepAttributes(buf, attrs);
            }
        }

        @Substitute
        private static void chown0(long pathAddress, int uid, int gid) throws Exception {
            int err;
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = chown(path, uid, gid);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void lchown0(long pathAddress, int uid, int gid) throws Exception {
            int err;
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = lchown(path, uid, gid);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void fchown(int fd, int uid, int gid) throws Exception {
            int err;

            do {
                err = Unistd.fchown(fd, uid, gid);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void chmod0(long pathAddress, int mode) throws Exception {
            int err;
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = chmod(path, mode);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void fchmod(int fd, int mode) throws Exception {
            int err;

            do {
                err = Stat.fchmod(fd, mode);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void utimes0(long pathAddress, long accessTime, long modificationTime) throws Exception {
            int err;
            Time.timeval times = StackValue.get(2, Time.timeval.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            times.addressOf(0).set_tv_sec(accessTime / 1000000);
            times.addressOf(0).set_tv_usec(accessTime % 1000000);

            times.addressOf(1).set_tv_sec(modificationTime / 1000000);
            times.addressOf(1).set_tv_usec(modificationTime % 1000000);

            do {
                err = Time.utimes(path, times);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static void futimes(int fd, long accessTime, long modificationTime) throws Exception {
            Time.timeval times = StackValue.get(2, Time.timeval.class);
            int err = 0;

            times.addressOf(0).set_tv_sec(accessTime / 1000000);
            times.addressOf(0).set_tv_usec(accessTime % 1000000);

            times.addressOf(1).set_tv_sec(modificationTime / 1000000);
            times.addressOf(1).set_tv_usec(modificationTime % 1000000);

            // #ifdef _ALLBSD_SOURCE
            do {
                err = Time.futimes(fd, times);
            } while ((err == -1) && (errno() == EINTR()));
            // #else
            // if (PointerUtils.isNullPointer(my_futimesat_func)) {
            // throw throwInternalError("my_ftimesat_func is NULL");
            // }
            // do {
            // err = my_futimesat_func.invoke(fd, (CCharPointer) PointerUtils.nullPointer(), times);
            // } while ((err == -1) && (errno() == EINTR()));
            // #endif
            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static long opendir0(long pathAddress) throws Exception {
            DIR dir;
            CCharPointer path = WordFactory.pointer(pathAddress);

            /* EINTR not listed as a possible error */
            dir = opendir(path);
            if (dir.isNull()) {
                throwUnixException(errno());
            }
            return dir.rawValue();
        }

        @Substitute
        private static long fdopendir(int dfd) throws Exception {
            DIR dir;

            if (Util_sun_nio_fs_UnixNativeDispatcher.my_fdopendir_func.isNull()) {
                throw throwInternalError("should not reach here");
            }

            /* EINTR not listed as a possible error */
            dir = Util_sun_nio_fs_UnixNativeDispatcher.my_fdopendir_func.invoke(dfd);
            if (dir.isNull()) {
                throwUnixException(errno());
            }
            return dir.rawValue();
        }

        @Substitute
        private static void closedir(long dir) throws Exception {
            int err;
            DIR dirp = WordFactory.pointer(dir);

            do {
                err = Dirent.closedir(dirp);
            } while ((err == -1) && (errno() == EINTR()));

            if (errno() == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static byte[] readdir(long value) throws Exception {
            direntPointer result = StackValue.get(direntPointer.class);
            dirent ptr = StackValue.get(SizeOf.get(dirent.class) + PATH_MAX() + 1);

            int res;
            DIR dirp = WordFactory.pointer(value);

            /* EINTR not listed as a possible error */
            /* TDB: reentrant version probably not required here */
            res = readdir_r(dirp, ptr, result);
            if (res != 0) {
                throw throwUnixException(res);
            } else {
                if (result.read().isNull()) {
                    return null;
                } else {
                    return toByteArray(ptr.d_name());
                }
            }
        }

        @Substitute
        private static int read(int fd, long address, int nbytes) throws Exception {
            int n;
            PointerBase bufp = WordFactory.pointer(address);
            do {
                n = (int) Unistd.read(fd, bufp, WordFactory.unsigned(nbytes)).rawValue();
            } while (n == -1 && (errno() == EINTR()));

            if (n == -1) {
                throwUnixException(errno());
            }
            return n;
        }

        @Substitute
        private static int write(int fd, long address, int nbytes) throws Exception {
            int n;
            PointerBase bufp = WordFactory.pointer(address);
            do {
                n = (int) Unistd.write(fd, bufp, WordFactory.unsigned(nbytes)).rawValue();
            } while (n == -1 && (errno() == EINTR()));

            if (n == -1) {
                throwUnixException(errno());
            }
            return n;
        }

        @Substitute
        private static void access0(long pathAddress, int amode) throws Exception {
            int err;
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = access(path, amode);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            }
        }

        @Substitute
        private static byte[] getpwuid(int uid) throws Exception {
            byte[] result;
            int buflen;
            CCharPointer pwbuf;

            /* allocate buffer for password record */
            buflen = (int) sysconf(_SC_GETPW_R_SIZE_MAX());
            if (buflen == -1) {
                buflen = Util_sun_nio_fs_UnixNativeDispatcher.ENT_BUF_SIZE;
            }
            pwbuf = LibC.malloc(WordFactory.unsigned(buflen));
            if (pwbuf.isNull()) {
                throw throwOutOfMemoryError("native heap");
            } else {
                passwd pwent = StackValue.get(passwd.class);
                passwdPointer p = StackValue.get(passwdPointer.class);
                p.write(WordFactory.nullPointer());
                int res = 0;

                // errno = 0;
                Errno.set_errno(0);
                // #ifdef __solaris__
                // RESTARTABLE_RETURN_PTR(getpwuid_r((uid_t)uid, &pwent, pwbuf, (Unsigned)buflen),
                // p);
                // #else
                do {
                    res = getpwuid_r(uid, pwent, pwbuf, WordFactory.unsigned(buflen), p);
                } while ((res == -1) && (errno() == EINTR()));

                // #endif

                if (res != 0 || p.read().isNull() || p.read().pw_name().isNull() || p.read().pw_name().read() == 0) {
                    /* not found or error */
                    throw throwUnixException(errno() == 0 ? ENOENT() : errno());
                } else {
                    result = toByteArray(p.read().pw_name());
                }
                LibC.free(pwbuf);
            }

            return result;
        }

        @Substitute
        private static byte[] getgrgid(int gid) throws Exception {
            byte[] result = null;
            int buflen;
            boolean retry;

            /* initial size of buffer for group record */
            buflen = (int) sysconf(_SC_GETGR_R_SIZE_MAX());
            if (buflen == -1) {
                buflen = Util_sun_nio_fs_UnixNativeDispatcher.ENT_BUF_SIZE;
            }

            do {
                group grent = StackValue.get(group.class);
                groupPointer g = StackValue.get(groupPointer.class);
                g.write(WordFactory.nullPointer());
                int res = 0;

                CCharPointer grbuf = LibC.malloc(WordFactory.unsigned(buflen));
                if (grbuf.isNull()) {
                    throw throwOutOfMemoryError("native heap");
                }

                // errno = 0;
                Errno.set_errno(0);
                // #ifdef __solaris__
                // RESTARTABLE_RETURN_PTR(getgrgid_r((gid_t)gid, &grent, grbuf, (Unsigned)buflen),
                // g);
                // #else
                do {
                    res = getgrgid_r(gid, grent, grbuf, WordFactory.unsigned(buflen), g);
                } while ((res == -1) && (errno() == EINTR()));
                // #endif

                retry = false;
                if (res != 0 || g.read().isNull() || g.read().gr_name().isNull() || g.read().gr_name().read() == 0) {
                    /* not found or error */
                    if (errno() == ERANGE()) {
                        /* insufficient buffer size so need larger buffer */
                        buflen += Util_sun_nio_fs_UnixNativeDispatcher.ENT_BUF_SIZE;
                        retry = true;
                    } else {
                        throw throwUnixException(errno() == 0 ? ENOENT() : errno());
                    }
                } else {
                    result = toByteArray(g.read().gr_name());
                }

                LibC.free(grbuf);

            } while (retry);

            return result;
        }

        @Substitute
        private static int getpwnam0(long nameAddress) throws Exception {
            int uid = -1;
            int buflen;
            CCharPointer pwbuf;

            /* allocate buffer for password record */
            buflen = (int) sysconf(_SC_GETPW_R_SIZE_MAX());
            if (buflen == -1) {
                buflen = Util_sun_nio_fs_UnixNativeDispatcher.ENT_BUF_SIZE;
            }
            pwbuf = LibC.malloc(WordFactory.unsigned(buflen));
            if (pwbuf.isNull()) {
                throw throwOutOfMemoryError("native heap");
            } else {
                passwd pwent = StackValue.get(passwd.class);
                passwdPointer p = StackValue.get(passwdPointer.class);
                p.write(WordFactory.nullPointer());
                int res = 0;
                CCharPointer name = WordFactory.pointer(nameAddress);

                // errno = 0;
                Errno.set_errno(0);
                // #ifdef __solaris__
                // RESTARTABLE_RETURN_PTR(getpwnam_r(name, &pwent, pwbuf, (Unsigned)buflen), p);
                // #else
                do {
                    res = getpwnam_r(name, pwent, pwbuf, WordFactory.unsigned(buflen), p);
                } while ((res == -1) && (errno() == EINTR()));
                // #endif

                if (res != 0 || p.read().isNull() || p.read().pw_name().isNull() || p.read().pw_name().read() == 0) {
                    /* not found or error */
                    if (errno() != 0 && errno() != ENOENT() && errno() != ESRCH()) {
                        throwUnixException(errno());
                    }
                } else {
                    uid = p.read().pw_uid();
                }
                LibC.free(pwbuf);
            }

            return uid;
        }

        @Substitute
        private static int getgrnam0(long nameAddress) throws Exception {
            int gid = -1;
            int buflen;
            boolean retry;

            /* initial size of buffer for group record */
            buflen = (int) sysconf(_SC_GETGR_R_SIZE_MAX());
            if (buflen == -1) {
                buflen = Util_sun_nio_fs_UnixNativeDispatcher.ENT_BUF_SIZE;
            }

            do {
                group grent = StackValue.get(group.class);
                groupPointer g = StackValue.get(groupPointer.class);
                g.write(WordFactory.nullPointer());
                int res = 0;
                CCharPointer grbuf;
                CCharPointer name = WordFactory.pointer(nameAddress);

                grbuf = LibC.malloc(WordFactory.unsigned(buflen));
                if (grbuf.isNull()) {
                    throw throwOutOfMemoryError("native heap");
                }

                // errno = 0;
                Errno.set_errno(0);
                // #ifdef __solaris__
                // RESTARTABLE_RETURN_PTR(getgrnam_r(name, &grent, grbuf, (Unsigned)buflen), g);
                // #else
                do {
                    res = getgrnam_r(name, grent, grbuf, WordFactory.unsigned(buflen), g);
                } while ((res == -1) && (errno() == EINTR()));
                // #endif

                retry = false;
                if (res != 0 || g.read().isNull() || g.read().gr_name().isNull() || g.read().gr_name().read() == 0) {
                    /* not found or error */
                    if (errno() != 0 && errno() != ENOENT() && errno() != ESRCH()) {
                        if (errno() == ERANGE()) {
                            /* insufficient buffer size so need larger buffer */
                            buflen += Util_sun_nio_fs_UnixNativeDispatcher.ENT_BUF_SIZE;
                            retry = true;
                        } else {
                            throwUnixException(errno());
                        }
                    }
                } else {
                    gid = g.read().gr_gid();
                }

                LibC.free(grbuf);

            } while (retry);

            return gid;
        }

        @Substitute
        private static void statvfs0(long pathAddress, Target_sun_nio_fs_UnixFileStoreAttributes attrs) throws Exception {
            int err;
            Statvfs.statvfs buf = StackValue.get(Statvfs.statvfs.class);
            CCharPointer path = WordFactory.pointer(pathAddress);

            do {
                err = Statvfs.statvfs(path, buf);
            } while ((err == -1) && (errno() == EINTR()));

            if (err == -1) {
                throwUnixException(errno());
            } else {
                attrs.f_frsize = buf.f_frsize();
                attrs.f_blocks = buf.f_blocks();
                attrs.f_bfree = buf.f_bfree();
                attrs.f_bavail = buf.f_bavail();
            }
        }

        @Substitute
        private static long pathconf0(long pathAddress, int name) throws Exception {
            long err;
            CCharPointer path = WordFactory.pointer(pathAddress);

            err = pathconf(path, name);
            if (err == -1) {
                throwUnixException(errno());
            }
            return err;
        }

        @Substitute
        private static long fpathconf(int fd, int name) throws Exception {
            long err;

            err = fpathconf(fd, name);
            if (err == -1) {
                throwUnixException(errno());
            }
            return err;
        }

        @Substitute
        private static byte[] strerror(int errnum) {
            return toByteArray(Errno.strerror(errnum));
        }

        @Substitute
        private static int init() {
            throw new InternalError("init() is only called from static initializers, so not reachable in Substrate VM");
        }

        @Substitute
        private static boolean openatSupported() {
            /*
             * Originally, this initialization is done in the static initializer. We do not have
             * that option available in the Substrate VM, so we need to check every time we access
             * the flag.
             */
            if (!Util_sun_nio_fs_UnixNativeDispatcher.initialized) {
                Util_sun_nio_fs_UnixNativeDispatcher.initialize();
            }
            return Util_sun_nio_fs_UnixNativeDispatcher.hasAtSysCalls;
        }
    }

    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Util_sun_nio_fs_UnixNativeDispatcher {
        /**
         * Size of password or group entry when not available via sysconf.
         */
        static final int ENT_BUF_SIZE = 1024;

        // Checkstyle: stop
        static openat64_func my_openat64_func;
        static fstatat64_func my_fstatat64_func;
        static unlinkat_func my_unlinkat_func;
        static renameat_func my_renameat_func;
        static futimesat_func my_futimesat_func;
        static fdopendir_func my_fdopendir_func;
        // Checkstyle: resume

        static volatile boolean initialized;

        // indicates if openat, unlinkat, etc. is supported
        private static boolean hasAtSysCalls;

        static void initialize() {
            /*
             * It does not hurt if we execute this initialization code multiple times in different
             * threads, so no synchronization is necessary. The "initialized" flag is volatile so
             * that we do not claim initialization prematurely, before all other fields are set.
             */

            /* system calls that might not be available at run time */
            // #if (defined(__solaris__) && defined(_LP64)) || defined(_ALLBSD_SOURCE)
            // /* Solaris 64-bit does not have openat64/fstatat64 */
            // my_openat64_func = (openat64_func*)dlsym(RTLD_DEFAULT, "openat");
            // my_fstatat64_func = (fstatat64_func*)dlsym(RTLD_DEFAULT, "fstatat");
            // #else
            my_openat64_func = dlsym(RTLD_DEFAULT(), "openat64");
            my_fstatat64_func = dlsym(RTLD_DEFAULT(), "fstatat64");
            // #endif
            my_unlinkat_func = dlsym(RTLD_DEFAULT(), "unlinkat");
            my_renameat_func = dlsym(RTLD_DEFAULT(), "renameat");
            my_futimesat_func = dlsym(RTLD_DEFAULT(), "futimesat");
            my_fdopendir_func = dlsym(RTLD_DEFAULT(), "fdopendir");

            // #if defined(FSTATAT64_SYSCALL_AVAILABLE)
            // /* fstatat64 missing from glibc */
            // if (my_fstatat64_func == NULL)
            // my_fstatat64_func = (fstatat64_func*)&fstatat64_wrapper;
            // #endif

            hasAtSysCalls = !my_openat64_func.isNull() && !my_fstatat64_func.isNull() && !my_unlinkat_func.isNull() && !my_renameat_func.isNull() &&
                            !my_futimesat_func.isNull() && !my_fdopendir_func.isNull();
            initialized = true;
        }

        /**
         * Copy stat64 members into sun.nio.fs.UnixFileAttributes.
         */
        static void prepAttributes(Stat.stat buf, Target_sun_nio_fs_UnixFileAttributes attrs) {
            Target_sun_nio_fs_UnixFileAttributes alias = attrs;
            alias.st_mode = buf.st_mode();
            alias.st_ino = buf.st_ino();
            alias.st_dev = buf.st_dev();
            alias.st_rdev = buf.st_rdev();
            alias.st_nlink = (int) buf.st_nlink();
            alias.st_uid = buf.st_uid();
            alias.st_gid = buf.st_gid();
            alias.st_size = buf.st_size();
            alias.st_atime_sec = buf.st_atime();
            alias.st_mtime_sec = buf.st_mtime();
            alias.st_ctime_sec = buf.st_ctime();
        }

    }

    /*
     * Converted from JDK 7 update 40 C source file:
     * src/solaris/native/sun/nio/fs/LinuxNativeDispatcher.c
     */
    // Checkstyle: stop
    interface fgetxattr_func extends CFunctionPointer {
        @InvokeCFunctionPointer
        UnsignedWord invoke(int fd, CCharPointer name, PointerBase value, UnsignedWord size);
    }

    interface fsetxattr_func extends CFunctionPointer {
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer name, PointerBase value, UnsignedWord size, int flags);
    }

    interface fremovexattr_func extends CFunctionPointer {
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer name);
    }

    interface flistxattr_func extends CFunctionPointer {
        @InvokeCFunctionPointer
        int invoke(int fd, CCharPointer list, UnsignedWord size);
    }

    // Checkstyle: resume

    @TargetClass(className = "sun.nio.fs.LinuxNativeDispatcher")
    @Platforms(Platform.LINUX.class)
    static final class Target_sun_nio_fs_LinuxNativeDispatcher {

        @Substitute
        private static long setmntent0(long pathAddress, long modeAddress) throws Exception {
            FILE fp;
            CCharPointer path = WordFactory.pointer(pathAddress);
            CCharPointer mode = WordFactory.pointer(modeAddress);

            do {
                fp = setmntent(path, mode);
            } while (fp.isNull() && errno() == EINTR());
            if (fp.isNull()) {
                throw throwUnixException(errno());
            }
            return fp.rawValue();
        }

        @Substitute
        private static int getmntent(long value, Target_sun_nio_fs_UnixMountEntry entry) {
            mntent ent = StackValue.get(mntent.class);
            int buflen = 1024;
            CCharPointer buf = StackValue.get(buflen, CCharPointer.class);
            FILE fp = WordFactory.pointer(value);

            mntent m = getmntent_r(fp, ent, buf, buflen);
            if (m.isNull()) {
                return -1;
            }
            entry.name = toByteArray(m.mnt_fsname());
            entry.dir = toByteArray(m.mnt_dir());
            entry.fstype = toByteArray(m.mnt_type());
            entry.opts = toByteArray(m.mnt_opts());

            return 0;
        }

        @Substitute
        private static void endmntent(long stream) {
            FILE fp = WordFactory.pointer(stream);
            /* FIXME - man page doesn't explain how errors are returned */
            Mntent.endmntent(fp);
        }

        @Substitute
        private static int fgetxattr0(int fd, long nameAddress, long valueAddress, int valueLen) throws Exception {
            int res;
            CCharPointer name = WordFactory.pointer(nameAddress);
            PointerBase value = WordFactory.pointer(valueAddress);

            if (!Util_sun_nio_fs_LinuxNativeDispatcher.initialized) {
                Util_sun_nio_fs_LinuxNativeDispatcher.initialize();
            }
            if (Util_sun_nio_fs_LinuxNativeDispatcher.my_fgetxattr_func.isNull()) {
                throw throwUnixException(ENOTSUP());
            } else {
                /* EINTR not documented */
                res = (int) Util_sun_nio_fs_LinuxNativeDispatcher.my_fgetxattr_func.invoke(fd, name, value, WordFactory.unsigned(valueLen)).rawValue();
            }
            if (res == -1) {
                throw throwUnixException(errno());
            }
            return res;
        }

        @Substitute
        private static void fsetxattr0(int fd, long nameAddress, long valueAddress, int valueLen) throws Exception {
            int res;
            CCharPointer name = WordFactory.pointer(nameAddress);
            PointerBase value = WordFactory.pointer(valueAddress);

            if (!Util_sun_nio_fs_LinuxNativeDispatcher.initialized) {
                Util_sun_nio_fs_LinuxNativeDispatcher.initialize();
            }
            if (Util_sun_nio_fs_LinuxNativeDispatcher.my_fsetxattr_func.isNull()) {
                throw throwUnixException(ENOTSUP());
            } else {
                /* EINTR not documented */
                res = Util_sun_nio_fs_LinuxNativeDispatcher.my_fsetxattr_func.invoke(fd, name, value, WordFactory.unsigned(valueLen), 0);
            }
            if (res == -1) {
                throw throwUnixException(errno());
            }
        }

        @Substitute
        private static void fremovexattr0(int fd, long nameAddress) throws Exception {
            int res;
            CCharPointer name = WordFactory.pointer(nameAddress);

            if (!Util_sun_nio_fs_LinuxNativeDispatcher.initialized) {
                Util_sun_nio_fs_LinuxNativeDispatcher.initialize();
            }
            if (Util_sun_nio_fs_LinuxNativeDispatcher.my_fremovexattr_func.isNull()) {
                throw throwUnixException(ENOTSUP());
            } else {
                /* EINTR not documented */
                res = Util_sun_nio_fs_LinuxNativeDispatcher.my_fremovexattr_func.invoke(fd, name);
            }
            if (res == -1) {
                throw throwUnixException(errno());
            }
        }

        @Substitute
        private static int flistxattr(int fd, long listAddress, int size) throws Exception {
            int res;
            CCharPointer list = WordFactory.pointer(listAddress);

            if (!Util_sun_nio_fs_LinuxNativeDispatcher.initialized) {
                Util_sun_nio_fs_LinuxNativeDispatcher.initialize();
            }
            if (Util_sun_nio_fs_LinuxNativeDispatcher.my_flistxattr_func.isNull()) {
                throw throwUnixException(ENOTSUP());
            } else {
                /* EINTR not documented */
                res = Util_sun_nio_fs_LinuxNativeDispatcher.my_flistxattr_func.invoke(fd, list, WordFactory.unsigned(size));
            }
            if (res == -1) {
                throw throwUnixException(errno());
            }
            return res;
        }

        @Substitute
        private static void init() {
            throw new InternalError("init() is only called from static initializers, so not reachable in Substrate VM");
        }

    }

    @Platforms(Platform.LINUX.class)
    static final class Util_sun_nio_fs_LinuxNativeDispatcher {

        // Checkstyle: stop
        static fgetxattr_func my_fgetxattr_func;
        static fsetxattr_func my_fsetxattr_func;
        static fremovexattr_func my_fremovexattr_func;
        static flistxattr_func my_flistxattr_func;
        // Checkstyle: resume

        static volatile boolean initialized;

        static void initialize() {
            my_fgetxattr_func = dlsym(RTLD_DEFAULT(), "fgetxattr");
            my_fsetxattr_func = dlsym(RTLD_DEFAULT(), "fsetxattr");
            my_fremovexattr_func = dlsym(RTLD_DEFAULT(), "fremovexattr");
            my_flistxattr_func = dlsym(RTLD_DEFAULT(), "flistxattr");

            initialized = true;
        }
    }

    /*
     * Converted from JDK 7 update 40 C source file:
     * src/solaris/native/sun/nio/fs/MacOSXNativeDispatcher.c
     */

    @TargetClass(className = "sun.nio.fs.MacOSXNativeDispatcher")
    @Platforms(Platform.DARWIN.class)
    static final class Target_sun_nio_fs_MacOSXNativeDispatcher {

        @Substitute
        private static char[] normalizepath(char[] path, int form) {
            CoreFoundation.CFMutableStringRef csref = DarwinCoreFoundationUtils.toCFStringRef(String.valueOf(path));
            CoreFoundation.CFStringNormalize(csref, WordFactory.signed(form));
            String res = DarwinCoreFoundationUtils.fromCFStringRef(csref);
            CFRelease(csref);
            return res.toCharArray();
        }
    }

    /*
     * Converted from JDK 7 update 40 C source file: src/solaris/native/sun/nio/ch/FileChannelImpl.c
     */

    @TargetClass(className = "sun.nio.ch.FileChannelImpl")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    @SuppressWarnings("static-method")
    static final class Target_sun_nio_ch_FileChannelImpl {
        @Alias @TargetElement(name = "fd") private FileDescriptor fdfield;

        // Checkstyle: stop
        @Alias private static int MAP_RO;
        @Alias private static int MAP_RW;
        @Alias private static int MAP_PV;

        // Checkstyle: resume

        @Substitute
        private static long initIDs() {
            throw new InternalError("initIDs() is only called from static initializers, so not reachable in Substrate VM");
        }

        @Substitute
        private long map0(int prot, long off, long len) throws IOException {
            PointerBase mapAddress;
            int fd = fdval(fdfield);
            int protections = 0;
            int flags = 0;

            if (prot == MAP_RO) {
                protections = PROT_READ();
                flags = MAP_SHARED();
            } else if (prot == MAP_RW) {
                protections = PROT_WRITE() | PROT_READ();
                flags = MAP_SHARED();
            } else if (prot == MAP_PV) {
                protections = PROT_WRITE() | PROT_READ();
                flags = MAP_PRIVATE();
            }

            mapAddress = mmap(WordFactory.nullPointer(), /* Let OS decide location */
                            WordFactory.unsigned(len), /* Number of bytes to map */
                            protections, /* File permissions */
                            flags, /* Changes are shared */
                            fd, /* File descriptor of mapped file */
                            off); /* Offset into file */

            if (mapAddress.equal(MAP_FAILED())) {
                if (errno() == ENOMEM()) {
                    throw throwOutOfMemoryError("Map failed");
                }
                return handle(-1, "Map failed");
            }

            return mapAddress.rawValue();
        }

        @Substitute
        private static int unmap0(long address, long len) throws IOException {
            PointerBase a = WordFactory.pointer(address);
            return handle(munmap(a, WordFactory.unsigned(len)), "Unmap failed");
        }

        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        private long position0(FileDescriptor fdo, long offset) throws IOException {
            int fd = fdval(fdo);
            long result = 0;

            if (offset < 0) {
                result = lseek(fd, WordFactory.zero(), SEEK_CUR()).rawValue();
            } else {
                result = lseek(fd, WordFactory.signed(offset), SEEK_SET()).rawValue();
            }
            return handle(result, "Position failed");
        }

        // @Substitute
        // private static void close0(FileDescriptor fdo) throws IOException {
        // int fd = fdval(fdo);
        // if (fd != -1) {
        // long result = close(fd);
        // if (result < 0) {
        // throw throwIOExceptionWithLastError("Close failed");
        // }
        // }
        // }

        /*
         * JDK 8 udpate 60 changed the signature of trasferTo0 from "int" to "FileDescriptor" for
         * "src" and "dst".
         */

        @Substitute
        @TargetElement(name = "transferTo0")
        @Platforms(Platform.LINUX.class)
        private long transferTo0Linux(FileDescriptor src, long position, long count, FileDescriptor dst) throws IOException {
            CLongPointer offset = StackValue.get(CLongPointer.class);
            offset.write(position);

            SignedWord n = sendfile(fdval(dst), fdval(src), offset, WordFactory.unsigned(count));
            if (n.lessThan(0)) {
                if (errno() == EAGAIN()) {
                    return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
                }
                if ((errno() == EINVAL()) && count >= 0) {
                    return Target_sun_nio_ch_IOStatus.IOS_UNSUPPORTED_CASE;
                }
                if (errno() == EINTR()) {
                    return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                }
                throw throwIOExceptionWithLastError("Transfer failed");
            }
            return n.rawValue();
        }

        @Substitute
        @TargetElement(name = "transferTo0")
        @Platforms(Platform.DARWIN.class)
        private long transferTo0Darwin(FileDescriptor src, long position, long count, FileDescriptor dst) throws IOException {
            CLongPointer numBytes = StackValue.get(CLongPointer.class);
            int result;

            numBytes.write(count);

            result = sendfile(fdval(src), fdval(dst), position, numBytes, WordFactory.nullPointer(), 0);

            if (numBytes.read() > 0) {
                return numBytes.rawValue();
            }
            if (result == -1) {
                if (errno() == EAGAIN()) {
                    return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
                }
                if (errno() == EOPNOTSUPP() || errno() == ENOTSOCK() || errno() == ENOTCONN()) {
                    return Target_sun_nio_ch_IOStatus.IOS_UNSUPPORTED_CASE;
                }
                if ((errno() == EINVAL()) && count >= 0) {
                    return Target_sun_nio_ch_IOStatus.IOS_UNSUPPORTED_CASE;
                }
                if (errno() == EINTR()) {
                    return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                }
                throw throwIOExceptionWithLastError("Transfer failed");
            }

            return result;
        }
    }

    /*
     * Converted from JDK 7 update 40 C source file: src/solaris/native/sun/nio/ch/FileKey.c
     */

    @TargetClass(className = "sun.nio.ch.FileKey")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_FileKey {
        // Checkstyle: stop
        @Alias long st_dev;
        @Alias long st_ino;

        // Checkstule: resume

        @Substitute
        private static void initIDs() {
            throw new InternalError("initIDs() is only called from static initializers, so not reachable in Substrate VM");
        }

        @Substitute
        private void init(FileDescriptor fdo) throws IOException {
            Stat.stat fbuf = StackValue.get(Stat.stat.class);
            int res;

            do {
                res = fstat(fdval(fdo), fbuf);
            } while ((res == -1) && (errno() == EINTR()));

            if (res < 0) {
                throw throwIOExceptionWithLastError("fstat64 failed");
            } else {
                st_dev = fbuf.st_dev();
                st_ino = fbuf.st_ino();
            }
        }
    }

    /*
     * Converted from JDK 7 update 40 C source file: src/solaris/native/sun/nio/fs/UnixCopyFile.c
     */

    @TargetClass(className = "sun.nio.fs.UnixCopyFile")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixCopyFile {

        /**
         * Transfer all bytes from src to dst via user-space buffers.
         */
        @Substitute
        private static void transfer(int dst, int src, long cancelAddress) throws Exception {
            int bufsize = 8192;
            CCharPointer buf = StackValue.get(bufsize, CCharPointer.class);
            CIntPointer cancel = WordFactory.pointer(cancelAddress);

            for (;;) {
                SignedWord n;
                SignedWord pos;
                SignedWord len;
                do {
                    n = read(src, buf, WordFactory.unsigned(bufsize));
                } while (n.equal(-1) && (errno() == EINTR()));

                if (n.lessOrEqual(0)) {
                    if (n.lessThan(0)) {
                        throw throwUnixException(errno());
                    }
                    return;
                }
                if (!cancel.isNull() && cancel.read() != 0) {
                    throw throwUnixException(ECANCELED());
                }
                pos = WordFactory.zero();
                len = n;
                do {
                    CCharPointer bufp = buf;
                    bufp = bufp.addressOf(pos);
                    do {
                        n = write(dst, bufp, (UnsignedWord) len);
                    } while (n.equal(-1) && (errno() == EINTR()));

                    if (n.equal(-1)) {
                        throw throwUnixException(errno());
                    }
                    pos = pos.add(n);
                    len = len.subtract(n);
                } while (len.greaterThan(0));
            }
        }
    }

    /** This class exists in JDK-9, but these methods do not. */
    @TargetClass(className = "java.nio.Bits", onlyWith = JDK8OrEarlier.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_java_nio_Bits {

        @Substitute
        private static void copyFromShortArray(Object src, long srcPos, long dstAddr, long length) {
            Pointer dstPointer = WordFactory.pointer(dstAddr);
            SignedWord srcOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Short) + srcPos);

            for (SignedWord i = WordFactory.zero(); i.lessOrEqual(WordFactory.signed(length)); i = i.add(2)) {
                dstPointer.writeShort(i, Short.reverseBytes(ObjectAccess.readShort(src, srcOffset.add(i))));
            }
        }

        @Substitute
        private static void copyToShortArray(long srcAddr, Object dst, long dstPos, long length) {
            Pointer srcPointer = WordFactory.pointer(srcAddr);
            SignedWord dstOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Short) + dstPos);

            for (SignedWord i = WordFactory.zero(); i.lessOrEqual(WordFactory.signed(length)); i = i.add(2)) {
                ObjectAccess.writeShort(dst, dstOffset.add(i), Short.reverseBytes(srcPointer.readShort(i)));
            }
        }

        @Substitute
        private static void copyFromIntArray(Object src, long srcPos, long dstAddr, long length) {
            Pointer dstPointer = WordFactory.pointer(dstAddr);
            SignedWord srcOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Int) + srcPos);

            for (SignedWord i = WordFactory.zero(); i.lessOrEqual(WordFactory.signed(length)); i = i.add(4)) {
                dstPointer.writeInt(i, Integer.reverseBytes(ObjectAccess.readInt(src, srcOffset.add(i))));
            }
        }

        @Substitute
        private static void copyToIntArray(long srcAddr, Object dst, long dstPos, long length) {
            Pointer srcPointer = WordFactory.pointer(srcAddr);
            SignedWord dstOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Int) + dstPos);

            for (SignedWord i = WordFactory.zero(); i.lessOrEqual(WordFactory.signed(length)); i = i.add(4)) {
                ObjectAccess.writeInt(dst, dstOffset.add(i), Integer.reverseBytes(srcPointer.readInt(i)));
            }
        }

        @Substitute
        private static void copyFromLongArray(Object src, long srcPos, long dstAddr, long length) {
            Pointer dstPointer = WordFactory.pointer(dstAddr);
            SignedWord srcOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Long) + srcPos);

            for (SignedWord i = WordFactory.zero(); i.lessOrEqual(WordFactory.signed(length)); i = i.add(8)) {
                dstPointer.writeLong(i, Long.reverseBytes(ObjectAccess.readLong(src, srcOffset.add(i))));
            }
        }

        @Substitute
        private static void copyToLongArray(long srcAddr, Object dst, long dstPos, long length) {
            Pointer srcPointer = WordFactory.pointer(srcAddr);
            SignedWord dstOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Short) + dstPos);

            for (SignedWord i = WordFactory.zero(); i.lessOrEqual(WordFactory.signed(length)); i = i.add(8)) {
                ObjectAccess.writeLong(dst, dstOffset.add(i), Long.reverseBytes(srcPointer.readLong(i)));
            }
        }
    }

    @TargetClass(className = "sun.nio.ch.SocketChannelImpl")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_SocketChannelImpl {

        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        // /jdk/src/share/classes/sun/nio/ch/SocketChannelImpl.java?v=Java_1.8.0_40_b10
        // 1027 private static native int checkConnect(FileDescriptor fd,
        // 1028 boolean block, boolean ready)
        // 1029 throws IOException;
        //
        // jdk/src/solaris/native/sun/nio/ch/SocketChannelImpl.c?v=Java_1.8.0_40_b10
        // 048 JNIEXPORT jint JNICALL
        // 049 Java_sun_nio_ch_SocketChannelImpl_checkConnect(JNIEnv *env, jobject this,
        // 050 jobject fdo, jboolean block,
        // 051 jboolean ready)
        // 052 {
        static int checkConnect(FileDescriptor fdo, boolean block, boolean ready) throws IOException {
            // 053 int error = 0;
            CIntPointer error_Pointer = StackValue.get(CIntPointer.class);
            error_Pointer.write(0);
            // 054 socklen_t n = sizeof(int);
            CIntPointer n_Pointer = StackValue.get(CIntPointer.class);
            n_Pointer.write(SizeOf.get(CIntPointer.class));
            // 055 jint fd = fdval(env, fdo);
            int fd = fdval(fdo);
            // 056 int result = 0;
            int result = 0;
            // 057 struct pollfd poller;
            Poll.pollfd poller = StackValue.get(Poll.pollfd.class);
            // 058
            // 059 poller.revents = 1;
            poller.set_revents(1);
            // 060 if (!ready) {
            if (!ready) {
                // 061 poller.fd = fd;
                poller.set_fd(fd);
                // 062 poller.events = POLLOUT;
                poller.set_events(Poll.POLLOUT());
                // 063 poller.revents = 0;
                poller.set_revents(0);
                // 064 result = poll(&poller, 1, block ? -1 : 0);
                result = Poll.poll(poller, 1, block ? -1 : 0);
                // 065 if (result < 0) {
                if (result < 0) {
                    // 066 JNU_ThrowIOExceptionWithLastError(env, "Poll failed");
                    throw new IOException("Poll failed");
                    // 067 return IOS_THROWN;
                    /* unreachable! */
                }
                // 069 if (!block && (result == 0))
                if (!block && (result == 0)) {
                    // 070 return IOS_UNAVAILABLE;
                    return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
                }
            }
            // 072
            // 073 if (poller.revents) {
            if (CTypeConversion.toBoolean(poller.revents())) {
                // 074 errno = 0;
                Errno.set_errno(0);
                // 075 result = getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &n);
                result = Socket.getsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_ERROR(), error_Pointer, n_Pointer);
                // 076 if (result < 0) {
                if (result < 0) {
                    // 077 handleSocketError(env, errno);
                    Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
                    // 078 return JNI_FALSE;
                    return Target_jni.JNI_FALSE();
                    // 079 } else if (error) {
                } else if (CTypeConversion.toBoolean(error_Pointer.read())) {
                    // 080 handleSocketError(env, error);
                    Util_sun_nio_ch_Net.handleSocketError(error_Pointer.read());
                    // 081 return JNI_FALSE;
                    return Target_jni.JNI_FALSE();
                }
                // 083 return 1;
                return 1;
            }
            // 085 return 0;
            return 0;
        }

        /* { Do not format quoted code: @formatter:off */
        @Substitute
        @TargetElement(onlyWith = JDK9OrLater.class)
        /* open/src/java.base/share/classes/sun/nio/ch/SocketChannelImpl.java */
        // 1120    private static native int checkConnect(FileDescriptor fd, boolean block)
        // 1121        throws IOException;
        /* open/src/java.base/unix/native/libnio/ch/SocketChannelImpl.c */
        // 48 JNIEXPORT jint JNICALL
        // 49 Java_sun_nio_ch_SocketChannelImpl_checkConnect(JNIEnv *env, jobject this,
        // 50                                                jobject fdo, jboolean block)
        // 51 {
        static int checkConnect(FileDescriptor fdo, boolean block) throws IOException {
            // 52     int error = 0;
            CIntPointer error_Pointer = StackValue.get(CIntPointer.class);
            error_Pointer.write(0);
            // 53     socklen_t n = sizeof(int);
            CIntPointer n_Pointer = StackValue.get(CIntPointer.class);
            n_Pointer.write(SizeOf.get(CIntPointer.class));
            // 54     jint fd = fdval(env, fdo);
            int fd = fdval(fdo);
            // 55     int result = 0;
            int result = 0;
            // 56     struct pollfd poller;
            Poll.pollfd poller = StackValue.get(Poll.pollfd.class);
            // 57
            // 58     poller.fd = fd;
            poller.set_fd(fd);
            // 59     poller.events = POLLOUT;
            poller.set_events(Poll.POLLOUT());
            // 60     poller.revents = 0;
            poller.set_revents(0);
            // 61     result = poll(&poller, 1, block ? -1 : 0);
            result = Poll.poll(poller, 1, block ? -1 : 0);
            // 62
            // 63     if (result < 0) {
            if (result < 0) {
                // 64         if (errno == EINTR) {
                if (Errno.errno() == Errno.EINTR()) {
                    // 65             return IOS_INTERRUPTED;
                    return Target_sun_nio_ch_IOStatus.IOS_INTERRUPTED;
                } else {
                    // 67             JNU_ThrowIOExceptionWithLastError(env, "poll failed");
                    throw throwIOExceptionWithLastError("poll failed");
                    // 68             return IOS_THROWN;
                    /* unreachable! */
                }
            }
            // 71     if (!block && (result == 0))
            if (!block && (result == 0)) {
                // 72         return IOS_UNAVAILABLE;
                return Target_sun_nio_ch_IOStatus.IOS_UNAVAILABLE;
            }
            // 73
            // 74     if (result > 0) {
            if (result > 0) {
                // 75         errno = 0;
                Errno.set_errno(0);
                // 76         result = getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &n);
                result = Socket.getsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_ERROR(), error_Pointer, n_Pointer);
                // 77         if (result < 0) {
                if (result < 0) {
                    // 78             return handleSocketError(env, errno);
                    return Util_sun_nio_ch_Net.handleSocketError(Errno.errno());
                    // 79         } else if (error) {
                } else if (CTypeConversion.toBoolean(error_Pointer.read())) {
                    // 80             return handleSocketError(env, error);
                    return Util_sun_nio_ch_Net.handleSocketError(error_Pointer.read());
                    // 81         } else if ((poller.revents & POLLHUP) != 0) {
                } else if ((poller.revents() & Poll.POLLHUP()) != 0) {
                    // 82             return handleSocketError(env, ENOTCONN);
                    return Util_sun_nio_ch_Net.handleSocketError(Errno.ENOTCONN());
                }
                // 84         // connected
                // 85         return 1;
                return 1;
            }
            // 87     return 0;
            return 0;
        }
        /* } Do not format quoted code: @formatter:on */
    }

    @TargetClass(className = "sun.nio.ch.UnixAsynchronousSocketChannelImpl")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_UnixAsynchronousSocketChannelImpl {

        /* { Do not format quoted code: @formatter:off */

        /* Translated from src/solaris/native/sun/nio/ch/UnixAsynchronousSocketChannelImpl.c?v=Java_1.8.0_40_b10 */
        @Substitute
        // 038 JNIEXPORT void JNICALL
        // 039 Java_sun_nio_ch_UnixAsynchronousSocketChannelImpl_checkConnect(JNIEnv *env,
        // 040     jobject this, int fd)
        static void checkConnect(int fd) throws IOException {
            // 042     int error = 0;
            CIntPointer errorPointer = StackValue.get(CIntPointer.class);
            errorPointer.write(0);
            // 043     socklen_t arglen = sizeof(error);
            CIntPointer arglenPointer = StackValue.get(CIntPointer.class);
            arglenPointer.write(SizeOf.get(CIntPointer.class));
            // 044     int result;
            int result;
            // 045
            // 046     result = getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &arglen);
            result = Socket.getsockopt(fd, Socket.SOL_SOCKET(), Socket.SO_ERROR(), errorPointer, arglenPointer);
            // 047     if (result < 0) {
            if (result < 0) {
                // 048         JNU_ThrowIOExceptionWithLastError(env, "getsockopt");
                PosixJavaNIOSubstitutions.throwIOExceptionWithLastError("getsockopt");
            } else {
                // 050         if (error)
                if (CTypeConversion.toBoolean(errorPointer.read())) {
                    // 051             handleSocketError(env, error);
                    Util_sun_nio_ch_Net.handleSocketError(errorPointer.read());
                }
            }
        }
    }

    @TargetClass(className = "sun.nio.ch.UnixAsynchronousServerSocketChannelImpl")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_ch_UnixAsynchronousServerSocketChannelImpl {

        /* { Do not format quoted code: @formatter:off */

        /* Translated from src/solaris/native/sun/nio/ch/UnixAsynchronousServerSocketChannelImpl.c?v=Java_1.8.0_40_b10 */
        @Substitute
        // 041 JNIEXPORT jint JNICALL
        // 042 Java_sun_nio_ch_UnixAsynchronousServerSocketChannelImpl_accept0(JNIEnv* env,
        // 043     jobject this, jobject ssfdo, jobject newfdo, jobjectArray isaa)
        @SuppressWarnings({"static-method"})
        int accept0(FileDescriptor ssfd, FileDescriptor newfd, InetSocketAddress[] isaa) throws IOException {
            // 045     return Java_sun_nio_ch_ServerSocketChannelImpl_accept0(env, this,
            // 046         ssfdo, newfdo, isaa);
            /* Ignore the receiver. */
            return Util_sun_nio_ch_ServerSocketChannelImpl.accept0(ssfd, newfd, isaa);
        }

        /* } Do not format quoted code: @formatter:on */
    }

    @TargetClass(className = "sun.nio.fs.UnixFileSystem")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixFileSystem {
    }

    @TargetClass(className = "sun.nio.fs.UnixFileSystemProvider")
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_sun_nio_fs_UnixFileSystemProvider {
        @Alias
        native Target_sun_nio_fs_UnixFileSystem newFileSystem(String s);
    }

    static final class Util_Target_java_nio_file_FileSystems {
        static FileSystemProvider defaultProvider = FileSystems.getDefault().provider();
        static Target_sun_nio_fs_UnixFileSystem defaultFilesystem;
    }

    @TargetClass(FileSystems.class)
    @Platforms({Platform.LINUX.class, Platform.DARWIN.class})
    static final class Target_java_nio_file_FileSystems {
        @Substitute
        static FileSystem getDefault() {
            if (Util_Target_java_nio_file_FileSystems.defaultFilesystem == null) {
                String userDir = System.getProperty("user.dir");
                Target_sun_nio_fs_UnixFileSystemProvider provider = Target_sun_nio_fs_UnixFileSystemProvider.class.cast(Util_Target_java_nio_file_FileSystems.defaultProvider);
                Util_Target_java_nio_file_FileSystems.defaultFilesystem = provider.newFileSystem(userDir);
            }
            return FileSystem.class.cast(Util_Target_java_nio_file_FileSystems.defaultFilesystem);
        }

        @Delete
        @TargetClass(value = FileSystems.class, innerClass = "DefaultFileSystemHolder")
        static final class Target_java_nio_file_FileSystems_DefaultFileSystemHolder {
        }
    }

    /*
     * Translated from:
     * jdk/src/solaris/native/sun/nio/fs/GnomeFileTypeDetector.c?v=Java_1.8.0_40_b10
     */
    @Platforms({Platform.LINUX.class})
    @TargetClass(className = "sun.nio.fs.GnomeFileTypeDetector", onlyWith = JDK8OrEarlier.class)
    static final class Target_sun_nio_fs_GnomeFileTypeDetector {

        /* { Do not format quoted code: @formatter:off */

        @Substitute
        // 086 JNIEXPORT jboolean JNICALL
        // 087 Java_sun_nio_fs_GnomeFileTypeDetector_initializeGio
        // 088     (JNIEnv* env, jclass this)
        // 089 {
        static boolean initializeGio() {
            final Log trace = Log.noopLog()
                            .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_GnomeFileTypeDetector.initializeGio:")
                            .newline();
            // 090     void* gio_handle;
            WordPointer gio_handle = WordFactory.nullPointer();
            // 092     gio_handle = dlopen("libgio-2.0.so", RTLD_LAZY);
            try (CCharPointerHolder libgio_2_0_so_Holder = CTypeConversion.toCString("libgio-2.0.so")) {
                gio_handle = (WordPointer) Dlfcn.dlopen(libgio_2_0_so_Holder.get(), Dlfcn.RTLD_LAZY());
                // 093     if (gio_handle == NULL) {
                if (gio_handle.isNull()) {
                    // 094         gio_handle = dlopen("libgio-2.0.so.0", RTLD_LAZY);
                    try (CCharPointerHolder libgio_2_0_so_0_Holder = CTypeConversion.toCString("libgio-2.0.so.0")) {
                        gio_handle = (WordPointer) Dlfcn.dlopen(libgio_2_0_so_0_Holder.get(), Dlfcn.RTLD_LAZY());
                        // 095         if (gio_handle == NULL) {
                        if (gio_handle.isNull()) {
                            // 096             return JNI_FALSE;
                            trace.string("  libgio not found: returns false]").newline();
                            return false;
                        }
                    }
                }
            }
            // 100     g_type_init = (g_type_init_func)dlsym(gio_handle, "g_type_init");
            try (CCharPointerHolder g_type_init_Holder = CTypeConversion.toCString("g_type_init")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.g_type_init = Dlfcn.dlsym(gio_handle, g_type_init_Holder.get());
            }
            // 101     (*g_type_init)();
            Util_sun_nio_fs_GnomeFileTypeDetector.g_type_init.invoke();
            // 102
            // 103     g_object_unref = (g_object_unref_func)dlsym(gio_handle, "g_object_unref");
            try (CCharPointerHolder g_object_unref_Holder = CTypeConversion.toCString("g_object_unref")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.g_object_unref = Dlfcn.dlsym(gio_handle, g_object_unref_Holder.get());
            }
            // 105     g_file_new_for_path =
            // 106         (g_file_new_for_path_func)dlsym(gio_handle, "g_file_new_for_path");
            try (CCharPointerHolder g_file_new_for_path_Holder = CTypeConversion.toCString("g_file_new_for_path")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.g_file_new_for_path = Dlfcn.dlsym(gio_handle, g_file_new_for_path_Holder.get());
            }
            // 108     g_file_query_info =
            // 109         (g_file_query_info_func)dlsym(gio_handle, "g_file_query_info");
            try (CCharPointerHolder g_file_query_info_Holder = CTypeConversion.toCString("g_file_query_info")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.g_file_query_info = Dlfcn.dlsym(gio_handle, g_file_query_info_Holder.get());
            }
            // 111     g_file_info_get_content_type = (g_file_info_get_content_type_func)
            // 112         dlsym(gio_handle, "g_file_info_get_content_type");
            try (CCharPointerHolder g_file_info_get_content_type_Holder = CTypeConversion.toCString("g_file_info_get_content_type")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.g_file_info_get_content_type = Dlfcn.dlsym(gio_handle, g_file_info_get_content_type_Holder.get());
            }
            // 115     if (g_type_init == NULL ||
            // 116         g_object_unref == NULL ||
            // 117         g_file_new_for_path == NULL ||
            // 118         g_file_query_info == NULL ||
            // 119         g_file_info_get_content_type == NULL)
            // 120     {
            if (Util_sun_nio_fs_GnomeFileTypeDetector.g_type_init.isNull() ||
                            Util_sun_nio_fs_GnomeFileTypeDetector.g_object_unref.isNull() ||
                            Util_sun_nio_fs_GnomeFileTypeDetector.g_file_new_for_path.isNull() ||
                            Util_sun_nio_fs_GnomeFileTypeDetector.g_file_query_info.isNull() ||
                            Util_sun_nio_fs_GnomeFileTypeDetector.g_file_info_get_content_type.isNull()) {
                // 121         dlclose(gio_handle);
                Dlfcn.dlclose(gio_handle);
                // 122         return JNI_FALSE;
                trace.string("  symbols not found: returns false]").newline();
                return false;
            }
            // 125     (*g_type_init)();
            Util_sun_nio_fs_GnomeFileTypeDetector.g_type_init.invoke();
            // 126     return JNI_TRUE;
            trace.string("  returns true]").newline();
            return true;
        }

        @Substitute
        // 129 JNIEXPORT jbyteArray JNICALL
        // 130 Java_sun_nio_fs_GnomeFileTypeDetector_probeUsingGio
        // 131     (JNIEnv* env, jclass this, jlong pathAddress)
        // 132 {
        static byte[] probeUsingGio(long pathAddress) {
            final Log trace = Log.noopLog()
                            .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_GnomeFileTypeDetector.probeUsingGio:")
                            .newline();
            // 133     char* path = (char*)jlong_to_ptr(pathAddress);
            CCharPointer path = WordFactory.pointer(pathAddress);
            trace.string("  pathAddress: ").string(path);
            // 134     GFile* gfile;
            Util_sun_nio_fs_GnomeFileTypeDetector.GFile gfile = WordFactory.nullPointer();
            // 135     GFileInfo* gfileinfo;
            Util_sun_nio_fs_GnomeFileTypeDetector.GFileInfo gfileinfo = WordFactory.nullPointer();
            // 136     jbyteArray result = NULL;
            byte[] result = null;
            // 138     gfile = (*g_file_new_for_path)(path);
            gfile = Util_sun_nio_fs_GnomeFileTypeDetector.g_file_new_for_path.invoke(path);
            // 139     gfileinfo = (*g_file_query_info)(gfile, G_FILE_ATTRIBUTE_STANDARD_CONTENT_TYPE,
            // 140         G_FILE_QUERY_INFO_NONE, NULL, NULL);
            try (CCharPointerHolder gfasctHolder = CTypeConversion.toCString(Util_sun_nio_fs_GnomeFileTypeDetector.G_FILE_ATTRIBUTE_STANDARD_CONTENT_TYPE)) {
                gfileinfo = Util_sun_nio_fs_GnomeFileTypeDetector.g_file_query_info.invoke(gfile,
                                gfasctHolder.get(),
                                Util_sun_nio_fs_GnomeFileTypeDetector.G_FILE_QUERY_INFO_NONE,
                                WordFactory.nullPointer(),
                                WordFactory.nullPointer());
            }
            // 141     if (gfileinfo != NULL) {
            if (gfileinfo.isNonNull()) {
                // 142         const char* mime = (*g_file_info_get_content_type)(gfileinfo);
                CCharPointer mime = Util_sun_nio_fs_GnomeFileTypeDetector.g_file_info_get_content_type.invoke(gfileinfo);
                // 143         if (mime != NULL) {
                if (mime.isNonNull()) {
                    // 144             jsize len = strlen(mime);
                    int len = (int) LibC.strlen(mime).rawValue();
                    // 145             result = (*env)->NewByteArray(env, len);
                    result = new byte[len];
                    // 146             if (result != NULL) {
                    /* `new` never returns `null`. */
                    // 147                 (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)mime);
                    VmPrimsJNI.SetByteArrayRegion(result, 0, len, mime);
                }
                // 150         (*g_object_unref)(gfileinfo);
                Util_sun_nio_fs_GnomeFileTypeDetector.g_object_unref.invoke((Util_sun_nio_fs_GnomeFileTypeDetector.gpointer) gfileinfo);
            }
            // 152     (*g_object_unref)(gfile);
            Util_sun_nio_fs_GnomeFileTypeDetector.g_object_unref.invoke((Util_sun_nio_fs_GnomeFileTypeDetector.gpointer) gfile);
            // 154     return result;
            trace.string("  returns:  result: ").object(result).string("]").newline();
            return result;
        }

        @Substitute
        // 157 JNIEXPORT jboolean JNICALL
        // 158 Java_sun_nio_fs_GnomeFileTypeDetector_initializeGnomeVfs
        // 159     (JNIEnv* env, jclass this)
        // 160 {
        static boolean initializeGnomeVfs() {
            final Log trace = Log.noopLog()
                            .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_GnomeFileTypeDetector.initializeGnomeVfs:")
                            .newline();
            // 161     void* vfs_handle;
            WordPointer vfs_handle;
            // 163     vfs_handle = dlopen("libgnomevfs-2.so", RTLD_LAZY);
            try (CCharPointerHolder libgnomevfs_2_so_Holder = CTypeConversion.toCString("libgnomevfs-2.so")) {
                vfs_handle = (WordPointer) Dlfcn.dlopen(libgnomevfs_2_so_Holder.get(), Dlfcn.RTLD_LAZY());
                // 164     if (vfs_handle == NULL) {
                if (vfs_handle.isNull()) {
                    // 165         vfs_handle = dlopen("libgnomevfs-2.so.0", RTLD_LAZY);
                    try (CCharPointerHolder libgnomevfs_2_so_0_Holder = CTypeConversion.toCString("libgnomevfs-2.so.0")) {
                        vfs_handle = (WordPointer) Dlfcn.dlopen(libgnomevfs_2_so_0_Holder.get(), Dlfcn.RTLD_LAZY());
                    }
                }
            }
            // 167     if (vfs_handle == NULL) {
            if (vfs_handle.isNull()) {
                // 168         return JNI_FALSE;
                trace.string("  library not found: returns false]").newline();
                return false;
            }
            // 171     gnome_vfs_init = (gnome_vfs_init_function)dlsym(vfs_handle, "gnome_vfs_init");
            try (CCharPointerHolder gnome_vfs_init_Holder = CTypeConversion.toCString("gnome_vfs_init")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.gnome_vfs_init = Dlfcn.dlsym(vfs_handle, gnome_vfs_init_Holder.get());
            }
            // 172     gnome_vfs_mime_type_from_name = (gnome_vfs_mime_type_from_name_function)
            // 173         dlsym(vfs_handle, "gnome_vfs_mime_type_from_name");
            try (CCharPointerHolder gnome_vfs_mime_type_from_name_Holder = CTypeConversion.toCString("gnome_vfs_mime_type_from_name")) {
                Util_sun_nio_fs_GnomeFileTypeDetector.gnome_vfs_mime_type_from_name =
                                Dlfcn.dlsym(vfs_handle, gnome_vfs_mime_type_from_name_Holder.get());
            }
            // 175     if (gnome_vfs_init == NULL ||
            // 176         gnome_vfs_mime_type_from_name == NULL)
            // 177     {
            if (Util_sun_nio_fs_GnomeFileTypeDetector.gnome_vfs_init.isNull() ||
                            Util_sun_nio_fs_GnomeFileTypeDetector.gnome_vfs_mime_type_from_name.isNull()) {
                // 178         dlclose(vfs_handle);
                Dlfcn.dlclose(vfs_handle);
                // 179         return JNI_FALSE;
                trace.string("  symbols not found: returns false]").newline();
                return false;
            }
            // 182     (*gnome_vfs_init)();
            Util_sun_nio_fs_GnomeFileTypeDetector.gnome_vfs_init.invoke();
            // 183     return JNI_TRUE;
            trace.string("  returns true]").newline();
            return true;
        }

        @Substitute
        // 186 JNIEXPORT jbyteArray JNICALL
        // 187 Java_sun_nio_fs_GnomeFileTypeDetector_probeUsingGnomeVfs
        // 188     (JNIEnv* env, jclass this, jlong pathAddress)
        // 189 {
        static byte[] probeUsingGnomeVfs(long pathAddress) {
            final Log trace = Log.noopLog()
                            .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_GnomeFileTypeDetector.probeUsingGnomeVfs:")
                            .newline();
            // 190     char* path = (char*)jlong_to_ptr(pathAddress);
            CCharPointer path = WordFactory.pointer(pathAddress);
            trace.string("  path: ").string(path).newline();
            // 191     const char* mime = (*gnome_vfs_mime_type_from_name)(path);
            CCharPointer mime = Util_sun_nio_fs_GnomeFileTypeDetector.gnome_vfs_mime_type_from_name.invoke(path);
            // 193     if (mime == NULL) {
            if (mime.isNull()) {
                // 194         return NULL;
                trace.string("  mime.isNull: returns null]").newline();
                return null;
            } else {
                // 196         jbyteArray result;
                byte[] result;
                // 197         jsize len = strlen(mime);
                int len = (int) LibC.strlen(mime).rawValue();
                // 198         result = (*env)->NewByteArray(env, len);
                result = new byte[len];
                // 199         if (result != NULL) {
                /* `new` never returns `null`. */
                // 200             (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)mime);
                VmPrimsJNI.SetByteArrayRegion(result, 0, len, mime);
                // 202         return result;
                trace.string("  returns:  result: ").object(result).string("]").newline();
                return result;
            }
        }

        /* } Do not format quoted code: @formatter:off */

        /** gioAvailable is replaced by an injected field and a get access method. */
        // true if GIO available
        @Alias @InjectAccessors(GioAvailableInjector.class) //
        /* private final */ boolean gioAvailable;

        /**
         * The injected field replacing gioAvailable.
         * This field is null if it is unset, otherwise it is a Boolean.
         */
        @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
        Boolean injectedGioAvailable;

        /** Injected access method for the gioAvailable field. */
        static final class GioAvailableInjector {

            /** The access method for gioAvailable. */
            static boolean getGioAvailable(Target_sun_nio_fs_GnomeFileTypeDetector that) {
                final Log trace = Log.noopLog()
                                .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_GnomeFileTypeDetector.GioAvailableInjector.getGioAvailable:")
                                .newline();
                if (that.injectedGioAvailable == null) {
                    trace.string("  injectedGioAvailable: null").newline();
                    if (initializeGio()) {
                        that.injectedGioAvailable = Boolean.TRUE;
                    } else {
                        that.injectedGioAvailable = Boolean.FALSE;
                    }
                }
                final boolean result = that.injectedGioAvailable.booleanValue();
                trace.string("  returns: ").bool(result).string("]").newline();
                return result;
            }
        }

        /** gnomeVfsAvailable is replaced by an injected field and a get access method. */
        // true if GNOME VFS available and GIO is not available
        @Alias @InjectAccessors(GnomeVfsAvailableInjector.class) //
        /* private final */ boolean gnomeVfsAvailable;

        /**
         * The injected field replacing gnomeVfsAvailable.
         * This field is null if it is unset, otherwise it is a Boolean.
         */
        @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
        Boolean injectedGnomeVfsAvailable;

        /** Injected access method for the gnomeVfsAvailable field. */
        static final class GnomeVfsAvailableInjector {

            /** The access method for gnomeVfsAvailable. */
            static boolean getGnomeVfsAvailable(Target_sun_nio_fs_GnomeFileTypeDetector that) {
                final Log trace = Log.noopLog()
                                .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_GnomeFileTypeDetector.GnomeVfsAvailableInjector.getGnomeVfsAvailable:")
                                .newline();
                if (that.injectedGnomeVfsAvailable == null) {
                    trace.string("  injectedGnomeVfsAvailable: null").newline();
                    if (initializeGnomeVfs()) {
                        that.injectedGnomeVfsAvailable = Boolean.TRUE;
                    } else {
                        that.injectedGnomeVfsAvailable = Boolean.FALSE;
                    }
                }
                final boolean result = that.injectedGnomeVfsAvailable.booleanValue();
                trace.string("  returns: ").bool(result).string("]").newline();
                return result;
            }
        }
    }

    @Platforms({Platform.LINUX.class})
    static final class Util_sun_nio_fs_GnomeFileTypeDetector {

        /* { Do not format quoted code: @formatter:off */
        /* { Allow names with underscores: Checkstyle: stop. */

        // 041
        // 042 /* Definitions for GIO */
        // 043

        // 044 #define G_FILE_ATTRIBUTE_STANDARD_CONTENT_TYPE "standard::content-type"
        static final String G_FILE_ATTRIBUTE_STANDARD_CONTENT_TYPE = "standard::content-type";

        // 046 typedef void* gpointer;
        interface gpointer extends PointerBase {
            /* Opaque. */
        }

        // 047 typedef struct _GFile GFile;
        interface GFile extends PointerBase {
            /* Opaque. */
        }

        // 048 typedef struct _GFileInfo GFileInfo;
        interface GFileInfo extends PointerBase {
            /* Opaque. */
        }

        // 049 typedef struct _GCancellable GCancellable;
        interface GCancellable extends PointerBase {
            /* Opaque. */
        }

        interface GErrorPointer extends PointerBase {
            /* Opaque. */
        }

        // 052 typedef enum {
        // 053   G_FILE_QUERY_INFO_NONE = 0
        // 054 } GFileQueryInfoFlags;
        /* Translating the type `GFileQueryInfoFlags`as `int`. */
        static final int G_FILE_QUERY_INFO_NONE = 0;

        // 056 typedef void (*g_type_init_func)(void);
        interface g_type_init_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            void invoke();
        }

        // 057 typedef void (*g_object_unref_func)(gpointer object);
        interface g_object_unref_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            void invoke(gpointer object);
        }

        // 058 typedef GFile* (*g_file_new_for_path_func)(const char* path);
        interface g_file_new_for_path_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            GFile invoke(CCharPointer path);
        }

        // 059 typedef GFileInfo* (*g_file_query_info_func)(GFile *file,
        // 060     const char *attributes, GFileQueryInfoFlags flags,
        // 061     GCancellable *cancellable, GError **error);
        interface g_file_query_info_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            GFileInfo invoke(GFile file,
                            CCharPointer attributes,
                            int flags,
                            GCancellable cancellable,
                            GErrorPointer error);
        }

        // 062 typedef char* (*g_file_info_get_content_type_func)(GFileInfo *info);
        interface g_file_info_get_content_type_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            CCharPointer invoke(GFileInfo info);
        }

        // 064 static g_type_init_func g_type_init;
        static g_type_init_func g_type_init;

        // 065 static g_object_unref_func g_object_unref;
        static g_object_unref_func g_object_unref;

        // 066 static g_file_new_for_path_func g_file_new_for_path;
        static g_file_new_for_path_func g_file_new_for_path;

        // 067 static g_file_query_info_func g_file_query_info;
        static g_file_query_info_func g_file_query_info;

        // 068 static g_file_info_get_content_type_func g_file_info_get_content_type;
        static g_file_info_get_content_type_func g_file_info_get_content_type;

        // 070
        // 071 /* Definitions for GNOME VFS */
        // 072

        // 073 typedef int gboolean;
        // 074
        // 075 typedef gboolean (*gnome_vfs_init_function)(void);
        interface gnome_vfs_init_function extends CFunctionPointer {
            @InvokeCFunctionPointer
            int invoke();
        }

        // 076 typedef const char* (*gnome_vfs_mime_type_from_name_function)
        // 077     (const char* filename);
        interface gnome_vfs_mime_type_from_name_function extends CFunctionPointer {
            @InvokeCFunctionPointer
            CCharPointer invoke(CCharPointer filename);
        }

        // 079 static gnome_vfs_init_function gnome_vfs_init;
        static gnome_vfs_init_function gnome_vfs_init;

        // 080 static gnome_vfs_mime_type_from_name_function gnome_vfs_mime_type_from_name;
        static gnome_vfs_mime_type_from_name_function gnome_vfs_mime_type_from_name;

        /* } Allow names with underscores: Checkstyle: resume. */
        /* } Do not format quoted code: @formatter:on */
    }

    /*
     * Translated from:
     * jdk/src/solaris/native/sun/nio/fs/MagicFileTypeDetector.c?v=Java_1.8.0_40_b10
     */
    @Platforms({Platform.LINUX.class})
    @TargetClass(className = "sun.nio.fs.MagicFileTypeDetector", onlyWith = JDK8OrEarlier.class)
    static final class Target_sun_nio_fs_MagicFileTypeDetector {
        /* { Do not format quoted code: @formatter:off */

        // 051 JNIEXPORT jboolean JNICALL
        // 052 Java_sun_nio_fs_MagicFileTypeDetector_initialize0
        // 053     (JNIEnv* env, jclass this)
        // 054 {
        @Substitute
        static boolean initialize0() {
            final Log trace = Log.noopLog()
                            .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_MagicFileTypeDetector.initialize0:")
                            .newline();
            // 055     magic_handle = dlopen("libmagic.so", RTLD_LAZY);
            WordPointer magicHandle = WordFactory.nullPointer();
            try (CCharPointerHolder libmagicsoHolder = CTypeConversion.toCString("libmagic.so")) {
                magicHandle = (WordPointer) Dlfcn.dlopen(libmagicsoHolder.get(), Dlfcn.RTLD_LAZY());
                // 056     if (magic_handle == NULL) {
                if (magicHandle.isNull()) {
                    // 057         magic_handle = dlopen("libmagic.so.1", RTLD_LAZY);
                    try (CCharPointerHolder libmagicso1Holder = CTypeConversion.toCString("libmagic.so.1")) {
                        magicHandle = (WordPointer) Dlfcn.dlopen(libmagicso1Holder.get(), Dlfcn.RTLD_LAZY());
                        // 058         if (magic_handle == NULL) {
                        if (magicHandle.isNull()) {
                            // 059             return JNI_FALSE;
                            trace.string("  libmagic.so not found: returns false]").newline();
                            return false;
                        }
                    }
                }
            }
            // 062
            // 063     magic_open = (magic_open_func)dlsym(magic_handle, "magic_open");
            try (CCharPointerHolder magicopenHolder = CTypeConversion.toCString("magic_open")) {
                Util_sun_nio_fs_MagicFileTypeDetector.magic_open = Dlfcn.dlsym(magicHandle, magicopenHolder.get());
            }
            // 064
            // 065     magic_load = (magic_load_func)dlsym(magic_handle, "magic_load");
            try (CCharPointerHolder magicloadHolder = CTypeConversion.toCString("magic_load")) {
                Util_sun_nio_fs_MagicFileTypeDetector.magic_load = Dlfcn.dlsym(magicHandle, magicloadHolder.get());
            }
            // 066
            // 067     magic_file = (magic_file_func)dlsym(magic_handle, "magic_file");
            try (CCharPointerHolder magicfileHolder = CTypeConversion.toCString("magic_file")) {
                Util_sun_nio_fs_MagicFileTypeDetector.magic_file = Dlfcn.dlsym(magicHandle, magicfileHolder.get());
            }
            // 068
            // 069     magic_close = (magic_close_func)dlsym(magic_handle, "magic_close");
            try (CCharPointerHolder magiccloseHolder = CTypeConversion.toCString("magic_close")) {
                Util_sun_nio_fs_MagicFileTypeDetector.magic_close = Dlfcn.dlsym(magicHandle, magiccloseHolder.get());
            }
            // 070
            // 071     if (magic_open == NULL ||
            // 072         magic_load == NULL ||
            // 073         magic_file == NULL ||
            // 074         magic_close == NULL)
            // 075     {
            if (Util_sun_nio_fs_MagicFileTypeDetector.magic_open.isNull() ||
                            Util_sun_nio_fs_MagicFileTypeDetector.magic_load.isNull() ||
                            Util_sun_nio_fs_MagicFileTypeDetector.magic_file.isNull() ||
                            Util_sun_nio_fs_MagicFileTypeDetector.magic_close.isNull()) {
                // 076         dlclose(magic_handle);
                Dlfcn.dlclose(magicHandle);
                // 077         return JNI_FALSE;
                trace.string("  symbols not available: returns false]").newline();
                return false;
            }
            // 079
            // 080     return JNI_TRUE;
            trace.string("  returns true]").newline();
            return true;
        }

        // 083 JNIEXPORT jbyteArray JNICALL
        // 084 Java_sun_nio_fs_MagicFileTypeDetector_probe0
        // 085     (JNIEnv* env, jclass this, jlong pathAddress)
        // 086 {
        @Substitute
        static byte[] probe0(long pathAddress) {
            final Log trace = Log.noopLog()
                            .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_MagicFileTypeDetector.probe0:")
                            .newline();
            // 087     char* path = (char*)jlong_to_ptr(pathAddress);
            CCharPointer path = WordFactory.pointer(pathAddress);
            trace.string("  path: ").string(path).newline();
            // 088     magic_t* cookie;
            Util_sun_nio_fs_MagicFileTypeDetector.magic_t cookie = WordFactory.nullPointer();
            // 089     jbyteArray result = NULL;
            byte[] result = null;
            // 090
            // 091     cookie = (*magic_open)(MAGIC_MIME_TYPE);
            cookie = Util_sun_nio_fs_MagicFileTypeDetector.magic_open.invoke(Util_sun_nio_fs_MagicFileTypeDetector.MAGIC_MIME_TYPE);
            // 092
            // 093     if (cookie != NULL) {
            if (cookie.isNonNull()) {
                // 094         if ((*magic_load)(cookie, NULL) != -1) {
                if (Util_sun_nio_fs_MagicFileTypeDetector.magic_load.invoke(cookie, WordFactory.nullPointer()) != -1) {
                    // 095             const char* type = (*magic_file)(cookie, path);
                    CCharPointer type = Util_sun_nio_fs_MagicFileTypeDetector.magic_file.invoke(cookie, path);
                    // 096             if (type != NULL) {
                    if (type.isNonNull()) {
                        // 097                 jsize len = strlen(type);
                        int len = (int) LibC.strlen(type).rawValue();
                        // 098                 result = (*env)->NewByteArray(env, len);
                        result = new byte[len];
                        // 099                 if (result != NULL) {
                        /* `new` never returns `null`. */
                        // 100                     (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)type);
                        VmPrimsJNI.SetByteArrayRegion(result, 0, len, type);
                    }
                }
                // 104         (*magic_close)(cookie);
                Util_sun_nio_fs_MagicFileTypeDetector.magic_close.invoke(cookie);
            }
            // 106
            // 107     return result;
            trace.string("  returns  result: ").object(result).string("]").newline();
            return result;
        }

        /* } Do not format quoted code: @formatter:on */

        /** libmagicAvailable is replaced by an injected field and a get access method. */
        // true if libmagic is available and successfully loaded
        @Alias @InjectAccessors(LibmagicAvailableInjector.class) //
        /* private final */ boolean libmagicAvailable;

        /**
         * The injected field replacing libmagicAvailable. This field is null if it is unset,
         * otherwise it is a Boolean.
         */
        @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
        Boolean injectedLibmagicAvailable;

        /** Injected access method for the libmagicAvailable field. */
        static final class LibmagicAvailableInjector {

            /** The access method for libmagicAvailable. */
            static boolean getLibmagicAvailable(Target_sun_nio_fs_MagicFileTypeDetector that) {
                final Log trace = Log.noopLog()
                                .string("[PosixJavaNIOSubstitutions.Target_sun_nio_fs_MagicFileTypeDetector.LibmagicAvailableInjector.getLibmagicAvailable:")
                                .newline();
                if (that.injectedLibmagicAvailable == null) {
                    trace.string(" injectedLibmagicAvailable: null").newline();
                    if (initialize0()) {
                        that.injectedLibmagicAvailable = Boolean.TRUE;
                    } else {
                        that.injectedLibmagicAvailable = Boolean.FALSE;
                    }
                }
                final boolean result = that.injectedLibmagicAvailable.booleanValue();
                trace.string(" returns: ").bool(result).string("]").newline();
                return result;
            }
        }
    }

    @Platforms({Platform.LINUX.class})
    static class Util_sun_nio_fs_MagicFileTypeDetector {

        /* { Do not format quoted code: @formatter:off */
        /* { Allow names with underscores: Checkstyle: stop. */

        // 034 #define MAGIC_MIME_TYPE 0x000010 /* Return the MIME type */
        static final int MAGIC_MIME_TYPE = 0x000010;

        // 036 typedef struct magic_set magic_t;
        interface magic_t extends PointerBase {}

        // 038 typedef magic_t* (*magic_open_func)(int flags);
        interface magic_open_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            magic_t invoke(int flags);
        }

        // 039 typedef int (*magic_load_func)(magic_t* cookie, const char* filename);
        interface magic_load_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            int invoke(magic_t cookie, CCharPointer filename);
        }

        // 040 typedef const char* (*magic_file_func)(magic_t* cookie, const char* filename);
        interface magic_file_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            CCharPointer invoke(magic_t cookie, CCharPointer filename);
        }

        // 041 typedef void (*magic_close_func)(magic_t* cookie);
        interface magic_close_func extends CFunctionPointer {
            @InvokeCFunctionPointer
            void invoke(magic_t cookie);
        }

        // 044 static magic_open_func magic_open;
        static magic_open_func magic_open;

        // 045 static magic_load_func magic_load;
        static magic_load_func magic_load;

        // 046 static magic_file_func magic_file;
        static magic_file_func magic_file;

        // 047 static magic_close_func magic_close;
        static magic_close_func magic_close;

        /* } Allow names with underscores: Checkstyle: resume. */
        /* } Do not format quoted code: @formatter:on */
    }
}
