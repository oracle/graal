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
package com.oracle.svm.core.posix.jdk11.darwin;

import java.net.SocketException;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.headers.Errno;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.posix.PosixJavaNetClose;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Socket;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;

import static com.oracle.svm.core.posix.headers.NetinetIn.IPPROTO_TCP;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_KEEPALIVE;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_KEEPCNT;
import static com.oracle.svm.core.posix.headers.NetinetTcp.TCP_KEEPINTVL;
import static com.oracle.svm.core.posix.jdk11.darwin.Util_jdk_net_MacOSXSocketOptions.handleError;
import static com.oracle.svm.core.posix.jdk11.darwin.Util_jdk_net_MacOSXSocketOptions.socketOptionSupported;

@TargetClass(className = "jdk.net.MacOSXSocketOptions", onlyWith = JDK11OrLater.class)
@Platforms({Platform.DARWIN_AMD64.class})
public final class Target_jdk_net_MacOSXSocketOptions {

    // ported from {jdk11}/src/jdk.net/macosx/native/libextnet/MacOSXSocketOptions.c

    /* { Do not re-format commented out C code. @formatter:off */
    //   140  JNIEXPORT jint JNICALL Java_jdk_net_MacOSXSocketOptions_getTcpKeepAliveIntvl0
    //   141  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static int getTcpKeepAliveIntvl0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   142      jint optval, rv;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        int rv;
        //   143      socklen_t sz = sizeof (optval);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //   144      rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, &sz);
        rv = Socket.getsockopt(fd, IPPROTO_TCP(), TCP_KEEPINTVL(), optval_Pointer, sz_Pointer);
        //   145      handleError(env, rv, "get option TCP_KEEPINTVL failed");
        handleError(rv, "get option TCL_KEEPINTVL failed");
        //   146      return optval;
        return optval_Pointer.read();
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   101  JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setTcpKeepAliveIntvl0
    //   102  (JNIEnv *env, jobject unused, jint fd, jint optval) {
    @Substitute
    private static void setTcpKeepAliveIntvl0(int fd, int optval) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        optval_Pointer.write(optval);
        //   103      jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, &optval, sizeof (optval));
        int rv = Socket.setsockopt(fd, IPPROTO_TCP(), TCP_KEEPINTVL(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //   104      handleError(env, rv, "set option TCP_KEEPINTVL failed");
        handleError(rv, "set option TCL_KEEPINTVL failed");
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   126  JNIEXPORT jint JNICALL Java_jdk_net_MacOSXSocketOptions_getTcpKeepAliveTime0
    //   127  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static int getTcpKeepAliveTime0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   128      jint optval, rv;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        int rv;
        //   129      socklen_t sz = sizeof (optval);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //   130      rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPALIVE, &optval, &sz);
        rv = Socket.getsockopt(fd, IPPROTO_TCP(), TCP_KEEPALIVE(), optval_Pointer, sz_Pointer);
        //   131      handleError(env, rv, "get option TCP_KEEPALIVE failed");// mac TCP_KEEPIDLE ->TCP_KEEPALIVE
        handleError(rv, "get option TCP_KEEPALIVE failed");
        //   132      return optval;
        return optval_Pointer.read();
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    90  JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setTcpKeepAliveTime0
    //    91  (JNIEnv *env, jobject unused, jint fd, jint optval) {
    @Substitute
    private static void setTcpKeepAliveTime0(int fd, int optval) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        optval_Pointer.write(optval);
        //    92      jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPALIVE, &optval, sizeof (optval));
        int rv = Socket.setsockopt(fd, IPPROTO_TCP(), TCP_KEEPALIVE(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //    93      handleError(env, rv, "set option TCP_KEEPALIVE failed");// mac TCP_KEEPIDLE ->TCP_KEEPALIVE
        handleError(rv, "set option TCP_KEEPALIVE failed");
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //   112  JNIEXPORT jint JNICALL Java_jdk_net_MacOSXSocketOptions_getTcpkeepAliveProbes0
    //   113  (JNIEnv *env, jobject unused, jint fd) {
    @Substitute
    private static int getTcpkeepAliveProbes0(int fd) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //   114      jint optval, rv;
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        int rv;
        //   115      socklen_t sz = sizeof (optval);
        CIntPointer sz_Pointer = StackValue.get(CIntPointer.class);
        sz_Pointer.write(SizeOf.get(CIntPointer.class));
        //   116      rv = getsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, &sz);
        rv = Socket.getsockopt(fd, IPPROTO_TCP(), TCP_KEEPCNT(), optval_Pointer, sz_Pointer);
        //   117      handleError(env, rv, "get option TCP_KEEPCNT failed");
        handleError(rv, "get option TCP_KEEPCNT failed");
        //   118      return optval;
        return optval_Pointer.read();
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    79  JNIEXPORT void JNICALL Java_jdk_net_MacOSXSocketOptions_setTcpkeepAliveProbes0
    //    80  (JNIEnv *env, jobject unused, jint fd, jint optval) {
    @Substitute
    private static void setTcpkeepAliveProbes0(int fd, int optval) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        CIntPointer optval_Pointer = StackValue.get(CIntPointer.class);
        optval_Pointer.write(optval);
        //    81      jint rv = setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, &optval, sizeof (optval));
        int rv = Socket.setsockopt(fd, IPPROTO_TCP(), TCP_KEEPCNT(), optval_Pointer, SizeOf.get(CIntPointer.class));
        //    82      handleError(env, rv, "set option TCP_KEEPCNT failed");
        handleError(rv, "set option TCP_KEEPCNT failed");
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    68  JNIEXPORT jboolean JNICALL Java_jdk_net_MacOSXSocketOptions_keepAliveOptionsSupported0
    //    69  (JNIEnv *env, jobject unused) {
    @Substitute
    private static boolean keepAliveOptionsSupported0() {
        /* ( Allow names with underscores: Checkstyle: stop */
        //    70      return socketOptionSupported(TCP_KEEPALIVE) && socketOptionSupported(TCP_KEEPCNT)
        //    71              && socketOptionSupported(TCP_KEEPINTVL);
        return socketOptionSupported(TCP_KEEPALIVE()) != 0 && socketOptionSupported(TCP_KEEPCNT()) != 0
                && socketOptionSupported(TCP_KEEPINTVL()) != 0;
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */
}

class Util_jdk_net_MacOSXSocketOptions {
    /* { Do not re-format commented out C code. @formatter:off */
    //    52  static void handleError(JNIEnv *env, jint rv, const char *errmsg) {
    static void handleError(int rv, String errmsg) throws SocketException {
        /* ( Allow names with underscores: Checkstyle: stop */
        //    53      if (rv < 0) {
        if (rv < 0) {
            //    54          if (errno == ENOPROTOOPT) {
            if (Errno.errno() == Errno.ENOPROTOOPT()) {
                //    55              JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                //    56                      "unsupported socket option");
                throw new UnsupportedOperationException("unsupported socket option");
            } else {
                //    58              JNU_ThrowByNameWithLastError(env, "java/net/SocketException", errmsg);
                throw new SocketException(PosixUtils.lastErrorString(errmsg));
            }
        }
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */

    /* { Do not re-format commented out C code. @formatter:off */
    //    35  static jint socketOptionSupported(jint sockopt) {
    static int socketOptionSupported(int sockopt) {
        /* ( Allow names with underscores: Checkstyle: stop */
        //    36      jint one = 1;
        CIntPointer one_Pointer = StackValue.get(CIntPointer.class);
        one_Pointer.write(1);
        //    37      jint rv, s;
        int rv, s;
        //    38      s = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
        s = Socket.socket(Socket.PF_INET(), Socket.SOCK_STREAM(), IPPROTO_TCP());
        //    39      if (s < 0) {
        if (s < 0) {
            //    40          return 0;
            return 0;
        }
        //    42      rv = setsockopt(s, IPPROTO_TCP, sockopt, (void *) &one, sizeof (one));
        rv = Socket.setsockopt(s, IPPROTO_TCP(), sockopt, one_Pointer, SizeOf.get(CIntPointer.class));
        //    43      if (rv != 0 && errno == ENOPROTOOPT) {
        if (rv != 0 && Errno.errno() == Errno.ENOPROTOOPT()) {
            //    44          rv = 0;
            rv = 0;
        }  else {
            //    46          rv = 1;
            rv = 1;
        }
        //    48      close(s);
        ImageSingletons.lookup(PosixJavaNetClose.class).NET_SocketClose(s);
        //    49      return rv;
        return rv;
        /* } Allow names with underscores: Checkstyle: resume */
    }
    /* } Do not re-format commented out C code. @formatter:on */
}
