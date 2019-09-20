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

import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@CLibrary(value = "nio", requireStatic = true)
@AutomaticFeature
class JNIRegistrationJavaNio extends JNIRegistrationUtil implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess a) {
        rerunClassInit(a, "sun.nio.ch.IOUtil", "sun.nio.ch.ServerSocketChannelImpl", "sun.nio.ch.DatagramChannelImpl", "sun.nio.ch.FileChannelImpl", "sun.nio.ch.FileKey");
        rerunClassInit(a, "java.nio.file.FileSystems$DefaultFileSystemHolder");
        rerunClassInit(a, "java.nio.file.Files$FileTypeDetectors");
        if (isPosix()) {
            rerunClassInit(a, "sun.nio.fs.UnixNativeDispatcher");
            if (isLinux()) {
                rerunClassInit(a, "sun.nio.ch.sctp.SctpChannelImpl");
            }
        } else if (isWindows()) {
            rerunClassInit(a, "sun.nio.fs.WindowsNativeDispatcher", "sun.nio.fs.WindowsSecurity", "sun.nio.ch.Iocp");
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (isPosix()) {
            registerForThrowNew(a, "sun.nio.fs.UnixException");
            JNIRuntimeAccess.register(constructor(a, "sun.nio.fs.UnixException", int.class));
        } else if (isWindows()) {
            registerForThrowNew(a, "sun.nio.fs.WindowsException");
            JNIRuntimeAccess.register(constructor(a, "sun.nio.fs.WindowsException", int.class));
        }

        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerServerSocketChannelImplInitIDs, method(a, "sun.nio.ch.ServerSocketChannelImpl", "initIDs"));
        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerDatagramChannelImplInitIDs, method(a, "sun.nio.ch.DatagramChannelImpl", "initIDs"));
        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerFileChannelImplInitIDs, method(a, "sun.nio.ch.FileChannelImpl", "initIDs"));
        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerFileKeyInitIDs, method(a, "sun.nio.ch.FileKey", "initIDs"));

        if (isPosix()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerUnixNativeDispatcherInit, method(a, "sun.nio.fs.UnixNativeDispatcher", "init"));
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerDefaultFileSystemProviderCreate, method(a, "sun.nio.fs.DefaultFileSystemProvider", "create"));
            if (isLinux()) {
                a.registerReachabilityHandler(JNIRegistrationJavaNio::registerSctpChannelImplInitIDs, method(a, "sun.nio.ch.sctp.SctpChannelImpl", "initIDs"));
            }

        } else if (isWindows()) {
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerWindowsNativeDispatcherInitIDs, method(a, "sun.nio.fs.WindowsNativeDispatcher", "initIDs"));
            a.registerReachabilityHandler(JNIRegistrationJavaNio::registerIocpInitIDs, method(a, "sun.nio.ch.Iocp", "initIDs"));
        }

        a.registerReachabilityHandler(JNIRegistrationJavaNio::registerConnectionCreateInetSocketAddress, method(a, "com.sun.jndi.ldap.Connection", "createInetSocketAddress", String.class, int.class));
    }

    private static void registerDefaultFileSystemProviderCreate(@SuppressWarnings("unused") DuringAnalysisAccess a) {
        /*
         * The class instantiated on Posix systems depends on the OS, and instantiation is via
         * reflection. So we register exactly the class that is returned by the hosted invocation.
         */
        Object hostedDefaultProvider;
        try {
            hostedDefaultProvider = ReflectionUtil.lookupMethod(sun.nio.fs.DefaultFileSystemProvider.class, "create").invoke(null);
        } catch (ReflectionUtilError e) {
            try {
                // JDK-8213406
                hostedDefaultProvider = ReflectionUtil.lookupMethod(sun.nio.fs.DefaultFileSystemProvider.class, "instance").invoke(null);
            } catch (Exception e2) {
                throw new InternalError(e2);
            }
        } catch (Exception e) {
            throw new InternalError(e);
        }
        RuntimeReflection.register(hostedDefaultProvider.getClass());
        RuntimeReflection.register(ReflectionUtil.lookupConstructor(hostedDefaultProvider.getClass()));
    }

    private static void registerServerSocketChannelImplInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.net.InetSocketAddress"));
        JNIRuntimeAccess.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
    }

    private static void registerDatagramChannelImplInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "java.net.InetSocketAddress"));
        JNIRuntimeAccess.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.DatagramChannelImpl"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.ch.DatagramChannelImpl", "sender", "cachedSenderInetAddress", "cachedSenderPort"));
    }

    private static void registerFileChannelImplInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "sun.nio.ch.FileChannelImpl", "fd"));
    }

    private static void registerFileKeyInitIDs(DuringAnalysisAccess a) {
        if (isPosix()) {
            JNIRuntimeAccess.register(fields(a, "sun.nio.ch.FileKey", "st_dev", "st_ino"));
        } else if (isWindows()) {
            JNIRuntimeAccess.register(fields(a, "sun.nio.ch.FileKey", "dwVolumeSerialNumber", "nFileIndexHigh", "nFileIndexLow"));
        }
    }

    private static void registerUnixNativeDispatcherInit(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.UnixFileAttributes"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.UnixFileAttributes",
                        "st_mode", "st_ino", "st_dev", "st_rdev", "st_nlink", "st_uid", "st_gid", "st_size",
                        "st_atime_sec", "st_atime_nsec", "st_mtime_sec", "st_mtime_nsec", "st_ctime_sec", "st_ctime_nsec"));
        if (isDarwin()) {
            JNIRuntimeAccess.register(fields(a, "sun.nio.fs.UnixFileAttributes", "st_birthtime_sec"));
        }

        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.UnixFileStoreAttributes"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.UnixFileStoreAttributes", "f_frsize", "f_blocks", "f_bfree", "f_bavail"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.UnixMountEntry"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.UnixMountEntry", "name", "dir", "fstype", "opts", "dev"));

        /*
         * Registrations shared between all OS-specific subclasses of UnixNativeDispatcher,
         * therefore we factor it out here.
         */
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.UnixMountEntry"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.UnixMountEntry", "name", "dir", "fstype", "opts"));

    }

    private static void registerSctpChannelImplInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.sctp.MessageInfoImpl"));
        JNIRuntimeAccess.register(constructor(a, "sun.nio.ch.sctp.MessageInfoImpl", int.class, SocketAddress.class, int.class, int.class, boolean.class, boolean.class, int.class));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.sctp.ResultContainer"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.ch.sctp.ResultContainer", "value", "type"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.sctp.SendFailed"));
        JNIRuntimeAccess.register(constructor(a, "sun.nio.ch.sctp.SendFailed", int.class, SocketAddress.class, ByteBuffer.class, int.class, int.class));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.sctp.AssociationChange"));
        JNIRuntimeAccess.register(constructor(a, "sun.nio.ch.sctp.AssociationChange", int.class, int.class, int.class, int.class));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.sctp.PeerAddrChange"));
        JNIRuntimeAccess.register(constructor(a, "sun.nio.ch.sctp.PeerAddrChange", int.class, SocketAddress.class, int.class));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.sctp.Shutdown"));
        JNIRuntimeAccess.register(constructor(a, "sun.nio.ch.sctp.Shutdown", int.class));
    }

    private static void registerWindowsNativeDispatcherInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$FirstFile"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$FirstFile", "handle", "name", "attributes"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$FirstStream"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$FirstStream", "handle", "name"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$VolumeInformation"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$VolumeInformation", "fileSystemName", "volumeName", "volumeSerialNumber", "flags"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$DiskFreeSpace", "freeBytesAvailable", "totalNumberOfBytes", "totalNumberOfFreeBytes"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$Account"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$Account", "domain", "name", "use"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$AclInformation"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$AclInformation", "aceCount"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$CompletionStatus"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$CompletionStatus", "error", "bytesTransferred", "completionKey"));
        JNIRuntimeAccess.register(clazz(a, "sun.nio.fs.WindowsNativeDispatcher$BackupResult"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.fs.WindowsNativeDispatcher$BackupResult", "bytesTransferred", "context"));
    }

    private static void registerIocpInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(clazz(a, "sun.nio.ch.Iocp$CompletionStatus"));
        JNIRuntimeAccess.register(fields(a, "sun.nio.ch.Iocp$CompletionStatus", "error", "bytesTransferred", "completionKey", "overlapped"));
    }

    private static void registerConnectionCreateInetSocketAddress(DuringAnalysisAccess a) {
        RuntimeReflection.register(clazz(a, "java.net.InetSocketAddress"));
        RuntimeReflection.register(constructor(a, "java.net.InetSocketAddress", InetAddress.class, int.class));
    }
}
