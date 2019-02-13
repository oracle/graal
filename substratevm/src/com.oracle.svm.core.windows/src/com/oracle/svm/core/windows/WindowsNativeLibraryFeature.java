/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.windows.headers.Jvm;
import com.oracle.svm.core.windows.headers.WinBase;

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
class WindowsNativeLibraryFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        WindowsNativeLibrarySupport.initialize();
    }
}

class WindowsNativeLibrarySupport implements PlatformNativeLibrarySupport {
    static final String[] builtInPkgNatives = {
                    "Java_com_sun_demo_jvmti_hprof",
                    "Java_com_sun_java_util_jar_pack",
                    "Java_com_sun_net_ssl",
                    "Java_com_sun_nio_file",
                    "Java_com_sun_security_cert_internal_x509",
                    "Java_java_io",
                    "Java_java_lang",
                    "Java_java_math",
                    "Java_java_net",
                    "Java_java_nio",
                    "Java_java_security",
                    "Java_java_text",
                    "Java_java_time",
                    "Java_java_util",
                    "Java_javax_net",
                    "Java_javax_script",
                    "Java_javax_security",
                    "Java_jdk_internal_org",
                    "Java_jdk_internal_util",
                    "Java_jdk_net",
                    "Java_sun_invoke",
                    "Java_sun_launcher",
                    "Java_sun_net",
                    "Java_sun_nio",
                    "Java_sun_reflect",
                    "Java_sun_security",
                    "Java_sun_text",
                    "Java_sun_util",

                    /* SVM Specific packages */
                    "Java_com_oracle_svm_core_jdk"
    };

    static void initialize() {
        ImageSingletons.add(PlatformNativeLibrarySupport.class, new WindowsNativeLibrarySupport());
    }

    @Override
    public boolean initializeBuiltinLibraries() {
        if (!WindowsJavaNetSubstitutions.initIDs()) {
            return false;
        }
        if (!WindowsJavaIOSubstitutions.initIDs()) {
            return false;
        }
        if (!WindowsJavaNIOSubstitutions.initIDs()) {
            return false;
        }
        return true;
    }

    @Override
    public WindowsNativeLibrary createLibrary(String canonical, boolean builtIn) {
        return new WindowsNativeLibrary(canonical, builtIn);
    }

    @Override
    public PointerBase findBuiltinSymbol(String name) {
        try (CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
            Pointer builtinHandle = WinBase.GetModuleHandleA(WordFactory.nullPointer());
            return WinBase.GetProcAddress(builtinHandle, symbol.get());
        }
    }

    @Override
    public boolean isBuiltinPkgNative(String name) {
        // Do a quick check first
        if (name.startsWith("Java_")) {
            for (String str : builtInPkgNatives) {
                if (name.startsWith(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    class WindowsNativeLibrary implements NativeLibrary {

        private final String canonicalIdentifier;
        private final boolean builtin;
        private Pointer dlhandle = WordFactory.nullPointer();

        WindowsNativeLibrary(String canonicalIdentifier, boolean builtin) {
            // Make sure the jvm.lib is available for linking
            // Need a better place to put this.
            Jvm.initialize();

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
        public void load() {
            if (!builtin) {
                assert dlhandle.isNull();
                String dllPath = canonicalIdentifier;
                CCharPointerHolder dllpathPin = CTypeConversion.toCString(dllPath);
                CCharPointer dllPathPtr = dllpathPin.get();
                /*
                 * WinBase.SetDllDirectoryA(dllpathPtr); CCharPointerHolder pathPin =
                 * CTypeConversion.toCString(path); CCharPointer pathPtr = pathPin.get();
                 */
                dlhandle = WinBase.LoadLibraryA(dllPathPtr);
                if (this.dlhandle.isNull()) {
                    throw new UnsatisfiedLinkError(dllPath + ": " + WinBase.GetLastError());
                }
            }
        }

        @Override
        public PointerBase findSymbol(String name) {
            if (builtin) {
                PointerBase addr = findBuiltinSymbol(name);
                return (addr);
            }
            assert dlhandle.isNonNull();
            try (CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
                PointerBase addr = WinBase.GetProcAddress(dlhandle, symbol.get());
                return (addr);
            }
        }
    }
}
