/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.posix.headers.Unistd._SC_GETPW_R_SIZE_MAX;

import java.io.FileDescriptor;
import java.io.IOException;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.libc.GLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Locale;
import com.oracle.svm.core.posix.headers.Pwd;
import com.oracle.svm.core.posix.headers.Signal;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.Wait;
import com.oracle.svm.core.posix.headers.Pwd.passwd;
import com.oracle.svm.core.posix.headers.Pwd.passwdPointer;
import com.oracle.svm.core.posix.headers.darwin.DarwinTime;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

public class PosixUtils {

    static String setLocale(String category, String locale) {
        int intCategory = getCategory(category);

        return setLocale(intCategory, locale);
    }

    private static String setLocale(int category, String locale) {
        if (locale == null) {
            CCharPointer cstrResult = Locale.setlocale(category, WordFactory.nullPointer());
            return CTypeConversion.toJavaString(cstrResult);
        }
        try (CCharPointerHolder localePin = CTypeConversion.toCString(locale)) {
            CCharPointer cstrLocale = localePin.get();
            CCharPointer cstrResult = Locale.setlocale(category, cstrLocale);
            return CTypeConversion.toJavaString(cstrResult);
        }
    }

    private static int getCategory(String category) {
        switch (category) {
            case "LC_ALL":
                return Locale.LC_ALL();
            case "LC_COLLATE":
                return Locale.LC_COLLATE();
            case "LC_CTYPE":
                return Locale.LC_CTYPE();
            case "LC_MONETARY":
                return Locale.LC_MONETARY();
            case "LC_NUMERIC":
                return Locale.LC_NUMERIC();
            case "LC_TIME":
                return Locale.LC_TIME();
            case "LC_MESSAGES":
                return Locale.LC_MESSAGES();
        }

        if (Platform.includedIn(Platform.LINUX.class) && LibCBase.targetLibCIs(GLibC.class)) {
            switch (category) {
                case "LC_PAPER":
                    return Locale.LC_PAPER();
                case "LC_NAME":
                    return Locale.LC_NAME();
                case "LC_ADDRESS":
                    return Locale.LC_ADDRESS();
                case "LC_TELEPHONE":
                    return Locale.LC_TELEPHONE();
                case "LC_MEASUREMENT":
                    return Locale.LC_MEASUREMENT();
                case "LC_IDENTIFICATION":
                    return Locale.LC_IDENTIFICATION();
            }
        }
        throw new IllegalArgumentException("Unknown locale category: " + category);
    }

    @TargetClass(java.io.FileDescriptor.class)
    private static final class Target_java_io_FileDescriptor {

        @Alias int fd;
    }

    public static int getFD(FileDescriptor descriptor) {
        return SubstrateUtil.cast(descriptor, Target_java_io_FileDescriptor.class).fd;
    }

    public static void setFD(FileDescriptor descriptor, int fd) {
        SubstrateUtil.cast(descriptor, Target_java_io_FileDescriptor.class).fd = fd;
    }

    /** Return the error string for the last error, or a default message. */
    public static String lastErrorString(String defaultMsg) {
        int errno = LibC.errno();
        return errorString(errno, defaultMsg);
    }

    public static IOException newIOExceptionWithLastError(String defaultMsg) {
        return new IOException(lastErrorString(defaultMsg));
    }

    /** Return the error string for the given error number, or a default message. */
    public static String errorString(int errno, String defaultMsg) {
        String result = "";
        if (errno != 0) {
            result = CTypeConversion.toJavaString(Errno.strerror(errno));
        }
        return result.length() != 0 ? result : defaultMsg;
    }

    public static int getpid() {
        return Unistd.getpid();
    }

    @TargetClass(className = "java.lang.ProcessImpl")
    private static final class Target_java_lang_ProcessImpl {
        @Alias int pid;
    }

    public static int getpid(Process process) {
        Target_java_lang_ProcessImpl instance = SubstrateUtil.cast(process, Target_java_lang_ProcessImpl.class);
        return instance.pid;
    }

