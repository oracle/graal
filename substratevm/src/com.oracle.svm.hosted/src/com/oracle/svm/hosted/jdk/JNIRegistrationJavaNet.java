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

import java.net.DatagramPacket;
import java.net.InetAddress;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.core.util.VMError;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticFeature
class JNIRegistrationJavaNet extends JNIRegistrationUtil implements Feature {

    private boolean hasExtendedOptionsImpl;
    private boolean hasPlatformSocketOptions;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        hasExtendedOptionsImpl = a.findClassByName("sun.net.ExtendedOptionsImpl") != null;
        hasPlatformSocketOptions = a.findClassByName("jdk.net.ExtendedSocketOptions$PlatformSocketOptions") != null;

        rerunClassInit(a, "java.net.DatagramPacket", "java.net.InetAddress", "java.net.NetworkInterface",
                        "java.net.SocketInputStream", "java.net.SocketOutputStream",
                        /* Caches networking properties. */
                        "java.net.DefaultDatagramSocketImplFactory");
        if (isWindows()) {
            rerunClassInit(a, "java.net.DualStackPlainDatagramSocketImpl", "java.net.TwoStacksPlainDatagramSocketImpl");
            if (JavaVersionUtil.JAVA_SPEC < 11) {
                rerunClassInit(a, "java.net.DualStackPlainSocketImpl", "java.net.TwoStacksPlainSocketImpl",
                                /* Caches networking properties. */
                                "java.net.PlainSocketImpl");
            } else {
                /* The other implementations are merged into PlainSocketImpl. */
                rerunClassInit(a, "java.net.PlainSocketImpl");
            }
        } else {
            assert isPosix();
            rerunClassInit(a, "java.net.PlainDatagramSocketImpl", "java.net.PlainSocketImpl");
            if (hasExtendedOptionsImpl) {
                rerunClassInit(a, "sun.net.ExtendedOptionsImpl");
            }
            if (JavaVersionUtil.JAVA_SPEC >= 11) {
                /* These two classes cache whether SO_REUSEPORT, added in Java 9, is supported. */
                rerunClassInit(a, "java.net.AbstractPlainDatagramSocketImpl", "java.net.AbstractPlainSocketImpl");
            }
            if (hasPlatformSocketOptions) {
                /*
                 * The libextnet was actually introduced in Java 9, but the support for Linux and
                 * Darwin was added later in Java 10 and Java 11 respectively.
                 */
                rerunClassInit(a, "jdk.net.ExtendedSocketOptions", "jdk.net.ExtendedSocketOptions$PlatformSocketOptions");
                /*
                 * Different JDK versions are not consistent about the "ext" in the package name. We
                 * need to support both variants.
                 */
                if (a.findClassByName("sun.net.ext.ExtendedSocketOptions") != null) {
                    rerunClassInit(a, "sun.net.ext.ExtendedSocketOptions");
                } else {
                    rerunClassInit(a, "sun.net.ExtendedSocketOptions");
                }
            }
            if (isDarwin()) {
                /* Caches the default interface. */
                rerunClassInit(a, "java.net.DefaultInterface");
            }
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
        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInitInetAddressIDs,
                        method(a, "java.net.InetAddress", "init"),
                        /* The next two methods call initInetAddressIDs directly. */
                        method(a, "java.net.Inet4AddressImpl", "lookupAllHostAddr", String.class),
                        method(a, "java.net.Inet6AddressImpl", "lookupAllHostAddr", String.class));
        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerInetAddressLoadImpl,
                        method(a, "java.net.InetAddress", "loadImpl", String.class));

        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerNetworkInterfaceInit,
                        method(a, "java.net.NetworkInterface", "init"));

        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDatagramPacketInit,
                        method(a, "java.net.DatagramPacket", "init"));

        if (JavaVersionUtil.JAVA_SPEC < 15) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDatagramSocketCheckOldImpl,
                            method(a, "java.net.DatagramSocket", "checkOldImpl"));
        }

        String plainDatagramSocketImpl = isWindows() ? "TwoStacksPlainDatagramSocketImpl" : "PlainDatagramSocketImpl";
        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainDatagramSocketImplInit,
                        method(a, "java.net." + plainDatagramSocketImpl, "init"));
        a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainDatagramSocketImplSocketGetOption,
                        method(a, "java.net." + plainDatagramSocketImpl, "socketGetOption", int.class));

        if (JavaVersionUtil.JAVA_SPEC < 11 || isPosix()) {
            String plainSocketImpl = isWindows() ? "TwoStacksPlainSocketImpl" : "PlainSocketImpl";
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainSocketImplInitProto,
                            method(a, "java.net." + plainSocketImpl, "initProto"));
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlainSocketImplSocketGetOption,
                            method(a, "java.net." + plainSocketImpl, "socketGetOption", int.class, Object.class));
        }
        if (isWindows()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDualStackPlainDatagramSocketImplInitIDs,
                            method(a, "java.net.DualStackPlainDatagramSocketImpl", "initIDs"));

            String dualStackPlainSocketImpl = JavaVersionUtil.JAVA_SPEC < 11 ? "DualStackPlainSocketImpl" : "PlainSocketImpl";
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDualStackPlainSocketImplInitIDs,
                            method(a, "java.net." + dualStackPlainSocketImpl, "initIDs"));
            a.registerReachabilityHandler(JNIRegistrationJavaNet::registerDualStackPlainSocketImplLocalAddress,
                            method(a, "java.net." + dualStackPlainSocketImpl, "localAddress", int.class, clazz(a, "java.net.InetAddressContainer")));
        } else {
            assert isPosix();
            if (hasExtendedOptionsImpl) {
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerExtendedOptionsImplInit,
                                method(a, "sun.net.ExtendedOptionsImpl", "init"));
            }
            if (hasPlatformSocketOptions) {
                /* Support for the libextnet. */
                a.registerReachabilityHandler(JNIRegistrationJavaNet::registerPlatformSocketOptionsCreate,
                                method(a, "jdk.net.ExtendedSocketOptions$PlatformSocketOptions", "create"));
            }
        }
    }

    static void registerInitInetAddressIDs(DuringAnalysisAccess a) {
        if (isRunOnce(JNIRegistrationJavaNet::registerInitInetAddressIDs)) {
            return; /* Already registered. */
        }

        /* Java_java_net_InetAddress_init */
        JNIRuntimeAccess.register(fields(a, "java.net.InetAddress", "holder", "preferIPv6Address"));
        JNIRuntimeAccess.register(fields(a, "java.net.InetAddress$InetAddressHolder", "address", "family", "hostName", "originalHostName"));

        /* Java_java_net_Inet4Address_init */
        JNIRuntimeAccess.register(constructor(a, "java.net.Inet4Address"));

        /* Java_java_net_Inet6Address_init */
        JNIRuntimeAccess.register(constructor(a, "java.net.Inet6Address"));
        JNIRuntimeAccess.register(fields(a, "java.net.Inet6Address", "holder6"));
        if (JavaVersionUtil.JAVA_SPEC < 13) {
            JNIRuntimeAccess.register(fields(a, "java.net.Inet6Address", "cached_scope_id"));
        }
        JNIRuntimeAccess.register(fields(a, "java.net.Inet6Address$Inet6AddressHolder", "ipaddress", "scope_id", "scope_id_set", "scope_ifname"));
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

        JNIRuntimeAccess.register(constructor(a, "java.net.NetworkInterface"));
        JNIRuntimeAccess.register(fields(a, "java.net.NetworkInterface", "name", "displayName", "index", "addrs", "bindings", "childs"));
        if (isPosix()) {
            JNIRuntimeAccess.register(fields(a, "java.net.NetworkInterface", "virtual", "parent", "defaultIndex"));
        }

        JNIRuntimeAccess.register(constructor(a, "java.net.InterfaceAddress"));
        JNIRuntimeAccess.register(fields(a, "java.net.InterfaceAddress", "address", "broadcast", "maskLength"));

        registerInitInetAddressIDs(a);
    }

    private static void registerDatagramPacketInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.DatagramPacket", "address", "port", "buf", "offset", "length", "bufLength"));
    }

    private static void registerDatagramSocketCheckOldImpl(DuringAnalysisAccess a) {
        a.registerSubtypeReachabilityHandler((access, clazz) -> {
            // Checkstyle: stop
            if (!java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                // Checkstyle: resume
                RuntimeReflection.register(method(access, clazz.getName(), "peekData", DatagramPacket.class));
            }
        }, clazz(a, "java.net.DatagramSocketImpl"));
    }

    private static void registerPlainDatagramSocketImplInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.DatagramSocketImpl", "fd", "localPort"));
        JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainDatagramSocketImpl", "timeout", "trafficClass", "connected"));
        if (isWindows()) {
            JNIRuntimeAccess.register(fields(a, "java.net.TwoStacksPlainDatagramSocketImpl", "fd1", "fduse", "lastfd"));
            registerInitInetAddressIDs(a);
        } else {
            JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainDatagramSocketImpl", "connectedAddress", "connectedPort"));
            registerNetworkInterfaceInit(a);
        }
    }

    private static void registerPlainDatagramSocketImplSocketGetOption(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(method(a, "java.net.InetAddress", "anyLocalAddress"));
        RuntimeReflection.register(clazz(a, "[Ljava.net.Inet4Address;")); /* Created via JNI. */
    }

    private static void registerDualStackPlainDatagramSocketImplInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.DatagramSocketImpl", "fd"));
        registerInitInetAddressIDs(a);
    }

    private static void registerPlainSocketImplInitProto(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.SocketImpl", "fd", "address", "port", "localport", "serverSocket"));
        JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainSocketImpl", "timeout", "trafficClass"));
        if (isWindows()) {
            JNIRuntimeAccess.register(fields(a, "java.net.TwoStacksPlainSocketImpl", "fd1", "lastfd"));
        } else {
            JNIRuntimeAccess.register(fields(a, "java.net.AbstractPlainSocketImpl", "fdLock", "closePending"));
            registerInitInetAddressIDs(a);
        }
    }

    private static void registerPlainSocketImplSocketGetOption(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.InetAddressContainer", "addr"));
    }

    private static void registerDualStackPlainSocketImplInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
        registerInitInetAddressIDs(a);
    }

    private static void registerDualStackPlainSocketImplLocalAddress(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.net.InetAddressContainer", "addr"));
    }

    private static void registerExtendedOptionsImplInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "jdk.net.SocketFlow"));
        JNIRuntimeAccess.register(fields(a, "jdk.net.SocketFlow", "status", "priority", "bandwidth"));

        JNIRuntimeAccess.register(clazz(a, "jdk.net.SocketFlow$Status"));
        JNIRuntimeAccess.register(fields(a, "jdk.net.SocketFlow$Status", "NO_STATUS", "OK", "NO_PERMISSION", "NOT_CONNECTED", "NOT_SUPPORTED", "ALREADY_CREATED", "IN_PROGRESS", "OTHER"));
    }

    private static void registerPlatformSocketOptionsCreate(DuringAnalysisAccess a) {
        String implClassName;
        if (isLinux()) {
            implClassName = "jdk.net.LinuxSocketOptions";
        } else if (isDarwin()) {
            implClassName = "jdk.net.MacOSXSocketOptions";
        } else {
            throw VMError.shouldNotReachHere("Unexpected platform");
        }
        RuntimeReflection.register(clazz(a, implClassName));
        RuntimeReflection.register(constructor(a, implClassName));
    }
}
