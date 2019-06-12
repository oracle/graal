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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.JDKLibZipSubstitutions;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.windows.headers.WinBase;
import com.oracle.svm.core.windows.headers.WinBase.HMODULE;

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
class WindowsNativeLibraryFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        WindowsNativeLibrarySupport.initialize();
    }
}

class WindowsNativeLibrarySupport implements PlatformNativeLibrarySupport {

    static void initialize() {
        ImageSingletons.add(PlatformNativeLibrarySupport.class, new WindowsNativeLibrarySupport());
    }

    @Override
    public boolean initializeBuiltinLibraries() {
        if (!WindowsJavaNetSubstitutions.initIDs()) {
            return false;
        }
        /*
         * java.dll is normally loaded by the VM. After loading java.dll, the VM then calls
         * initializeSystemClasses which loads zip.dll.
         *
         * We might want to consider calling System.initializeSystemClasses instead of explicitly
         * loading the builtin zip library.
         */
        if (!WindowsJavaIOSubstitutions.initIDs()) {
            return false;
        }
        if (!JDKLibZipSubstitutions.initIDs()) {
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
            HMODULE builtinHandle = WinBase.GetModuleHandleA(WordFactory.nullPointer());
            return WinBase.GetProcAddress(builtinHandle, symbol.get());
        }
    }

    class WindowsNativeLibrary implements NativeLibrary {

        private final String canonicalIdentifier;
        private final boolean builtin;
        private HMODULE dlhandle = WordFactory.nullPointer();

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
        public boolean load() {
            if (builtin) {
                return true;
            }
            assert dlhandle.isNull();
            try (CCharPointerHolder dllPathPin = CTypeConversion.toCString(canonicalIdentifier)) {
                CCharPointer dllPathPtr = dllPathPin.get();
                /*
                 * WinBase.SetDllDirectoryA(dllpathPtr); CCharPointerHolder pathPin =
                 * CTypeConversion.toCString(path); CCharPointer pathPtr = pathPin.get();
                 */
                dlhandle = WinBase.LoadLibraryA(dllPathPtr);
            }
            return dlhandle.isNonNull();
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
