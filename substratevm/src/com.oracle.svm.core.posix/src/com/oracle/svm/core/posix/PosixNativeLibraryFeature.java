/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.posix.headers.Dlfcn;

@AutomaticFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class PosixNativeLibraryFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        PosixNativeLibrarySupport.initialize();
    }
}

class PosixNativeLibrarySupport implements PlatformNativeLibrarySupport {

    static void initialize() {
        ImageSingletons.add(PlatformNativeLibrarySupport.class, new PosixNativeLibrarySupport());
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
                String path = canonicalIdentifier;
                dlhandle = PosixUtils.dlopen(path, Dlfcn.RTLD_LAZY());
                if (this.dlhandle.isNull()) {
                    String error = CTypeConversion.toJavaString(Dlfcn.dlerror());
                    throw new UnsatisfiedLinkError(path + ": " + error);
                }
            }
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
