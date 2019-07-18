/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.jdk11.linux;

import java.net.SocketException;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.posix.PosixJavaNetClose;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.NetinetIn;
import com.oracle.svm.core.posix.headers.NetinetTcp;
import com.oracle.svm.core.posix.headers.Socket;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;

import static com.oracle.svm.core.posix.headers.NetinetTcp.SOL_TCP;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_KEEPIDLE;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_KEEPCNT;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_KEEPINTVL;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_QUICKACK;
import static com.oracle.svm.core.posix.headers.Socket.PF_INET;
import static com.oracle.svm.core.posix.headers.Socket.SOCK_STREAM;
import static com.oracle.svm.core.posix.headers.Socket.SOL_SOCKET;
import static com.oracle.svm.core.posix.headers.Socket.getsockopt;
import static com.oracle.svm.core.posix.headers.Socket.setsockopt;
import static com.oracle.svm.core.posix.headers.Socket.socket;
import static com.oracle.svm.core.posix.jdk11.linux.Util_jdk_net_LinuxSocketOptions.handleError;
import static com.oracle.svm.core.posix.jdk11.linux.Util_jdk_net_LinuxSocketOptions.socketOptionSupported;

@TargetClass(className = "jdk.net.LinuxSocketOptions", onlyWith = JDK11OrLater.class)
@Platforms({Platform.LINUX.class})
public final class Target_jdk_net_LinuxSocketOptions {

    // ported from {jdk11}/src/jdk.net/linux/native/libextnet/LinuxSocketOptions.c

