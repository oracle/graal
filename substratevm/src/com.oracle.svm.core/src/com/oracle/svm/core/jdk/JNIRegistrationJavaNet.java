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
package com.oracle.svm.core.jdk;

import java.net.DatagramPacket;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@CLibrary(value = "net", requireStatic = true)
@AutomaticFeature
class JNIRegistrationJavaNet extends JNIRegistrationUtil implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        rerunClassInit(a, "java.net.NetworkInterface", "java.net.DefaultInterface",
                        "java.net.InetAddress", "java.net.Inet4AddressImpl", "java.net.Inet6AddressImpl",
                        "java.net.SocketInputStream", "java.net.SocketOutputStream",
                        "java.net.DatagramPacket",
                        "java.net.AbstractPlainSocketImpl", "java.net.AbstractPlainDatagramSocketImpl");
        if (isPosix()) {
            rerunClassInit(a, "java.net.PlainSocketImpl", "java.net.PlainDatagramSocketImpl");
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                rerunClassInit(a, "sun.net.ExtendedOptionsImpl");
            }
        }
        if (isWindows()) {
            rerunClassInit(a, "java.net.DualStackPlainSocketImpl", "java.net.TwoStacksPlainSocketImpl",
                            "java.net.DualStackPlainDatagramSocketImpl", "java.net.TwoStacksPlainDatagramSocketImpl");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {

        /*
         * It is difficult to track down all the places where exceptions are thrown via JNI. And
         * unconditional registration is cheap because the exception classes have no dependency on
         * the actual network implementation. Therefore, we register them unconditionally.
         */
        JNIRuntimeAccess.register(clazz(a, "java.net.SocketException"));
        JNIRuntimeAccess.register(constructor(a, "java.net.SocketException", String.class));
        JNIRuntimeAccess.register(clazz(a, "java.net.ConnectException"));
        JNIRuntimeAccess.register(constructor(a, "java.net.ConnectException", String.class));
        JNIRuntimeAccess.register(clazz(a, "java.net.BindException"));
        JNIRuntimeAccess.register(constructor(a, "java.net.BindException", String.class));
        JNIRuntimeAccess.register(clazz(a, "java.net.UnknownHostException"));
        JNIRuntimeAccess.register(constructor(a, "java.net.UnknownHostException", String.class));

        /*
         * InetAddress, Inet4Address, and Inet6Address are registered from many places in the JDK,
         * so it does not make sense to separate them.
         */
        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInitInetAddressIDs,
                        method(a, "java.net.InetAddress", "init"),
                        method(a, "java.net.Inet4Address", "init"),
                        method(a, "java.net.Inet6Address", "init"),
                        method(a, "java.net.NetworkInterface", "init"),
                        method(a, "sun.nio.ch.IOUtil", "initIDs"),
                        clazz(a, "java.net.Inet4AddressImpl"),
                        clazz(a, "java.net.Inet6AddressImpl"));
        if (isPosix()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInitInetAddressIDs,
                            method(a, "java.net.PlainSocketImpl", "initProto"),
                            method(a, "java.net.PlainDatagramSocketImpl", "init"));
        }
        if (isWindows()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInitInetAddressIDs,
                            method(a, "java.net.DualStackPlainSocketImpl", "initIDs"),
                            method(a, "java.net.DualStackPlainDatagramSocketImpl", "initIDs"));
        }

        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerNetworkInterfaceInit,
                        method(a, "java.net.NetworkInterface", "init"));

        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDatagramPacketInit,
                        method(a, "java.net.DatagramPacket", "init"));

        if (isPosix()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainDatagramSocketImplInit,
                            method(a, "java.net.PlainDatagramSocketImpl", "init"));
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerNetworkInterfaceInit,
                            method(a, "java.net.PlainDatagramSocketImpl", "init"));

            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainSocketImplInitProto,
                            method(a, "java.net.PlainSocketImpl", "initProto"));
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerExtendedOptionsImplInit,
                                method(a, "sun.net.ExtendedOptionsImpl", "init"));
            }
        }
    }

    private static void registerInitInetAddressIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.net.InetAddress"));
        JNIRuntimeAccess.register(fields(a, "java.net.InetAddress", "holder", "preferIPv6Address"));
        JNIRuntimeAccess.register(clazz(a, "java.net.InetAddress$InetAddressHolder"));
        JNIRuntimeAccess.register(fields(a, "java.net.InetAddress$InetAddressHolder", "address", "family", "hostName", "originalHostName"));

        JNIRuntimeAccess.register(clazz(a, "java.net.Inet4Address"));
        JNIRuntimeAccess.register(constructor(a, "java.net.Inet4Address"));

        JNIRuntimeAccess.register(clazz(a, "java.net.Inet6Address"));
        JNIRuntimeAccess.register(constructor(a, "java.net.Inet6Address"));
        JNIRuntimeAccess.register(fields(a, "java.net.Inet6Address", "holder6"));
        if (JavaVersionUtil.JAVA_SPEC <= 11) {
            JNIRuntimeAccess.register(fields(a, "java.net.Inet6Address", "cached_scope_id"));
        }

        JNIRuntimeAccess.register(clazz(a, "java.net.Inet6Address$Inet6AddressHolder"));
        JNIRuntimeAccess.register(fields(a, "java.net.Inet6Address$Inet6AddressHolder", "ipaddress", "scope_id", "scope_id_set", "scope_ifname"));
    }

    private static void registerNetworkInterfaceInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.net.NetworkInterface"));
        JNIRuntimeAccess.register(constructor(a, "java.net.NetworkInterface"));
        JNIRuntimeAccess.register(fields(a, "java.net.NetworkInterface", "name", "displayName", "index", "addrs", "bindings", "childs"));

        if (isPosix()) {
            JNIRuntimeAccess.register(fields(a, "java.net.NetworkInterface", "virtual", "parent", "defaultIndex"));
        }

        JNIRuntimeAccess.register(clazz(a, "java.net.InterfaceAddress"));
        JNIRuntimeAccess.register(constructor(a, "java.net.InterfaceAddress"));
        JNIRuntimeAccess.register(fields(a, "java.net.InterfaceAddress", "address", "broadcast", "maskLength"));
    }

    private static void registerDatagramPacketInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.DatagramPacket", "address", "port", "buf", "offset", "length", "bufLength"));

    }

    private static void registerPlainDatagramSocketImplInit(DuringAnalysisAccess a) {
        /* See java.net.DatagramSocket.checkOldImpl */
        RuntimeReflection.register(method(a, "java.net.PlainDatagramSocketImpl", "peekData", DatagramPacket.class));

        JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainDatagramSocketImpl", "timeout", "trafficClass", "connected", "connectedAddress", "connectedPort"));
        JNIRuntimeAccess.register(fields(a, "java.net.DatagramSocketImpl", "fd", "localPort"));

        JNIRuntimeAccess.register(clazz(a, "java.lang.Integer"));
        JNIRuntimeAccess.register(constructor(a, "java.lang.Integer", int.class));
        JNIRuntimeAccess.register(clazz(a, "java.lang.Boolean"));
        JNIRuntimeAccess.register(constructor(a, "java.lang.Boolean", boolean.class));
    }

    private static void registerPlainSocketImplInitProto(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.SocketImpl", "fd", "address", "port", "localport", "serverSocket"));
        JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainSocketImpl", "timeout", "trafficClass"));
        if (isPosix()) {
            JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainSocketImpl", "fdLock", "closePending"));
        }
    }

    private static void registerExtendedOptionsImplInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "jdk.net.SocketFlow"));
        JNIRuntimeAccess.register(fields(a, "jdk.net.SocketFlow", "status", "priority", "bandwidth"));

        JNIRuntimeAccess.register(clazz(a, "jdk.net.SocketFlow$Status"));
        JNIRuntimeAccess.register(fields(a, "jdk.net.SocketFlow$Status", "NO_STATUS", "OK", "NO_PERMISSION", "NOT_CONNECTED", "NOT_SUPPORTED", "ALREADY_CREATED", "IN_PROGRESS", "OTHER"));
    }
}
