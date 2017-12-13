/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.File;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.posix.headers.Dlfcn;

@AutomaticFeature
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
    public PosixNativeLibrary create(File canonicalPath) {
        return new PosixNativeLibrary(canonicalPath);
    }

    class PosixNativeLibrary implements NativeLibrary {

        private final File canonicalPath;
        private PointerBase dlhandle = WordFactory.nullPointer();

        PosixNativeLibrary(File canonicalPath) {
            this.canonicalPath = canonicalPath;
        }

        @Override
        public File getCanonicalPath() {
            return canonicalPath;
        }

        @Override
        public boolean isLoaded() {
            return dlhandle.isNonNull();
        }

        @Override
        public void load() {
            dlhandle = PosixUtils.dlopen(canonicalPath.toString(), Dlfcn.RTLD_LAZY());
            if (this.dlhandle.isNull()) {
                String error = CTypeConversion.toJavaString(Dlfcn.dlerror());
                throw new UnsatisfiedLinkError(canonicalPath.toString() + ": " + error);
            }
        }

        @Override
        public PointerBase findSymbol(String name) {
            assert dlhandle.isNonNull();
            try (CCharPointerHolder symbol = CTypeConversion.toCString(name)) {
                return Dlfcn.dlsym(dlhandle, symbol.get());
            }
        }
    }
}
