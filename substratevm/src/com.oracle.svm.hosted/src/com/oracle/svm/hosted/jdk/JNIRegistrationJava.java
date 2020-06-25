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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.jdk.JNIRegistrationUtil;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;

/**
 * Registration of classes, methods, and fields accessed via JNI by C code of the JDK.
 */
@Platforms({InternalPlatform.PLATFORM_JNI.class})
@AutomaticFeature
class JNIRegistrationJava extends JNIRegistrationUtil implements GraalFeature {

    private static final Consumer<DuringAnalysisAccess> CORESERVICES_LINKER = (duringAnalysisAccess -> {
        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) duringAnalysisAccess;
        accessImpl.getNativeLibraries().addDynamicNonJniLibrary("-framework CoreServices");
    });

    @Override
    public void registerGraphBuilderPlugins(Providers providers, Plugins plugins, boolean analysis, boolean hosted) {
        JNIRegistrationSupport.singleton().registerNativeLibrary(providers, plugins, System.class, "loadLibrary");
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        ImageSingletons.add(JNIRegistrationSupport.class, new JNIRegistrationSupport());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        NativeLibraries nativeLibraries = ((BeforeAnalysisAccessImpl) a).getNativeLibraries();
        JNIRegistrationSupport.singleton().setNativeLibraries(nativeLibraries);

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
                        "java.util.zip.DataFormatException");
        JNIRuntimeAccess.register(constructor(a, "java.io.FileNotFoundException", String.class, String.class));

        /* Unconditional Integer and Boolean JNI registration (cheap) */
        JNIRuntimeAccess.register(clazz(a, "java.lang.Integer"));
        JNIRuntimeAccess.register(constructor(a, "java.lang.Integer", int.class));
        JNIRuntimeAccess.register(fields(a, "java.lang.Integer", "value"));
        JNIRuntimeAccess.register(clazz(a, "java.lang.Boolean"));
        JNIRuntimeAccess.register(constructor(a, "java.lang.Boolean", boolean.class));
        JNIRuntimeAccess.register(fields(a, "java.lang.Boolean", "value"));
        JNIRuntimeAccess.register(method(a, "java.lang.Boolean", "getBoolean", String.class));

        /*
         * Core JDK elements accessed from many places all around the JDK. They can be registered
         * unconditionally.
         */

        JNIRuntimeAccess.register(java.io.FileDescriptor.class);
        JNIRuntimeAccess.register(fields(a, "java.io.FileDescriptor", "fd"));
        if (isWindows()) {
            JNIRuntimeAccess.register(fields(a, "java.io.FileDescriptor", "handle"));
        }
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            JNIRuntimeAccess.register(fields(a, "java.io.FileDescriptor", "append"));
        }

        /* Used by FileOutputStream.initIDs, which is called unconditionally during startup. */
        JNIRuntimeAccess.register(fields(a, "java.io.FileOutputStream", "fd"));
        /* Used by FileInputStream.initIDs, which is called unconditionally during startup. */
        JNIRuntimeAccess.register(fields(a, "java.io.FileInputStream", "fd"));
        /* Used by UnixFileSystem/WinNTFileSystem.initIDs, called unconditionally during startup. */
        JNIRuntimeAccess.register(java.io.File.class);
        JNIRuntimeAccess.register(fields(a, "java.io.File", "path"));

        // TODO classify the remaining registrations

        JNIRuntimeAccess.register(byte[].class); /* used by ProcessEnvironment.environ() */

        JNIRuntimeAccess.register(java.lang.String.class);
        JNIRuntimeAccess.register(java.lang.System.class);
        JNIRuntimeAccess.register(method(a, "java.lang.System", "getProperty", String.class));
        JNIRuntimeAccess.register(java.nio.charset.Charset.class);
        JNIRuntimeAccess.register(method(a, "java.nio.charset.Charset", "isSupported", String.class));
        JNIRuntimeAccess.register(constructor(a, "java.lang.String", byte[].class, String.class));
        JNIRuntimeAccess.register(method(a, "java.lang.String", "getBytes", String.class));
        JNIRuntimeAccess.register(method(a, "java.lang.String", "concat", String.class));
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            JNIRuntimeAccess.register(fields(a, "java.lang.String", "coder", "value"));
        }

        a.registerReachabilityHandler(JNIRegistrationJava::registerRandomAccessFileInitIDs, method(a, "java.io.RandomAccessFile", "initIDs"));
        if (isWindows()) {
            /* Resolve calls to sun_security_provider_NativeSeedGenerator* as built-in. */
            PlatformNativeLibrarySupport.singleton().addBuiltinPkgNativePrefix("sun_security_provider_NativeSeedGenerator");
        }
        if (isDarwin()) {
            List<Method> darwinMethods = Arrays.asList(
                            method(a, "apple.security.KeychainStore", "_scanKeychain"),
                            method(a, "apple.security.KeychainStore", "_releaseKeychainItemRef", long.class),
                            method(a, "apple.security.KeychainStore", "_addItemToKeychain", String.class, boolean.class, byte[].class, char[].class),
                            method(a, "apple.security.KeychainStore", "_removeItemFromKeychain", long.class),
                            method(a, "apple.security.KeychainStore", "_getEncodedKeyData", long.class, char[].class));
            if (JavaVersionUtil.JAVA_SPEC >= 11) {
                /*
                 * JNI method implementations depending on CoreService are present in the following
                 * jdk classes sun.nio.fs.MacOXFileSystemProvider (9+),
                 * sun.net.spi.DefaultProxySelector (9+)
                 */
                ArrayList<Method> methods = new ArrayList<>(darwinMethods);
                methods.addAll(Arrays.asList(method(a, "sun.nio.fs.MacOSXFileSystemProvider", "getFileTypeDetector"),
                                method(a, "sun.net.spi.DefaultProxySelector", "getSystemProxies", String.class, String.class),
                                method(a, "sun.net.spi.DefaultProxySelector", "init")));

                a.registerReachabilityHandler(CORESERVICES_LINKER, methods.toArray(new Object[]{}));
            } else {
                a.registerReachabilityHandler(CORESERVICES_LINKER, darwinMethods.toArray(new Object[]{}));
            }
        }

        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            a.registerReachabilityHandler(JNIRegistrationJava::registerProcessHandleImplInfoInitIDs, method(a, "java.lang.ProcessHandleImpl$Info", "initIDs"));
        }
    }

    private static void registerProcessHandleImplInfoInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.lang.ProcessHandleImpl$Info", "command", "commandLine", "arguments", "startTime", "totalTime", "user"));
    }

    private static void registerRandomAccessFileInitIDs(DuringAnalysisAccess a) {
        JNIRuntimeAccess.register(fields(a, "java.io.RandomAccessFile", "fd"));
    }
}