    public static int waitForProcessExit(int ppid) {
        CIntPointer statusptr = UnsafeStackValue.get(CIntPointer.class);
        while (Wait.waitpid(ppid, statusptr, 0) < 0) {
            int errno = LibC.errno();
            if (errno == Errno.ECHILD()) {
                return 0;
            } else if (errno == Errno.EINTR()) {
                break;
            } else {
                return -1;
            }
        }

        int status = statusptr.read();
        if (Wait.WIFEXITED(status)) {
            return Wait.WEXITSTATUS(status);
        } else if (Wait.WIFSIGNALED(status)) {
            // Exited because of signal: return 0x80 + signal number like shells do
            return 0x80 + Wait.WTERMSIG(status);
        }
        return status;
    }

    /**
     * Low-level output of bytes already in native memory. This method is allocation free, so that
     * it can be used, e.g., in low-level logging routines.
     */
    public static boolean writeBytes(FileDescriptor descriptor, CCharPointer bytes, UnsignedWord length) {
        CCharPointer curBuf = bytes;
        UnsignedWord curLen = length;
        while (curLen.notEqual(0)) {
            int fd = getFD(descriptor);
            if (fd == -1) {
                return false;
            }

            SignedWord n = Unistd.write(fd, curBuf, curLen);
            if (n.equal(-1)) {
                if (LibC.errno() == Errno.EINTR()) {
                    // Retry the write if it was interrupted before any bytes were written.
                    continue;
                }
                return false;
            }
            curBuf = curBuf.addressOf(n);
            curLen = curLen.subtract((UnsignedWord) n);
        }
        return true;
    }

    public static boolean flush(FileDescriptor descriptor) {
        int fd = getFD(descriptor);
        return Unistd.fsync(fd) == 0;
    }

