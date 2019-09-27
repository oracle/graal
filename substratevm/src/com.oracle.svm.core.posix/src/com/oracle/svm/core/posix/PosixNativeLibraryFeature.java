/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import com.oracle.svm.core.jdk.NativeLibrarySupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JNIPlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.Dlfcn;

@AutomaticFeature
@Platforms({InternalPlatform.LINUX_AND_JNI.class, InternalPlatform.DARWIN_AND_JNI.class})
class PosixNativeLibraryFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        PosixNativeLibrarySupport.initialize();
    }
}

class PosixNativeLibrarySupport extends JNIPlatformNativeLibrarySupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    PosixNativeLibrarySupport() {
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            addBuiltInLibrary("extnet");
        }
    }

    static void initialize() {
        ImageSingletons.add(PlatformNativeLibrarySupport.class, new PosixNativeLibrarySupport());
    }

    @Override
    public boolean initializeBuiltinLibraries() {
        if (Platform.includedIn(InternalPlatform.PLATFORM_JNI.class)) {
            try {
                loadJavaLibrary();
                loadZipLibrary();
                loadNetLibrary();

                /*
                 * The JDK uses posix_spawn on the Mac to launch executables. This requires a
                 * separate process "jspawnhelper" which we don't want to have to rely on. Force the
                 * use of FORK on Linux and Mac.
                 */
                System.setProperty("jdk.lang.Process.launchMechanism", "FORK");

            } catch (UnsatisfiedLinkError e) {
                Log.log().string("System.loadLibrary failed, " + e).newline();
                return false;
            }
        }
        return true;
    }

    @Override
    protected void loadJavaLibrary() {
        super.loadJavaLibrary();
        Target_java_io_UnixFileSystem_JNI.initIDs();
    }

    protected void loadNetLibrary() {
        if (isFirstIsolate()) {
            /*
             * NOTE: because the native OnLoad code probes java.net.preferIPv4Stack and stores its
             * value in process-wide shared native state, the property's value in the first launched
             * isolate applies to all subsequently launched isolates.
             */
            System.loadLibrary("net");
        } else {
            NativeLibrarySupport.singleton().registerInitializedBuiltinLibrary("net");
        }
    }

    @Override
    public PosixNativeLibrary createLibrary(String canonical, boolean builtIn) {
        return new PosixNativeLibrary(canonical, builtIn);
    }

    @Override
    public PointerBase findBuiltinSymbol(String name) {
        try (CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
            return Dlfcn.dlsym(Dlfcn.RTLD_DEFAULT(), symbol.get());
        }
    }

    class PosixNativeLibrary implements NativeLibrary {

        private final String canonicalIdentifier;
        private final boolean builtin;
        private PointerBase dlhandle = WordFactory.nullPointer();

        PosixNativeLibrary(String canonicalIdentifier, boolean builtin) {
            // Make sure the jvm.lib is available for linking
            // Need a better place to put this.
            if (Platform.includedIn(InternalPlatform.LINUX_JNI.class) ||
                            Platform.includedIn(InternalPlatform.DARWIN_JNI.class)) {
                Jvm.initialize();
            }

            this.canonicalIdentifier = canonicalIdentifier;
            this.builtin = builtin;
        }

        @Override
        public String getCanonicalIdentifier() {
            return canonicalIdentifier;
        }

        @Override
        public boolean isBuiltin() {
            return builtin;
        }

        @Override
        public boolean load() {
            if (builtin) {
                return true;
            }
            assert dlhandle.isNull();
            dlhandle = PosixUtils.dlopen(canonicalIdentifier, Dlfcn.RTLD_LAZY());
            return dlhandle.isNonNull();
        }

        @Override
        public PointerBase findSymbol(String name) {
            if (builtin) {
                return findBuiltinSymbol(name);
            }
            assert dlhandle.isNonNull();
            try (CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
                return Dlfcn.dlsym(dlhandle, symbol.get());
            }
        }
    }
}

@Platforms({InternalPlatform.LINUX_JNI.class, InternalPlatform.DARWIN_JNI.class})
@TargetClass(className = "java.io.UnixFileSystem")
final class Target_java_io_UnixFileSystem_JNI {
    @Alias
    static native void initIDs();
}
