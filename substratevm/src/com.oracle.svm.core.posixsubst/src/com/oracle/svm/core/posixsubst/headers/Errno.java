/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.annotate.Uninterruptible;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/errno.h.
 */
@CContext(PosixSubstDirectives.class)
public class Errno {

    /** Operation not permitted. */
    @CConstant
    public static native int EPERM();

    /** No such file or directory. */
    @CConstant
    public static native int ENOENT();

    /** No such process. */
    @CConstant
    public static native int ESRCH();

    /** Interrupted system call. */
    @CConstant
    public static native int EINTR();

    /** I/O error. */
    @CConstant
    public static native int EIO();

    /** No such device or address. */
    @CConstant
    public static native int ENXIO();

    /** Argument list too long. */
    @CConstant
    public static native int E2BIG();

    /** Exec format error. */
    @CConstant
    public static native int ENOEXEC();

    /** Bad file number. */
    @CConstant
    public static native int EBADF();

    /** No child processes. */
    @CConstant
    public static native int ECHILD();

    /** Try again. */
    @CConstant
    public static native int EAGAIN();

    /** Out of memory. */
    @CConstant
    public static native int ENOMEM();

    /** Permission denied. */
    @CConstant
    public static native int EACCES();

    /** Bad address. */
    @CConstant
    public static native int EFAULT();

    /** Block device required. */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOTBLK();

    /** Device or resource busy. */
    @CConstant
    public static native int EBUSY();

    /** File exists. */
    @CConstant
    public static native int EEXIST();

    /** Cross-device link. */
    @CConstant
    public static native int EXDEV();

    /** No such device. */
    @CConstant
    public static native int ENODEV();

    /** Not a directory. */
    @CConstant
    public static native int ENOTDIR();

    /** Is a directory. */
    @CConstant
    public static native int EISDIR();

    /** Invalid argument. */
    @CConstant
    public static native int EINVAL();

    /** File table overflow. */
    @CConstant
    public static native int ENFILE();

    /** Too many open files. */
    @CConstant
    public static native int EMFILE();

    /** Not a typewriter. */
    @CConstant
    public static native int ENOTTY();

    /** Text file busy. */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ETXTBSY();

    /** File too large. */
    @CConstant
    public static native int EFBIG();

    /** No space left on device. */
    @CConstant
    public static native int ENOSPC();

    /** Illegal seek. */
    @CConstant
    public static native int ESPIPE();

    /** Read-only file system. */
    @CConstant
    public static native int EROFS();

    /** Too many links. */
    @CConstant
    public static native int EMLINK();

    /** Broken pipe. */
    @CConstant
    public static native int EPIPE();

    /** Math argument out of domain of func. */
    @CConstant
    public static native int EDOM();

    /** Math result not representable. */
    @CConstant
    public static native int ERANGE();

    /** Linux has no ENOTSUP error code. */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOTSUP();

    /** Resource deadlock would occur */
    @CConstant
    public static native int EDEADLK();

    /** File name too long */
    @CConstant
    public static native int ENAMETOOLONG();

    /** No record locks available */
    @CConstant
    public static native int ENOLCK();

    /** Function not implemented */
    @CConstant
    public static native int ENOSYS();

    /** Directory not empty */
    @CConstant
    public static native int ENOTEMPTY();

    /** Too many symbolic links encountered */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ELOOP();

    /** Operation would block */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EWOULDBLOCK();

    /** No message of desired type */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOMSG();

    /** Identifier removed */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EIDRM();

    /** Protocol error */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EPROTO();

    /** Multihop attempted */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EMULTIHOP();

    /** Not a data message */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EBADMSG();

    /** Value too large for defined data type */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EOVERFLOW();

    /** Too many users */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EUSERS();

    /** Socket operation on non-socket */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOTSOCK();

    /** Destination address required */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EDESTADDRREQ();

    /** Message too long */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EMSGSIZE();

    /** Protocol wrong type for socket */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EPROTOTYPE();

    /** Protocol not available */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOPROTOOPT();

    /** Protocol not supported */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EPROTONOSUPPORT();

    /** Socket type not supported */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ESOCKTNOSUPPORT();

    /** Operation not supported on transport endpoint */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EOPNOTSUPP();

    /** Protocol family not supported */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EPFNOSUPPORT();

    /** Address family not supported by protocol */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EAFNOSUPPORT();

    /** Address already in use */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EADDRINUSE();

    /** Cannot assign requested address */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EADDRNOTAVAIL();

    /** Network is down */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENETDOWN();

    /** Network is unreachable */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENETUNREACH();

    /** Network dropped connection because of reset */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENETRESET();

    /** Software caused connection abort */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ECONNABORTED();

    /** Connection reset by peer */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ECONNRESET();

    /** No buffer space available */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOBUFS();

    /** Transport endpoint is already connected */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EISCONN();

    /** Transport endpoint is not connected */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOTCONN();

    /** Cannot send after transport endpoint shutdown */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ESHUTDOWN();

    /** Too many references: cannot splice */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ETOOMANYREFS();

    /** Connection timed out */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ETIMEDOUT();

    /** Connection refused */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ECONNREFUSED();

    /** Host is down */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EHOSTDOWN();

    /** No route to host */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EHOSTUNREACH();

    /** Operation already in progress */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EALREADY();

    /** Operation now in progress */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EINPROGRESS();

    /** Stale NFS file handle */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ESTALE();

    /** Quota exceeded */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EDQUOT();

    /** Operation Canceled */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ECANCELED();

    /* for robust mutexes */
    /** Owner died */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int EOWNERDEAD();

    /** State not recoverable */
    @CConstant
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native int ENOTRECOVERABLE();

    /*
     * errno is defined as a macro, with different functions being called in different libc
     * implementations. So there is no C-function errno, and there is no point in annotating this
     * method with @CFunction. Instead, OS-specific code substitutes this method with the
     * appropriate implementation.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static int errno() {
        return CErrorNumber.getCErrorNumber();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void set_errno(int value) {
        CErrorNumber.setCErrorNumber(value);
    }

    /*
     * strerror() and strerror_r() are actually defined in string.h, but are probably always used
     * together with errno() and therefore declared here.
     */

    @CFunction
    public static native CCharPointer strerror(int errnum);

    @CFunction
    @Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class, Platform.LINUX.class, Platform.DARWIN.class})
    public static native CCharPointer strerror_r(int errnum, CCharPointer buf, UnsignedWord buflen);
}
