/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeJNIAccess;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms(InternalPlatform.PLATFORM_JNI.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
class JNIRegistrationJava extends JNIRegistrationUtil implements InternalFeature {

    private static final Consumer<DuringAnalysisAccess> CORESERVICES_LINKER = (duringAnalysisAccess -> {
        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) duringAnalysisAccess;
        accessImpl.getNativeLibraries().addDynamicNonJniLibrary("-framework CoreServices");
    });

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        /*
         * It is difficult to track down all the places where exceptions are thrown via JNI. And
         * unconditional registration is cheap. Therefore, we register them unconditionally.
         */
        registerForThrowNew(a, "java.lang.Exception", "java.lang.Error", "java.lang.OutOfMemoryError",
                        "java.lang.RuntimeException", "java.lang.NullPointerException", "java.lang.ArrayIndexOutOfBoundsException",
                        "java.lang.IllegalArgumentException", "java.lang.IllegalAccessException", "java.lang.IllegalAccessError", "java.lang.InternalError",
                        "java.lang.NoSuchFieldException", "java.lang.NoSuchMethodException", "java.lang.ClassNotFoundException", "java.lang.NumberFormatException",
                        "java.lang.NoSuchFieldError", "java.lang.NoSuchMethodError", "java.lang.UnsatisfiedLinkError", "java.lang.StringIndexOutOfBoundsException",
                        "java.lang.InstantiationException", "java.lang.UnsupportedOperationException",
                        "java.io.IOException", "java.io.FileNotFoundException", "java.io.SyncFailedException", "java.io.InterruptedIOException",
                        "java.util.zip.DataFormatException", "java.lang.IndexOutOfBoundsException");
        JVMCIRuntimeJNIAccess.register(constructor(a, "java.io.FileNotFoundException", String.class, String.class));

        /* Unconditional Integer and Boolean JNI registration (cheap) */
        JVMCIRuntimeJNIAccess.register(type(a, "java.lang.Integer"));
        JVMCIRuntimeJNIAccess.register(constructor(a, "java.lang.Integer", int.class));
        JVMCIRuntimeJNIAccess.register(fields(a, "java.lang.Integer", "value"));
        JVMCIRuntimeJNIAccess.register(type(a, "java.lang.Boolean"));
        JVMCIRuntimeJNIAccess.register(constructor(a, "java.lang.Boolean", boolean.class));
        JVMCIRuntimeJNIAccess.register(fields(a, "java.lang.Boolean", "value"));
        JVMCIRuntimeJNIAccess.register(method(a, "java.lang.Boolean", "getBoolean", String.class));

        /*
         * Core JDK elements accessed from many places all around the JDK. They can be registered
         * unconditionally.
         */

        RuntimeJNIAccess.register(java.io.FileDescriptor.class);
        JVMCIRuntimeJNIAccess.register(fields(a, "java.io.FileDescriptor", "fd"));
        if (isWindows()) {
            JVMCIRuntimeJNIAccess.register(fields(a, "java.io.FileDescriptor", "handle"));
        }
        JVMCIRuntimeJNIAccess.register(fields(a, "java.io.FileDescriptor", "append"));

        /* Used by FileOutputStream.initIDs, which is called unconditionally during startup. */
        JVMCIRuntimeJNIAccess.register(fields(a, "java.io.FileOutputStream", "fd"));
        /* Used by FileInputStream.initIDs, which is called unconditionally during startup. */
        JVMCIRuntimeJNIAccess.register(fields(a, "java.io.FileInputStream", "fd"));
        /* Used by UnixFileSystem/WinNTFileSystem.initIDs, called unconditionally during startup. */
        RuntimeJNIAccess.register(java.io.File.class);
        JVMCIRuntimeJNIAccess.register(fields(a, "java.io.File", "path"));

        // TODO classify the remaining registrations

        /* used by ProcessEnvironment.environ() */
        RuntimeJNIAccess.register(byte[].class);

        RuntimeJNIAccess.register(String.class);
        RuntimeJNIAccess.register(System.class);
        JVMCIRuntimeJNIAccess.register(method(a, "java.lang.System", "getProperty", String.class));
        RuntimeJNIAccess.register(java.nio.charset.Charset.class);
        JVMCIRuntimeJNIAccess.register(constructor(a, "java.lang.String", byte[].class));
        JVMCIRuntimeJNIAccess.register(method(a, "java.lang.String", "getBytes"));
        JVMCIRuntimeJNIAccess.register(method(a, "java.nio.charset.Charset", "forName", String.class));
        JVMCIRuntimeJNIAccess.register(constructor(a, "java.lang.String", byte[].class, java.nio.charset.Charset.class));
        JVMCIRuntimeJNIAccess.register(method(a, "java.lang.String", "getBytes", java.nio.charset.Charset.class));
        JVMCIRuntimeJNIAccess.register(method(a, "java.lang.String", "concat", String.class));
        JVMCIRuntimeJNIAccess.register(fields(a, "java.lang.String", "coder", "value"));

        a.registerReachabilityHandler(JNIRegistrationJava::registerRandomAccessFileInitIDs, method(a, "java.io.RandomAccessFile", "initIDs"));
        if (isWindows()) {
            /* Resolve calls to sun_security_provider_NativeSeedGenerator* as built-in. */
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_security_provider_NativeSeedGenerator");
        }
        if (isDarwin()) {
            List<ResolvedJavaMethod> darwinMethods = Arrays.asList(
                            method(a, "apple.security.KeychainStore", "_scanKeychain", String.class), // JDK-8320362
                            method(a, "apple.security.KeychainStore", "_releaseKeychainItemRef", long.class),
                            method(a, "apple.security.KeychainStore", "_addItemToKeychain", String.class, boolean.class, byte[].class, char[].class),
                            method(a, "apple.security.KeychainStore", "_removeItemFromKeychain", long.class),
                            method(a, "apple.security.KeychainStore", "_getEncodedKeyData", long.class, char[].class));
            /*
             * JNI method implementations depending on CoreService are present in the following jdk
             * classes sun.nio.fs.MacOXFileSystemProvider (9+), sun.net.spi.DefaultProxySelector
             * (9+)
             */
            ArrayList<ResolvedJavaMethod> methods = new ArrayList<>(darwinMethods);
            methods.addAll(Arrays.asList(method(a, "sun.nio.fs.MacOSXFileSystemProvider", "getFileTypeDetector"),
                            method(a, "sun.net.spi.DefaultProxySelector", "getSystemProxies", String.class, String.class),
                            method(a, "sun.net.spi.DefaultProxySelector", "init")));

            a.registerReachabilityHandler(CORESERVICES_LINKER, methods.toArray(new Object[]{}));
        }

        a.registerReachabilityHandler(JNIRegistrationJava::registerProcessHandleImplInfoInitIDs, method(a, "java.lang.ProcessHandleImpl$Info", "initIDs"));
    }

    private static void registerProcessHandleImplInfoInitIDs(DuringAnalysisAccess a) {
        JVMCIRuntimeJNIAccess.register(fields(a, "java.lang.ProcessHandleImpl$Info", "command", "commandLine", "arguments", "startTime", "totalTime", "user"));
    }

    private static void registerRandomAccessFileInitIDs(DuringAnalysisAccess a) {
        JVMCIRuntimeJNIAccess.register(fields(a, "java.io.RandomAccessFile", "fd"));
    }
}
