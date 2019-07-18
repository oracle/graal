/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import java.net.InetAddress;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.windows.headers.WinSock;

@Platforms(Platform.WINDOWS.class)
@AutomaticFeature
@CLibrary(value = "net", requireStatic = true)
class WindowsJavaNetSubstitutionsFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            /* Common Networking Classes */
            JNIRuntimeAccess.register(access.findClassByName("java.net.InetAddress").getDeclaredMethod("anyLocalAddress"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.InetAddressContainer"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.InetAddressContainer").getDeclaredField("addr"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.InetSocketAddress"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.InetSocketAddress").getDeclaredConstructor(InetAddress.class, int.class));

            /* Required for `initializeEncoding` function in jni_util.c */
            JNIRuntimeAccess.register(String.class.getDeclaredConstructor(byte[].class, String.class));
            JNIRuntimeAccess.register(String.class.getDeclaredMethod("getBytes", String.class));

            /* Windows specific classes */
            JNIRuntimeAccess.register(access.findClassByName("java.net.SocketInputStream"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.SocketOutputStream"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.DualStackPlainDatagramSocketImpl"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.DatagramSocketImpl"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.DatagramSocketImpl").getDeclaredField("fd"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.DatagramSocketImpl").getDeclaredField("localPort"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.AbstractPlainDatagramSocketImpl"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.AbstractPlainDatagramSocketImpl").getDeclaredField("timeout"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.AbstractPlainDatagramSocketImpl").getDeclaredField("trafficClass"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.AbstractPlainDatagramSocketImpl").getDeclaredField("connected"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainDatagramSocketImpl"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainDatagramSocketImpl").getDeclaredField("fd1"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainDatagramSocketImpl").getDeclaredField("fduse"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainDatagramSocketImpl").getDeclaredField("lastfd"));

            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainSocketImpl"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainSocketImpl").getDeclaredField("fd1"));
            JNIRuntimeAccess.register(access.findClassByName("java.net.TwoStacksPlainSocketImpl").getDeclaredField("lastfd"));

            JNIRuntimeAccess.register(access.findClassByName("java.lang.Integer").getDeclaredConstructor(int.class));
            JNIRuntimeAccess.register(access.findClassByName("java.lang.Integer").getDeclaredField("value"));

            JNIRuntimeAccess.register(access.findClassByName("java.lang.Boolean"));
            JNIRuntimeAccess.register(access.findClassByName("java.lang.Boolean").getDeclaredConstructor(boolean.class));
            JNIRuntimeAccess.register(access.findClassByName("java.lang.Boolean").getDeclaredMethod("getBoolean", String.class));

            RuntimeReflection.register(access.findClassByName("java.net.InetAddressImpl"));
            RuntimeReflection.register(access.findClassByName("java.net.Inet4AddressImpl"));
            RuntimeReflection.register(access.findClassByName("java.net.Inet6AddressImpl"));
            RuntimeReflection.registerForReflectiveInstantiation(access.findClassByName("java.net.Inet4AddressImpl"));
            RuntimeReflection.registerForReflectiveInstantiation(access.findClassByName("java.net.Inet6AddressImpl"));

        } catch (Exception e) {
            VMError.shouldNotReachHere("WindowsJavaNetSubstitutionsFeature: Error registering class or method: ", e);
        }
    }
}

@Platforms(Platform.WINDOWS.class)
public final class WindowsJavaNetSubstitutions {

    public static boolean initIDs() {
        WinSock.init();
        return true;
    }
}