    /* { Do not re-format commented out C code. @formatter:off */
    //   136  JNIEXPORT jboolean JNICALL Java_jdk_net_LinuxSocketOptions_keepAliveOptionsSupported0
    //   137  (JNIEnv *env, jobject unused) {
    @Substitute
    private static boolean keepAliveOptionsSupported0() {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   138      return socketOptionSupported(TCP_KEEPIDLE) && socketOptionSupported(TCP_KEEPCNT)
        //   139              && socketOptionSupported(TCP_KEEPINTVL);
        return socketOptionSupported(TCP_KEEPIDLE()) != 0  && socketOptionSupported(TCP_KEEPCNT()) != 0
                && socketOptionSupported(TCP_KEEPINTVL()) != 0;
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   147  JNIEXPORT void JNICALL Java_jdk_net_LinuxSocketOptions_setTcpkeepAliveProbes0
    //   148  (JNIEnv *env, jobject unused, jint fd, jint optval) {
    @Substitute
    private static void setTcpkeepAliveProbes0(int fd, int optval) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        optval_Pointer.write(optval);
        //   149      jint rv = setsockopt(fd, SOL_TCP, TCP_KEEPCNT, &optval, sizeof (optval));
        int rv = setsockopt(fd, SOL_TCP(), TCP_KEEPCNT(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //   150      handleError(env, rv, "set option TCP_KEEPCNT failed");
        handleError(rv, "set option TCP_KEEPCNT failed");
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   158  JNIEXPORT void JNICALL Java_jdk_net_LinuxSocketOptions_setTcpKeepAliveTime0
    //   159  (JNIEnv *env, jobject unused, jint fd, jint optval) {
    @Substitute
    private static void setTcpKeepAliveTime0(int fd, int optval) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        optval_Pointer.write(optval);
        //   160      jint rv = setsockopt(fd, SOL_TCP, TCP_KEEPIDLE, &optval, sizeof (optval));
        int rv = setsockopt(fd, SOL_TCP(), TCP_KEEPIDLE(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //   161      handleError(env, rv, "set option TCP_KEEPIDLE failed");
        handleError(rv, "set option TCP_KEEPIDLE failed");
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   169  JNIEXPORT void JNICALL Java_jdk_net_LinuxSocketOptions_setTcpKeepAliveIntvl0
    //   170  (JNIEnv *env, jobject unused, jint fd, jint optval) {
    @Substitute
    private static void setTcpKeepAliveIntvl0(int fd, int optval) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        optval_Pointer.write(optval);
        //   171      jint rv = setsockopt(fd, SOL_TCP, TCP_KEEPINTVL, &optval, sizeof (optval));
        int rv = setsockopt(fd, SOL_TCP(), TCP_KEEPINTVL(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //   172      handleError(env, rv, "set option TCP_KEEPINTVL failed");
        handleError(rv, "set option TCP_KEEPINTVL failed");
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   180  JNIEXPORT jint JNICALL Java_jdk_net_LinuxSocketOptions_getTcpkeepAliveProbes0
    //   181  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static int getTcpkeepAliveProbes0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   182      jint optval, rv;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        int rv;
        //   183      socklen_t sz = sizeof (optval);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //   184      rv = getsockopt(fd, SOL_TCP, TCP_KEEPCNT, &optval, &sz);
        rv = getsockopt(fd, SOL_TCP(), TCP_KEEPCNT(), optval_Pointer, sz_Pointer);
        //   185      handleError(env, rv, "get option TCP_KEEPCNT failed");
        handleError(rv, "get option TCP_KEEPCNT failed");
        //   186      return optval;
        return optval_Pointer.read();
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   194  JNIEXPORT jint JNICALL Java_jdk_net_LinuxSocketOptions_getTcpKeepAliveTime0
    //   195  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static int getTcpKeepAliveTime0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   196      jint optval, rv;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        int rv;
        //   197      socklen_t sz = sizeof (optval);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //   198      rv = getsockopt(fd, SOL_TCP, TCP_KEEPIDLE, &optval, &sz);
        rv = getsockopt(fd, SOL_TCP(), TCP_KEEPIDLE(), optval_Pointer, sz_Pointer);
        //   199      handleError(env, rv, "get option TCP_KEEPIDLE failed");
        handleError(rv, "get option TCP_KEEPIDLE failed");
        //   200      return optval;
        return optval_Pointer.read();
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   208  JNIEXPORT jint JNICALL Java_jdk_net_LinuxSocketOptions_getTcpKeepAliveIntvl0
    //   209  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static int getTcpKeepAliveIntvl0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   210      jint optval, rv;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        int rv;
        //   211      socklen_t sz = sizeof (optval);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //   212      rv = getsockopt(fd, SOL_TCP, TCP_KEEPINTVL, &optval, &sz);
        rv = getsockopt(fd, SOL_TCP(), TCP_KEEPINTVL(), optval_Pointer, sz_Pointer);
        //   213      handleError(env, rv, "get option TCP_KEEPINTVL failed");
        handleError(rv, "get option TCP_KEEPINTVL failed");
        //   214      return optval;
        return optval_Pointer.read();
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    41  JNIEXPORT void JNICALL Java_jdk_net_LinuxSocketOptions_setQuickAck0
    //    42  (JNIEnv *env, jobject unused, jint fd, jboolean on) {
    @Substitute
    private static void setQuickAck0(int fd, boolean on) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //    43      int optval;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        //    44      int rv;
        int rv;
        //    45      optval = (on ? 1 : 0);
        optval_Pointer.write(on ? 1 : 0);
        //    46      rv = setsockopt(fd, SOL_SOCKET, TCP_QUICKACK, &optval, sizeof (optval));
        rv = setsockopt(fd, SOL_SOCKET(), TCP_QUICKACK(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //    47      if (rv < 0) {
        if (rv < 0) {
            //    48          if (errno == ENOPROTOOPT) {
            if (Errno.errno() == Errno.ENOPROTOOPT()) {
                //    49              JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                //    50                              "unsupported socket option");
                throw new UnsupportedOperationException("unsupported socket option");
            } else {
                //    52              JNU_ThrowByNameWithLastError(env, "java/net/SocketException",
                //    53                                          "set option TCP_QUICKACK failed");
                throw new SocketException(PosixUtils.lastErrorString("set option TCP_QUICKACK failed"));
            }
        }
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    63  JNIEXPORT jboolean JNICALL Java_jdk_net_LinuxSocketOptions_getQuickAck0
    //    64  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static boolean getQuickAck0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //    65      int on;
        CIntPointer on_Pointer = StackValue.get(CIntPointer.class);
        //    66      socklen_t sz = sizeof (on);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //    67      int rv = getsockopt(fd, SOL_SOCKET, TCP_QUICKACK, &on, &sz);
        int rv = getsockopt(fd, SOL_SOCKET(), TCP_QUICKACK(), on_Pointer, sz_Pointer);
        //    68      if (rv < 0) {
        if (rv < 0) {
            //    69          if (errno == ENOPROTOOPT) {
            if (Errno.errno() == Errno.ENOPROTOOPT()) {
                //    70              JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                //    71                              "unsupported socket option");
                throw new UnsupportedOperationException("unsupported socket option");
            } else {
                //    73              JNU_ThrowByNameWithLastError(env, "java/net/SocketException",
                //    74                                          "get option TCP_QUICKACK failed");
                throw new SocketException(PosixUtils.lastErrorString("get option TCP_QUICKACK failed"));
            }
        }
        //    77      return on != 0;
        return on_Pointer.read() != 0;
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    85  JNIEXPORT jboolean JNICALL Java_jdk_net_LinuxSocketOptions_quickAckSupported0
    //    86  (JNIEnv *env, jobject unused) {
    @Substitute
    private static boolean quickAckSupported0() {
        /* ( Allow names with underscores: Checkstyle: stop */
        //    87      int one = 1;
        CIntPointer one_Pointer = StackValue.get(CIntPointer.class);
        //    88      int rv, s;
        int rv, s;
        //    89      s = socket(PF_INET, SOCK_STREAM, 0);
        s = socket(PF_INET(), SOCK_STREAM(), 0);
        //    90      if (s < 0) {
        if (s < 0) {
            //    91          return JNI_FALSE;
            return false;
        }
        //    93      rv = setsockopt(s, SOL_SOCKET, TCP_QUICKACK, (void *) &one, sizeof (one));
        rv = setsockopt(s, SOL_SOCKET(), TCP_QUICKACK(), one_Pointer, SizeOf.get(CIntPointer.class));
        //    94      if (rv != 0 && errno == ENOPROTOOPT) {
        if (rv != 0 && Errno.errno() == Errno.ENOPROTOOPT()) {
            //    95          rv = JNI_FALSE;
            rv = 0;
        } else {
            //    97          rv = JNI_TRUE;
            rv = 1;
        }
        //    99      close(s);
        ImageSingletons.lookup(PosixJavaNetClose.class).NET_SocketClose(s);
        //   100      return rv;
        return rv != 0;
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

}

class Util_jdk_net_LinuxSocketOptions {

    /* { Do not re-format commented out C code. @formatter:off */
    //   103  static jint socketOptionSupported(jint sockopt) {
    static int socketOptionSupported(int sockopt) {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   104      jint one = 1;
        CIntPointer one_Pointer = StackValue.get(CIntPointer.class);
        one_Pointer.write(1);
        //   105      jint rv, s;

        int rv, s;
        //   106      s = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
        s = Socket.socket(Socket.PF_INET(), Socket.SOCK_STREAM(), NetinetIn.IPPROTO_TCP());
        //   107      if (s < 0) {
        if (s < 0) {
            //   108          return 0;
            return 0;
        }

        //   110      rv = setsockopt(s, SOL_TCP, sockopt, (void *) &one, sizeof (one));
        rv = setsockopt(s, NetinetTcp.SOL_TCP(), sockopt, one_Pointer, SizeOf.get(CIntPointer.class));
        //   111      if (rv != 0 && errno == ENOPROTOOPT) {
        if (rv != 0 && Errno.errno() == Errno.ENOPROTOOPT()) {
            //   112          rv = 0;
            rv = 0;
        } else {
            //   114          rv = 1;
            rv = 1;
        }
        //   116      close(s);
        ImageSingletons.lookup(PosixJavaNetClose.class).NET_SocketClose(s);

        //   117      return rv;
        return rv;
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   120  static void handleError(JNIEnv *env, jint rv, const char *errmsg) {
    static void handleError(int rv, String errmsg) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   121      if (rv < 0) {
        if (rv < 0) {
            //   122          if (errno == ENOPROTOOPT) {
            if (Errno.errno() == Errno.ENOPROTOOPT()) {
                //   123              JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                //   124                      "unsupported socket option");
                throw new UnsupportedOperationException("unsupported socket option");
            } else {
                //   126              JNU_ThrowByNameWithLastError(env, "java/net/SocketException", errmsg);
                throw new SocketException(PosixUtils.lastErrorString(errmsg));
            }
        }
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

}
