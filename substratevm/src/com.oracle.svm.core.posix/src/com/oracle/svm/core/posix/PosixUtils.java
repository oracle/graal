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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.posix.headers.Unistd._SC_GETPW_R_SIZE_MAX;

import java.io.FileDescriptor;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.libc.GLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.locale.LocaleSupport;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.posix.headers.Dlfcn;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Locale;
import com.oracle.svm.core.posix.headers.Pwd;
import com.oracle.svm.core.posix.headers.Pwd.passwd;
import com.oracle.svm.core.posix.headers.Pwd.passwdPointer;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.posix.headers.Wait;
import com.oracle.svm.core.posix.headers.darwin.DarwinTime;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

public class PosixUtils {
    /** This method is unsafe and should not be used, see {@link LocaleSupport}. */
    static String setLocale(String category, String locale) {
        int intCategory = getCategory(category);
        return setLocale(intCategory, locale);
    }

    /** This method is unsafe and should not be used, see {@link LocaleSupport}. */
    private static String setLocale(int category, String locale) {
        if (locale == null) {
            CCharPointer cstrResult = Locale.setlocale(category, Word.nullPointer());
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
    public static boolean write(FileDescriptor descriptor, CCharPointer data, UnsignedWord size) {
        CCharPointer position = data;
        UnsignedWord remaining = size;
        while (remaining.notEqual(0)) {
            int fd = getFD(descriptor);
            if (fd == -1) {
                return false;
            }

            SignedWord writtenBytes = Unistd.write(fd, position, remaining);
            if (writtenBytes.equal(-1)) {
                if (LibC.errno() == Errno.EINTR()) {
                    // Retry the write if it was interrupted before any bytes were written.
                    continue;
                }
                return false;
            }
            position = position.addressOf(writtenBytes);
            remaining = remaining.subtract((UnsignedWord) writtenBytes);
        }
        return true;
    }

    @Uninterruptible(reason = "Array must not move.")
    public static boolean writeUninterruptibly(int fd, byte[] data) {
        DynamicHub hub = KnownIntrinsics.readHub(data);
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
        Pointer dataPtr = Word.objectToUntrackedPointer(data).add(baseOffset);
        return writeUninterruptibly(fd, dataPtr, Word.unsigned(data.length));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean writeUninterruptibly(int fd, Pointer data, UnsignedWord size) {
        Pointer position = data;
        UnsignedWord remaining = size;
        while (remaining.notEqual(0)) {
            SignedWord writtenBytes = Unistd.NoTransitions.write(fd, position, remaining);
            if (writtenBytes.equal(-1)) {
                if (LibC.errno() == Errno.EINTR()) {
                    // Retry the write if it was interrupted before any bytes were written.
                    continue;
                }
                return false;
            }
            position = position.add((UnsignedWord) writtenBytes);
            remaining = remaining.subtract((UnsignedWord) writtenBytes);
        }
        return true;
    }

    public static boolean flush(FileDescriptor descriptor) {
        int fd = getFD(descriptor);
        return Unistd.fsync(fd) == 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean flushUninterruptibly(int fd) {
        return Unistd.NoTransitions.fsync(fd) == 0;
    }

    public static PointerBase dlopen(String file, int mode) {
        try (CCharPointerHolder pathPin = CTypeConversion.toCString(file)) {
            CCharPointer pathPtr = pathPin.get();
            return Dlfcn.dlopen(pathPtr, mode);
        }
    }

    public static boolean dlclose(PointerBase handle) {
        return Dlfcn.dlclose(handle) == 0;
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

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int readUninterruptibly(int fd, Pointer buffer, int bufferLen, int bufferOffset) {
        int readBytes = -1;
        if (bufferOffset < bufferLen) {
            do {
                readBytes = (int) Unistd.NoTransitions.read(fd, buffer.add(bufferOffset), Word.unsigned(bufferLen - bufferOffset)).rawValue();
            } while (readBytes == -1 && LibC.errno() == Errno.EINTR());
        }
        return readBytes;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int readUninterruptibly(int fd, Pointer buffer, int bufferSize) {
        VMError.guarantee(bufferSize >= 0);
        long readBytes = readUninterruptibly(fd, buffer, Word.unsigned(bufferSize));
        assert (int) readBytes == readBytes;
        return (int) readBytes;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long readUninterruptibly(int fd, Pointer buffer, UnsignedWord bufferSize) {
        SignedWord readBytes;
        do {
            readBytes = Unistd.NoTransitions.read(fd, buffer, bufferSize);
        } while (readBytes.equal(-1) && LibC.errno() == Errno.EINTR());

        return readBytes.rawValue();
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

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+13/src/hotspot/os/posix/perfMemory_posix.cpp#L436-L486")
    private static String getUserNameOrDir(int uid, boolean name) {
        /* Determine max. pwBuf size. */
        long bufSize = Unistd.sysconf(_SC_GETPW_R_SIZE_MAX());
        if (bufSize == -1) {
            bufSize = 1024;
        }

        /* Does not use StackValue because it is not safe to use in virtual threads. */
        UnsignedWord allocSize = Word.unsigned(SizeOf.get(passwdPointer.class) + SizeOf.get(passwd.class) + bufSize);
        Pointer alloc = NullableNativeMemory.malloc(allocSize, NmtCategory.Internal);
        if (alloc.isNull()) {
            return null;
        }

        try {
            passwdPointer p = (passwdPointer) alloc;
            passwd pwent = (passwd) ((Pointer) p).add(SizeOf.get(passwdPointer.class));
            CCharPointer pwBuf = (CCharPointer) ((Pointer) pwent).add(SizeOf.get(passwd.class));
            int code = Pwd.getpwuid_r(uid, pwent, pwBuf, Word.unsigned(bufSize), p);
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
            NullableNativeMemory.free(alloc);
        }
    }
}
