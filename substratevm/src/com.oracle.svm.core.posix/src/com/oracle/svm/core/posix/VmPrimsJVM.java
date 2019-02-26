/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;

import com.oracle.svm.core.posix.headers.Socket;

/** Native methods (and macros) from src/share/vm/prims/jvm.cpp translated to Java. */
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
public final class VmPrimsJVM {
    /* { Do not re-wrap commented-out code.  @formatter:off */
    /* { Allow names with underscores: @Checkstyle: stop */

    /* Private constructor: No instances. */
    private VmPrimsJVM() {
    }

    // 3719 JVM_LEAF(jint, JVM_Socket(jint domain, jint type, jint protocol))
    public static int JVM_Socket(int domain, int type, int protocol) {
        // 3720   JVMWrapper("JVM_Socket");
        // 3721   return os::socket(domain, type, protocol);
        return Socket.socket(domain, type, protocol);
        // 3722 JVM_END
    }

    // 3767 JVM_LEAF(jint, JVM_Connect(jint fd, struct sockaddr *him, jint len))
    static int JVM_Connect(int fd, Socket.sockaddr him, int len) {
        // 3768   JVMWrapper2("JVM_Connect (0x%x)", fd);
        // 3769   //%note jvm_r6
        // 3770   return os::connect(fd, him, (socklen_t)len);
        return Socket.connect(fd, him, len);
        // 3771 JVM_END
    }

    // 3732 JVM_LEAF(jint, JVM_SocketShutdown(jint fd, jint howto))
    static int JVM_SocketShutdown(int fd, int howto) {
        // 3733   JVMWrapper2("JVM_SocketShutdown (0x%x)", fd);
        // 3734   //%note jvm_r6
        // 3735   return os::socket_shutdown(fd, howto);
        return Socket.shutdown(fd, howto);
        // 3736 JVM_END
    }

    // 3781 JVM_LEAF(jint, JVM_Accept(jint fd, struct sockaddr *him, jint *len))
    static int JVM_Accept(int fd, Socket.sockaddr him, CIntPointer len_Pointer) {
        // 3782   JVMWrapper2("JVM_Accept (0x%x)", fd);
        // 3783   //%note jvm_r6
        // 3784   socklen_t socklen = (socklen_t)(*len);
        CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
        socklen_Pointer.write(len_Pointer.read());
        // 3785   jint result = os::accept(fd, him, &socklen);
        int result = Target_os.accept(fd, him, socklen_Pointer);
        // 3786   *len = (jint)socklen;
        len_Pointer.write(socklen_Pointer.read());
        // 3787   return result;
        return result;
        // 3788 JVM_END
    }

    // 3825 JVM_LEAF(jint, JVM_GetSockOpt(jint fd, int level, int optname, char *optval, int *optlen))
    static int JVM_GetSockOpt(int fd, int level, int optname, CCharPointer optval, CIntPointer optlen) {
        // 3826   JVMWrapper2("JVM_GetSockOpt (0x%x)", fd);
        // 3827   //%note jvm_r6
        /* typedef u_int socklen_t; */
        // 3828   socklen_t socklen = (socklen_t)(*optlen);
        CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
        socklen_Pointer.write(optlen.read());
         // 3829   jint result = os::get_sock_opt(fd, level, optname, optval, &socklen);
        int result = Socket.getsockopt(fd, level, optname, optval, optlen);
        // 3830   *optlen = (int)socklen;
        optlen.write(socklen_Pointer.read());
        // 3831   return result;
        return result;
        // 3832 JVM_END    /* @formatter:on */
    }

    /* Do not re-format commented out code: @formatter:off */
    // 3794 JVM_LEAF(jint, JVM_SetSockOpt(jint fd, int level, int optname, const char *optval, int optlen))
    // 3794 JVMWrapper2("JVM_GetSockOpt (0x%x)", fd);
    static int JVM_SetSockOpt(int fd, int level, int optname, CCharPointer optval, int optlen) {
        // 3794 //%note jvm_r6
        // 3794 return os::set_sock_opt(fd, level, optname, optval, (socklen_t)optlen);
        // 3794 JVM_END
        return Socket.setsockopt(fd, level, optname, optval, optlen);
    }
    /* Do not re-format commented out code: @formatter:on */

