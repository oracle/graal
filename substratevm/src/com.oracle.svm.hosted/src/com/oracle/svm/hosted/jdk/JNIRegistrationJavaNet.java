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
package com.oracle.svm.hosted.jdk;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.SocketAddress;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticallyRegisteredFeature
class JNIRegistrationJavaNet extends JNIRegistrationUtil implements InternalFeature {
    private boolean hasPlatformSocketOptions;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        /* jdk.net.ExtendedSocketOptions is only available if the jdk.net module is loaded. */
        this.hasPlatformSocketOptions = a.findClassByName("jdk.net.ExtendedSocketOptions$PlatformSocketOptions") != null;
<<<<<<< HEAD
        rerunClassInit(a, "java.net.DatagramPacket", "java.net.InetAddress", "java.net.NetworkInterface",
=======
        initializeAtRunTime(a, "java.net.DatagramPacket", "java.net.NetworkInterface",
                        /*
                         * InetAddress would be enough ("initialized-at-runtime" is propagated to
                         * subclasses) but for documentation purposes we mention all subclasses
                         * anyway (each subclass has its own static constructor that calls native
                         * code).
                         */
                        "java.net.InetAddress", "java.net.Inet4Address", "java.net.Inet6Address",
>>>>>>> bf74c97415d (Fixed a crash in InheritedChannel.inetPeerAddress0().)
                        /* Stores a default SSLContext in a static field. */
                        "javax.net.ssl.SSLContext");
        if (JavaVersionUtil.JAVA_SPEC < 19) {
            /* Removed by https://bugs.openjdk.java.net/browse/JDK-8253119 */
            rerunClassInit(a, "java.net.SocketInputStream", "java.net.SocketOutputStream",
                            /* Caches networking properties. */
                            "java.net.DefaultDatagramSocketImplFactory");
            if (isWindows()) {
                /* Caches networking properties. */
                rerunClassInit(a, "java.net.PlainSocketImpl", "java.net.DualStackPlainDatagramSocketImpl", "java.net.TwoStacksPlainDatagramSocketImpl");
            } else {
                assert isPosix();
                rerunClassInit(a, "java.net.PlainDatagramSocketImpl", "java.net.PlainSocketImpl");
                rerunClassInit(a, "java.net.AbstractPlainDatagramSocketImpl", "java.net.AbstractPlainSocketImpl");
            }
        }

        if (this.hasPlatformSocketOptions && (isPosix() || JavaVersionUtil.JAVA_SPEC >= 19)) {
            /*
             * The libextnet was actually introduced in Java 9, but the support for Linux, Darwin
             * and Windows was added later in Java 10, Java 11 and Java 19 respectively.
             */
            rerunClassInit(a, "jdk.net.ExtendedSocketOptions", "jdk.net.ExtendedSocketOptions$PlatformSocketOptions", "sun.net.ext.ExtendedSocketOptions");
        }
        if (isDarwin()) {
            /* Caches the default interface. */
            rerunClassInit(a, "java.net.DefaultInterface");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        /*
         * It is difficult to track down all the places where exceptions are thrown via JNI. And
         * unconditional registration is cheap because the exception classes have no dependency on
         * the actual network implementation. Therefore, we register them unconditionally.
         */
        registerForThrowNew(a, "java.net.BindException", "java.net.ConnectException",
                        "java.net.NoRouteToHostException", "java.net.PortUnreachableException",
                        "java.net.ProtocolException", "java.net.SocketException", "java.net.SocketTimeoutException",
                        "java.net.UnknownHostException", "sun.net.ConnectionResetException");

        /*
         * InetAddress, Inet4Address, and Inet6Address are registered from many places in the JDK,
         * so it does not make sense to separate them.
         */
        if (JavaVersionUtil.JAVA_SPEC < 19) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInitInetAddressIDs,
                            method(a, "java.net.InetAddress", "init"),
                            /* The next two methods call initInetAddressIDs directly. */
                            method(a, "java.net.Inet4AddressImpl", "lookupAllHostAddr", String.class),
                            method(a, "java.net.Inet6AddressImpl", "lookupAllHostAddr", String.class));
        } else {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInitInetAddressIDs,
                            method(a, "java.net.InetAddress", "init"),
                            /* The next two methods call initInetAddressIDs directly. */
                            method(a, "java.net.Inet4AddressImpl", "lookupAllHostAddr", String.class),
                            method(a, "java.net.Inet6AddressImpl", "lookupAllHostAddr", String.class, int.class));
        }
        if (JavaVersionUtil.JAVA_SPEC < 19) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInetAddressLoadImpl,
                            method(a, "java.net.InetAddress", "loadImpl", String.class));
        }

        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerNetworkInterfaceInit,
                        method(a, "java.net.NetworkInterface", "init"));

        if (JavaVersionUtil.JAVA_SPEC < 19) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDatagramPacketInit,
                            method(a, "java.net.DatagramPacket", "init"));
        }

        if (JavaVersionUtil.JAVA_SPEC < 19) {
            /* Removed by https://bugs.openjdk.java.net/browse/JDK-8253119 */
            String plainDatagramSocketImpl = isWindows() ? "TwoStacksPlainDatagramSocketImpl" : "PlainDatagramSocketImpl";
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainDatagramSocketImplInit,
                            method(a, "java.net." + plainDatagramSocketImpl, "init"));
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainDatagramSocketImplSocketGetOption,
                            method(a, "java.net." + plainDatagramSocketImpl, "socketGetOption", int.class));

            if (isPosix()) {
                String plainSocketImpl = isWindows() ? "TwoStacksPlainSocketImpl" : "PlainSocketImpl";
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainSocketImplInitProto,
                                method(a, "java.net." + plainSocketImpl, "initProto"));
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainSocketImplSocketGetOption,
                                method(a, "java.net." + plainSocketImpl, "socketGetOption", int.class, Object.class));
            }
            if (isWindows()) {
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDualStackPlainDatagramSocketImplInitIDs,
                                method(a, "java.net.DualStackPlainDatagramSocketImpl", "initIDs"));
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDualStackPlainSocketImplInitIDs,
                                method(a, "java.net.PlainSocketImpl", "initIDs"));
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDualStackPlainSocketImplLocalAddress,
                                method(a, "java.net.PlainSocketImpl", "localAddress", int.class, clazz(a, "java.net.InetAddressContainer")));
            }
        }
        if (this.hasPlatformSocketOptions && (isPosix() || JavaVersionUtil.JAVA_SPEC >= 19)) {
            /* Support for the libextnet. */
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlatformSocketOptionsCreate,
                            method(a, "jdk.net.ExtendedSocketOptions$PlatformSocketOptions", "create"));
        }

        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDefaultProxySelectorInit, method(a, "sun.net.spi.DefaultProxySelector", "init"));
    }

    static void registerInitInetAddressIDs(DuringAnalysisAccess a) {
        if (isRunOnce(JNIRegistrationJavaNet::registerInitInetAddressIDs)) {
            return; /* Already registered. */
        }

        /* Java_java_net_InetAddress_init */
        RuntimeJNIAccess.register(fields(a, "java.net.InetAddress", "holder"));
        if (JavaVersionUtil.JAVA_SPEC <= 17) {
            RuntimeJNIAccess.register(fields(a, "java.net.InetAddress", "preferIPv6Address"));
        }
        RuntimeJNIAccess.register(fields(a, "java.net.InetAddress$InetAddressHolder", "address", "family", "hostName", "originalHostName"));

        /* Java_java_net_Inet4Address_init */
        RuntimeJNIAccess.register(constructor(a, "java.net.Inet4Address"));

        /* Java_java_net_Inet6Address_init */
        RuntimeJNIAccess.register(constructor(a, "java.net.Inet6Address"));
        RuntimeJNIAccess.register(fields(a, "java.net.Inet6Address", "holder6"));
        RuntimeJNIAccess.register(fields(a, "java.net.Inet6Address$Inet6AddressHolder", "ipaddress", "scope_id", "scope_id_set", "scope_ifname"));
    }

    private static void registerInetAddressLoadImpl(DuringAnalysisAccess a) {
        RuntimeReflection.register(clazz(a, "java.net.Inet4AddressImpl"));
        RuntimeReflection.register(constructor(a, "java.net.Inet4AddressImpl"));
        RuntimeReflection.register(clazz(a, "java.net.Inet6AddressImpl"));
        RuntimeReflection.register(constructor(a, "java.net.Inet6AddressImpl"));
    }

    private static void registerNetworkInterfaceInit(DuringAnalysisAccess a) {
        if (isRunOnce(JNIRegistrationJavaNet::registerNetworkInterfaceInit)) {
            return; /* Already registered. */
        }

        RuntimeJNIAccess.register(constructor(a, "java.net.NetworkInterface"));
        RuntimeJNIAccess.register(fields(a, "java.net.NetworkInterface", "name", "displayName", "index", "addrs", "bindings", "childs"));
        if (isPosix()) {
            RuntimeJNIAccess.register(fields(a, "java.net.NetworkInterface", "virtual", "parent"));
            if (JavaVersionUtil.JAVA_SPEC < 20) {
                RuntimeJNIAccess.register(fields(a, "java.net.NetworkInterface", "defaultIndex"));
            }
        }

        RuntimeJNIAccess.register(constructor(a, "java.net.InterfaceAddress"));
        RuntimeJNIAccess.register(fields(a, "java.net.InterfaceAddress", "address", "broadcast", "maskLength"));

        registerInitInetAddressIDs(a);
    }

    private static void registerDatagramPacketInit(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.net.DatagramPacket", "address", "port", "buf", "offset", "length", "bufLength"));
    }

    private static void registerPlainDatagramSocketImplInit(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.net.DatagramSocketImpl", "fd", "localPort"));
        RuntimeJNIAccess.register(fields(a, "java.net.AbstractPlainDatagramSocketImpl", "timeout", "trafficClass", "connected"));
        if (isWindows()) {
            RuntimeJNIAccess.register(fields(a, "java.net.TwoStacksPlainDatagramSocketImpl", "fd1", "fduse", "lastfd"));
            registerInitInetAddressIDs(a);
        } else {
            RuntimeJNIAccess.register(fields(a, "java.net.AbstractPlainDatagramSocketImpl", "connectedAddress", "connectedPort"));
            registerNetworkInterfaceInit(a);
        }
    }

    private static void registerPlainDatagramSocketImplSocketGetOption(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(method(a, "java.net.InetAddress", "anyLocalAddress"));
        RuntimeReflection.register(clazz(a, "[Ljava.net.Inet4Address;")); /* Created via JNI. */
    }

    private static void registerDualStackPlainDatagramSocketImplInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.net.DatagramSocketImpl", "fd"));
        registerInitInetAddressIDs(a);
    }

    private static void registerPlainSocketImplInitProto(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.net.SocketImpl", "fd", "address", "port", "localport", "serverSocket"));
        RuntimeJNIAccess.register(fields(a, "java.net.AbstractPlainSocketImpl", "timeout", "trafficClass"));
        if (isWindows()) {
            RuntimeJNIAccess.register(fields(a, "java.net.TwoStacksPlainSocketImpl", "fd1", "lastfd"));
        } else {
            RuntimeJNIAccess.register(fields(a, "java.net.AbstractPlainSocketImpl", "fdLock", "closePending"));
            registerInitInetAddressIDs(a);
        }
    }

    private static void registerPlainSocketImplSocketGetOption(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.net.InetAddressContainer", "addr"));
    }

    private static void registerDualStackPlainSocketImplInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
        registerInitInetAddressIDs(a);
    }

    private static void registerDualStackPlainSocketImplLocalAddress(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "java.net.InetAddressContainer", "addr"));
    }

    private static void registerPlatformSocketOptionsCreate(DuringAnalysisAccess a) {
        String implClassName;
        if (isLinux()) {
            implClassName = "jdk.net.LinuxSocketOptions";
        } else if (isDarwin()) {
            implClassName = "jdk.net.MacOSXSocketOptions";
        } else {
            VMError.guarantee(isWindows(), "Unexpected platform");
            implClassName = "jdk.net.WindowsSocketOptions";
        }
        RuntimeReflection.register(clazz(a, implClassName));
        RuntimeReflection.register(constructor(a, implClassName));
    }

    private static void registerDefaultProxySelectorInit(DuringAnalysisAccess a) {
        if (isWindows()) {
            DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
            access.getNativeLibraries().addDynamicNonJniLibrary("winhttp");
        }

        RuntimeJNIAccess.register(constructor(a, "java.net.Proxy", Proxy.Type.class, SocketAddress.class));
        RuntimeJNIAccess.register(fields(a, "java.net.Proxy", "NO_PROXY"));

        RuntimeJNIAccess.register(fields(a, "java.net.Proxy$Type", "HTTP", "SOCKS"));

        RuntimeJNIAccess.register(method(a, "java.net.InetSocketAddress", "createUnresolved", String.class, int.class));
    }
}
