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
package com.oracle.svm.core.posixsubst.headers;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.posixsubst.headers.Uio.iovec;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/socket.h.
 */
@CContext(PosixSubstDirectives.class)
public class Socket {

    @CConstant
    public static native int SOCK_STREAM();

    @CConstant
    public static native int SOCK_DGRAM();

    @CConstant
    public static native int PF_UNIX();

    @CConstant
    public static native int PF_INET();

    @CConstant
    public static native int AF_UNSPEC();

    @CConstant
    @Platforms(DeprecatedPlatform.DARWIN_SUBSTITUTION.class)
    public static native int AF_LINK();

    @CConstant
    public static native int AF_UNIX();

    @CConstant
    public static native int AF_INET();

    @CConstant
    public static native int AF_INET6();

    @CStruct(addStructKeyword = true)
    public interface sockaddr extends PointerBase {

        @Platforms(DeprecatedPlatform.DARWIN_SUBSTITUTION.class)
        @CField
        @AllowWideningCast
        int sa_len();

        @CField
        @AllowWideningCast
        int sa_family();

        @CFieldAddress
        CCharPointer sa_data();
    }

    @CPointerTo(sockaddr.class)
    public interface sockaddrPointer extends PointerBase {

        sockaddr read();

        void write(sockaddr value);
    }

    @CConstant
    public static native int MSG_DONTWAIT();

    /*
     * Note: socklen_t is "u_int32" on Linux and "int32" on Darwin, so I am using "long" to cover
     * the values, but allowing widening on reads and narrowing on writes.
     */

    @CStruct(addStructKeyword = true)
    public interface msghdr extends PointerBase {
        @CField
        PointerBase msg_name();

        @CField
        void msg_name(PointerBase value);

        @CField
        @AllowWideningCast
        long /* socklen_t */ msg_namelen();

        @CField
        @AllowNarrowingCast
        void msg_namelen(long /* socklen_t */ value);

        @CField
        iovec msg_iov();

        @CField
        void msg_iov(iovec value);

        @CField
        @AllowWideningCast
        long /* socklen_t */ msg_iovlen();

        @CField
        @AllowNarrowingCast
        void msg_iovlen(long /* socklen_t */ value);

        @CField
        PointerBase msg_control();

        @CField
        void msg_control(PointerBase value);

        @CField
        @AllowWideningCast
        long /* socklen_t */ msg_controllen();

        @CField
        @AllowNarrowingCast
        void msg_controllen(long /* socklen_t */ value);

        @CField
        int msg_flags();

        @CField
        void msg_flags(int value);
    }

    @CStruct(addStructKeyword = true)
    public interface cmsghdr extends PointerBase {
        @CField
        @AllowWideningCast
        long /* socklen_t */ cmsg_len();

        @CField
        @AllowNarrowingCast
        void cmsg_len(long value);

        @CField
        int cmsg_level();

        @CField
        void cmsg_level(int value);

        @CField
        int cmsg_type();

        @CField
        void cmsg_type(int value);

        /* followed by unsigned char cmsg_data[]; */
    }

    @CConstant
    public static native int SCM_RIGHTS();

    @CConstant
    public static native long SIOCGIFCONF();

    @CConstant
    public static native long SIOCGIFFLAGS();

    @CConstant
    public static native long SIOCGIFBRDADDR();

    @CConstant
    @Platforms(DeprecatedPlatform.LINUX_SUBSTITUTION.class)
    public static native int SIOCGIFHWADDR();

    @CConstant
    public static native long SIOCGIFNETMASK();

    @CConstant
    @Platforms(DeprecatedPlatform.LINUX_SUBSTITUTION.class)
    public static native int SIOCGIFINDEX();

    @CConstant
    public static native int SOL_SOCKET();

    @CConstant
    public static native int SO_REUSEADDR();

    @CConstant
    public static native int SO_ERROR();

    @CConstant
    public static native int SO_BROADCAST();

    @CConstant
    public static native int SO_SNDBUF();

    @CConstant
    public static native int SO_RCVBUF();

    @CConstant
    public static native int SO_KEEPALIVE();

    @CConstant
    public static native int SO_OOBINLINE();

    @CConstant
    public static native int SO_LINGER();

    @CConstant
    public static native int SO_REUSEPORT();

    @CStruct(addStructKeyword = true)
    public interface linger extends PointerBase {
        @CField
        int l_onoff();

        @CField
        void set_l_onoff(int value);

        @CField
        int l_linger();

        @CField
        void set_l_linger(int value);
    }

    @CFunction
    public static native int socket(int domain, int type, int protocol);

    @CFunction
    public static native int socketpair(int domain, int type, int protocol, CIntPointer fds);

    @CFunction
    public static native int bind(int fd, sockaddr addr, int len);

    @CFunction
    public static native int getsockname(int fd, sockaddr addr, CIntPointer len);

    @CFunction
    public static native int connect(int fd, sockaddr addr, int len);

    @CFunction
    public static native SignedWord send(int fd, PointerBase buf, UnsignedWord n, int flags);

    @CFunction
    public static native SignedWord recv(int fd, PointerBase buf, UnsignedWord n, int flags);

    @CFunction
    public static native SignedWord sendto(int fd, PointerBase buf, UnsignedWord n, int flags, sockaddr addr, int addr_len);

    @CFunction
    public static native SignedWord recvfrom(int fd, PointerBase buf, UnsignedWord n, int flags, sockaddr addr, CIntPointer addr_len);

    @CFunction
    public static native int getsockopt(int fd, int level, int optname, PointerBase optval, CIntPointer optlen);

    @CFunction
    public static native int setsockopt(int fd, int level, int optname, PointerBase optval, int optlen);

    @CFunction
    public static native int listen(int fd, int n);

    @CFunction
    public static native int accept(int fd, sockaddr addr, CIntPointer addr_len);

    @CFunction
    public static native int shutdown(int fd, int how);
}