    // 3801 JVM_LEAF(jint, JVM_GetSockName(jint fd, struct sockaddr *him, int *len))
    static int JVM_GetSockName(int fd, Socket.sockaddr him, CIntPointer len_Pointer) {
        // 3802 JVMWrapper2("JVM_GetSockName (0x%x)", fd);
        // 3803 //%note jvm_r6
        // 3804 socklen_t socklen = (socklen_t)(*len);
        CIntPointer socklen_Pointer = StackValue.get(CIntPointer.class);
        socklen_Pointer.write(len_Pointer.read());
        // 3805 jint result = os::get_sock_name(fd, him, &socklen);
        int result = Target_os.get_sock_name(fd, him, socklen_Pointer);
        // 3806 *len = (int)socklen;
        len_Pointer.write(socklen_Pointer.read());
        // 3807 return result;
        return result;
        // 3808 JVM_END
    }

    // 2725 JVM_LEAF(jint, JVM_Read(jint fd, char *buf, jint nbytes))
    static int JVM_Read(int fd, CCharPointer buf, int nbytes) {
        // 2726 JVMWrapper2("JVM_Read (0x%x)", fd);
        // 2727
        // 2728 //%note jvm_r6
        // 2729 return (jint)os::restartable_read(fd, buf, nbytes);
        return (int) Target_os.restartable_read(fd, buf, nbytes);
        // 2730 JVM_END
    }

    // 3818 JVM_LEAF(jint, JVM_SocketAvailable(jint fd, jint *pbytes))
    static int JVM_SocketAvailable(int fd, CIntPointer pbytes) {
        // 3819 JVMWrapper2("JVM_SocketAvailable (0x%x)", fd);
        // 3820 //%note jvm_r6
        // 3821 return os::socket_available(fd, pbytes);
        return Target_os.socket_available(fd, pbytes);
        // 3822 JVM_END
    }

    // 3746 JVM_LEAF(jint, JVM_Send(jint fd, char *buf, jint nBytes, jint flags))
    static int JVM_Send(int fd, CCharPointer buf, int nBytes, int flags) {
        // 3747 JVMWrapper2("JVM_Send (0x%x)", fd);
        // 3748 //%note jvm_r6
        // 3749 return os::send(fd, buf, (size_t)nBytes, (uint)flags);
        return Target_os.send(fd, buf, nBytes, flags);
        // 3750 JVM_END
    }

    static int JVM_SendTo(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, int addr_len) {
        return Target_os.sendto(fd, buf, n, flags, addr, addr_len);
    }

    static int JVM_RecvFrom(int fd, CCharPointer buf, int n, int flags, Socket.sockaddr addr, CIntPointer addr_len) {
        return Target_os.recvfrom(fd, buf, n, flags, addr, addr_len);
    }

    // 3725 JVM_LEAF(jint, JVM_SocketClose(jint fd))
    static int JVM_SocketClose(int fd) {
        // 3726 JVMWrapper2("JVM_SocketClose (0x%x)", fd);
        // 3727 //%note jvm_r6
        // 3728 return os::socket_close(fd);
        return Target_os.socket_close(fd);
        // 3729 JVM_END
    }

    // 3842 JVM_LEAF(int, JVM_GetHostName(char* name, int namelen))
    static int JVM_GetHostName(CCharPointer name, int namelen) {
        // 3843 JVMWrapper("JVM_GetHostName");
        // 3844 return os::get_host_name(name, namelen);
        return Target_os.get_host_name(name, namelen);
        // 3845 JVM_END
    }

    // 3760 JVM_LEAF(jint, JVM_Listen(jint fd, jint count))
    static int JVM_Listen(int fd, int count) {
        // 3761 JVMWrapper2("JVM_Listen (0x%x)", fd);
        // 3762 //%note jvm_r6
        // 3763 return os::listen(fd, count);
        return Target_os.listen(fd, count);
        // 3764 JVM_END
    }

    /* } Allow names with underscores: @Checkstyle: resume */
    /* } Do not re-wrap commented-out code.  @formatter:@formatter:on */
}