    public static PointerBase dlopen(String file, int mode) {
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(file)) {
            CCharPointer pathPtr = pathPin.get();
            return Dlfcn.dlopen(pathPtr, mode);
        }
    }

    public static <T extends PointerBase> T dlsym(PointerBase handle, String name) {
        try (CCharPointerHolder namePin = CTypeConversion.toCString(name)) {
            CCharPointer namePtr = namePin.get();
            return Dlfcn.dlsym(handle, namePtr);
        }
    }

    public static String dlerror() {
        return CTypeConversion.toJavaString(Dlfcn.dlerror());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void checkStatusIs0(int status, String message) {
        VMError.guarantee(status == 0, message);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int readBytes(int fd, CCharPointer buffer, int bufferLen, int readOffset) {
        int readBytes = -1;
        if (readOffset < bufferLen) {
            do {
                readBytes = (int) Unistd.NoTransitions.read(fd, buffer.addressOf(readOffset), WordFactory.unsigned(bufferLen - readOffset)).rawValue();
            } while (readBytes == -1 && LibC.errno() == Errno.EINTR());
        }
        return readBytes;
    }

    public static Signal.SignalDispatcher installSignalHandler(Signal.SignalEnum signum, Signal.SignalDispatcher handler, int flags) {
        return installSignalHandler(signum.getCValue(), handler, flags);
    }

    /**
     * Emulates the deprecated {@code signal} function via its replacement {@code sigaction},
     * assuming BSD semantics (like glibc does, for example).
     *
     * Use this or {@code sigaction} directly instead of calling {@code signal} or {@code sigset}:
     * they are not portable and when running in HotSpot, signal chaining (libjsig) prints warnings.
     *
     * Note that this method should not be called from an initialization hook:
     * {@code EnableSignalHandling} may not be set correctly at the time initialization hooks run.
     */
    public static Signal.SignalDispatcher installSignalHandler(int signum, Signal.SignalDispatcher handler, int flags) {
        int structSigActionSize = SizeOf.get(Signal.sigaction.class);
        Signal.sigaction act = UnsafeStackValue.get(structSigActionSize);
        LibC.memset(act, WordFactory.signed(0), WordFactory.unsigned(structSigActionSize));
        act.sa_flags(flags);
        act.sa_handler(handler);

        Signal.sigaction old = UnsafeStackValue.get(Signal.sigaction.class);

        int result = sigaction(signum, act, old);
        if (result != 0) {
            return Signal.SIG_ERR();
        }
        return old.sa_handler();
    }

    public static void installSignalHandler(Signal.SignalEnum signum, Signal.AdvancedSignalDispatcher handler, int flags) {
        installSignalHandler(signum.getCValue(), handler, flags);
    }

    public static void installSignalHandler(int signum, Signal.AdvancedSignalDispatcher handler, int flags) {
        int structSigActionSize = SizeOf.get(Signal.sigaction.class);
        Signal.sigaction act = UnsafeStackValue.get(structSigActionSize);
        LibC.memset(act, WordFactory.signed(0), WordFactory.unsigned(structSigActionSize));
        act.sa_flags(Signal.SA_SIGINFO() | flags);
        act.sa_sigaction(handler);

        int result = sigaction(signum, act, WordFactory.nullPointer());
        PosixUtils.checkStatusIs0(result, "sigaction failed in installSignalHandler().");
    }

    /*
     * Avoid races with logic within Util_jdk_internal_misc_Signal#handle0 which reads these
     * signals.
     */
    private static int sigaction(int signum, Signal.sigaction structSigAction, Signal.sigaction old) {
        VMError.guarantee(SubstrateOptions.EnableSignalHandling.getValue(), "Trying to install a signal handler while signal handling is disabled.");

        if (VMOperation.isInProgress()) {
            /*
             * Note this can race with other signals being installed. However, using Java
             * synchronization is disallowed within a VMOperation. If race-free execution becomes
             * necessary, then a VMMutex will be needed and additional code will need to be
             * made @Uninterruptible so that a thread owning the VMMutex cannot block at a
             * safepoint.
             */
            return Signal.sigaction(signum, structSigAction, old);
        } else {
            synchronized (Target_jdk_internal_misc_Signal.class) {
                return Signal.sigaction(signum, structSigAction, old);
            }
        }
    }

    // Checkstyle: stop
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int clock_gettime(int clock_id, Time.timespec ts) {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            return DarwinTime.NoTransitions.clock_gettime(clock_id, ts);
        } else {
            assert Platform.includedIn(Platform.LINUX.class);
            return LinuxTime.NoTransitions.clock_gettime(clock_id, ts);
        }
    }
    // Checkstyle: resume

    public static String getUserName(int uid) {
        return getUserNameOrDir(uid, true);
    }

    public static String getUserDir(int uid) {
        return getUserNameOrDir(uid, false);
    }

    private static String getUserNameOrDir(int uid, boolean name) {
        /* Determine max. pwBuf size. */
        long bufSize = Unistd.sysconf(_SC_GETPW_R_SIZE_MAX());
        if (bufSize == -1) {
            bufSize = 1024;
        }

        /* Retrieve the username and copy it to a String object. */
        CCharPointer pwBuf = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(bufSize));
        if (pwBuf.isNull()) {
            return null;
        }

        try {
            passwd pwent = StackValue.get(passwd.class);
            passwdPointer p = StackValue.get(passwdPointer.class);
            int code = Pwd.getpwuid_r(uid, pwent, pwBuf, WordFactory.unsigned(bufSize), p);
            if (code != 0) {
                return null;
            }

            passwd result = p.read();
            if (result.isNull()) {
                return null;
            }

            CCharPointer pwName = name ? result.pw_name() : result.pw_dir();
            if (pwName.isNull() || pwName.read() == '\0') {
                return null;
            }

            return CTypeConversion.toJavaString(pwName);
        } finally {
            UnmanagedMemory.free(pwBuf);
        }
    }
}
