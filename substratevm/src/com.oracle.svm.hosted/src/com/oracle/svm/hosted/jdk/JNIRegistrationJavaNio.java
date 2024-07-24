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

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticallyRegisteredFeature
public class JNIRegistrationJavaNio extends JNIRegistrationUtil implements InternalFeature {

    private static final boolean isJdkSctpModulePresent;
    private static final boolean isJavaNamingModulePresent;

    static {
        Module thisModule = JNIRegistrationJavaNio.class.getModule();
        var sctpModule = ModuleLayer.boot().findModule("jdk.sctp");
        if (sctpModule.isPresent()) {
            thisModule.addReads(sctpModule.get());
        }
        isJdkSctpModulePresent = sctpModule.isPresent();

        var namingModule = ModuleLayer.boot().findModule("java.naming");
        if (namingModule.isPresent()) {
            thisModule.addReads(namingModule.get());
        }
        isJavaNamingModulePresent = namingModule.isPresent();
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        rerunClassInit(a, "sun.nio.ch.IOUtil", "sun.nio.ch.ServerSocketChannelImpl", "sun.nio.ch.DatagramChannelImpl", "sun.nio.ch.FileChannelImpl", "sun.nio.ch.FileKey");
        rerunClassInit(a, "java.nio.file.Files$FileTypeDetectors");
        rerunClassInit(a, "sun.nio.ch.Net", "sun.nio.ch.SocketOptionRegistry$LazyInitialization");
        rerunClassInit(a, "sun.nio.ch.AsynchronousSocketChannelImpl$DefaultOptionsHolder", "sun.nio.ch.AsynchronousServerSocketChannelImpl$DefaultOptionsHolder",
                        "sun.nio.ch.DatagramChannelImpl$DefaultOptionsHolder", "sun.nio.ch.ServerSocketChannelImpl$DefaultOptionsHolder", "sun.nio.ch.SocketChannelImpl$DefaultOptionsHolder");
        /* Ensure that the interrupt signal handler is initialized at runtime. */
        rerunClassInit(a, "sun.nio.ch.NativeThread");
        rerunClassInit(a, "sun.nio.ch.FileDispatcherImpl", "sun.nio.ch.FileChannelImpl$Unmapper");

        if (isPosix()) {
<<<<<<< HEAD
            rerunClassInit(a, "sun.nio.ch.SimpleAsynchronousFileChannelImpl", "sun.nio.ch.SimpleAsynchronousFileChannelImpl$DefaultExecutorHolder",
=======
            initializeAtRunTime(a, "sun.nio.ch.InheritedChannel");
            initializeAtRunTime(a, "sun.nio.ch.SimpleAsynchronousFileChannelImpl", "sun.nio.ch.SimpleAsynchronousFileChannelImpl$DefaultExecutorHolder",
>>>>>>> bf74c97415d (Fixed a crash in InheritedChannel.inetPeerAddress0().)
                            "sun.nio.ch.SinkChannelImpl", "sun.nio.ch.SourceChannelImpl");
            rerunClassInit(a, "sun.nio.fs.UnixNativeDispatcher", "sun.nio.ch.UnixAsynchronousServerSocketChannelImpl");
            if (isLinux() && isJdkSctpModulePresent) {
                rerunClassInit(a, "sun.nio.ch.sctp.SctpChannelImpl");
            }
        } else if (isWindows()) {
            rerunClassInit(a, "sun.nio.ch.WindowsAsynchronousFileChannelImpl", "sun.nio.ch.WindowsAsynchronousFileChannelImpl$DefaultIocpHolder");
            rerunClassInit(a, "sun.nio.fs.WindowsNativeDispatcher", "sun.nio.fs.WindowsSecurity", "sun.nio.ch.Iocp",
                            "sun.nio.ch.WindowsAsynchronousServerSocketChannelImpl", "sun.nio.ch.WindowsAsynchronousSocketChannelImpl");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (isPosix()) {
            registerForThrowNew(a, "sun.nio.fs.UnixException");
            RuntimeJNIAccess.register(constructor(a, "sun.nio.fs.UnixException", int.class));
        } else if (isWindows()) {
            registerForThrowNew(a, "sun.nio.fs.WindowsException");
            RuntimeJNIAccess.register(constructor(a, "sun.nio.fs.WindowsException", int.class));
        }

        // JDK-8220738
        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerNetInitIDs, method(a, "sun.nio.ch.Net", "initIDs"));
        if (JavaVersionUtil.JAVA_SPEC <= 17) {
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerFileChannelImplInitIDs, method(a, "sun.nio.ch.FileChannelImpl", "initIDs"));
        }
        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerFileKeyInitIDs, method(a, "sun.nio.ch.FileKey", "initIDs"));

        if (isPosix()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerUnixNativeDispatcherInit, method(a, "sun.nio.fs.UnixNativeDispatcher", "init"));
            if (isLinux() && isJdkSctpModulePresent) {
                a.registerReachabilityHandler(JNIRegistrationJavaNio::registerSctpChannelImplInitIDs, method(a, "sun.nio.ch.sctp.SctpChannelImpl", "initIDs"));
            }

        } else if (isWindows()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerWindowsNativeDispatcherInitIDs, method(a, "sun.nio.fs.WindowsNativeDispatcher", "initIDs"));
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerIocpInitIDs, method(a, "sun.nio.ch.Iocp", "initIDs"));
        }

        if (isJavaNamingModulePresent) {
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerConnectionCreateInetSocketAddress,
                            method(a, "com.sun.jndi.ldap.Connection", "createInetSocketAddress", String.class, int.class));
        }

        Consumer<DuringAnalysisAccess> registerInitInetAddressIDs = JNIRegistrationJavaNet::registerInitInetAddressIDs;
        a.registerReachabilityHandler(registerInitInetAddressIDs, method(a, "sun.nio.ch.Net", "initIDs"));

        /*
         * In JDK 17, all of the Buffer classes require MemorySegmentProxy which is accessed via
         * reflection.
         */
        if (JavaVersionUtil.JAVA_SPEC == 17) {
            RuntimeReflection.register(clazz(a, "jdk.internal.access.foreign.MemorySegmentProxy"));
        }
    }

    private static void registerNetInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(clazz(a, "java.net.InetSocketAddress"));
        RuntimeJNIAccess.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
    }

    private static void registerFileChannelImplInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(fields(a, "sun.nio.ch.FileChannelImpl", "fd"));
    }

    private static void registerFileKeyInitIDs(DuringAnalysisAccess a) {
        if (isPosix()) {
            RuntimeJNIAccess.register(fields(a, "sun.nio.ch.FileKey", "st_dev", "st_ino"));
        } else if (isWindows()) {
            RuntimeJNIAccess.register(fields(a, "sun.nio.ch.FileKey", "dwVolumeSerialNumber", "nFileIndexHigh", "nFileIndexLow"));
        }
    }

    private static void registerUnixNativeDispatcherInit(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.UnixFileAttributes"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.UnixFileAttributes",
                        "st_mode", "st_ino", "st_dev", "st_rdev", "st_nlink", "st_uid", "st_gid", "st_size",
                        "st_atime_sec", "st_atime_nsec", "st_mtime_sec", "st_mtime_nsec", "st_ctime_sec", "st_ctime_nsec"));
        if (isDarwin() || JavaVersionUtil.JAVA_SPEC >= 21 && isLinux()) {
            RuntimeJNIAccess.register(fields(a, "sun.nio.fs.UnixFileAttributes", "st_birthtime_sec"));
        }

        Field unixCreationTimeField = ReflectionUtil.lookupField(true, clazz(a, "sun.nio.fs.UnixFileAttributes"), "st_birthtime_nsec");
        if (unixCreationTimeField != null) {
            RuntimeJNIAccess.register(fields(a, "sun.nio.fs.UnixFileAttributes", "st_birthtime_nsec"));
        }

        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.UnixFileStoreAttributes"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.UnixFileStoreAttributes", "f_frsize", "f_blocks", "f_bfree", "f_bavail"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.UnixMountEntry"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.UnixMountEntry", "name", "dir", "fstype", "opts", "dev"));

        /*
         * Registrations shared between all OS-specific subclasses of UnixNativeDispatcher,
         * therefore we factor it out here.
         */
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.UnixMountEntry"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.UnixMountEntry", "name", "dir", "fstype", "opts"));

    }

    private static void registerSctpChannelImplInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.sctp.MessageInfoImpl"));
        RuntimeJNIAccess.register(constructor(a, "sun.nio.ch.sctp.MessageInfoImpl", int.class, SocketAddress.class, int.class, int.class, boolean.class, boolean.class, int.class));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.sctp.ResultContainer"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.ch.sctp.ResultContainer", "value", "type"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.sctp.SendFailed"));
        RuntimeJNIAccess.register(constructor(a, "sun.nio.ch.sctp.SendFailed", int.class, SocketAddress.class, ByteBuffer.class, int.class, int.class));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.sctp.AssociationChange"));
        RuntimeJNIAccess.register(constructor(a, "sun.nio.ch.sctp.AssociationChange", int.class, int.class, int.class, int.class));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.sctp.PeerAddrChange"));
        RuntimeJNIAccess.register(constructor(a, "sun.nio.ch.sctp.PeerAddrChange", int.class, SocketAddress.class, int.class));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.sctp.Shutdown"));
        RuntimeJNIAccess.register(constructor(a, "sun.nio.ch.sctp.Shutdown", int.class));
    }

    private static void registerWindowsNativeDispatcherInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$FirstFile"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$FirstFile", "handle", "name", "attributes"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$FirstStream"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$FirstStream", "handle", "name"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$VolumeInformation"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$VolumeInformation", "fileSystemName", "volumeName", "volumeSerialNumber", "flags"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace", "freeBytesAvailable", "totalNumberOfBytes", "totalNumberOfFreeBytes"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace", "bytesPerSector"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$Account"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$Account", "domain", "name", "use"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$AclInformation"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$AclInformation", "aceCount"));
        RuntimeJNIAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$CompletionStatus"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$CompletionStatus", "error", "bytesTransferred", "completionKey"));
    }

    private static void registerIocpInitIDs(DuringAnalysisAccess a) {
        RuntimeJNIAccess.register(clazz(a, "sun.nio.ch.Iocp$CompletionStatus"));
        RuntimeJNIAccess.register(fields(a, "sun.nio.ch.Iocp$CompletionStatus", "error", "bytesTransferred", "completionKey", "overlapped"));
    }

    private static void registerConnectionCreateInetSocketAddress(DuringAnalysisAccess a) {
        RuntimeReflection.register(clazz(a, "java.net.InetSocketAddress"));
        RuntimeReflection.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
    }
}
