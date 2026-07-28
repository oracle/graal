/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.shared.util.VMError;

public abstract class PlatformNativeLibrarySupport {

    /// Names (without platform-specific prefixes or suffixes) of the default built-in libraries.
    public static final String[] defaultBuiltinLibraries = {
                    "java",
                    "nio",
                    "net"
    };

    public static final String[] potentialBuiltinLibraries = {
                    "java",
                    "nio",
                    "net",
                    "extnet",
                    "jaas",
                    "sunmscapi",
                    "zip",
                    "management_agent",
                    "attach",
                    "management_ext",
                    "prefs"
    };

    public static PlatformNativeLibrarySupport singleton() {
        return ImageSingletons.lookup(PlatformNativeLibrarySupport.class);
    }

    protected PlatformNativeLibrarySupport() {
        builtinNatives = new LinkedHashSet<>();
    }

    /**
     * Determines if a library which has <em>not</em> been
     * {@linkplain NativeLibrarySupport#addBuiltinLibrary pre-registered}
     * during image generation is a built-in library.
     */
    public boolean isBuiltinLibrary(@SuppressWarnings("unused") String name) {
        return false;
    }

    /// Stores JNI-mangled symbols that are associated with built-in native libraries.
    private final Set<String> builtinNatives;

    private boolean builtinNativesSealed;

    /**
     * Registers built-in JNI symbols that will be statically linked into the image.
     * @see #isBuiltinNative
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void addBuiltinNatives(Collection<String> jniSymbols) {
        if (builtinNativesSealed) {
            throw VMError.shouldNotReachHere("Cannot register any more native built-ins because information has already been used.");
        }
        builtinNatives.addAll(jniSymbols);
    }

    /// Determines whether `jniSymbol` corresponds to a built-in native method.
    /// The method checks for an exact match with a symbol previously registered with [#addBuiltinNatives].
    ///
    /// @param jniSymbol the JNI symbol name to evaluate.
    /// @return `true` if the symbol corresponds to a built-in native method; `false` otherwise.
    public boolean isBuiltinNative(String jniSymbol) {
        builtinNativesSealed = true;
        return builtinNatives.contains(jniSymbol);
    }

    public interface NativeLibrary {

        String getCanonicalIdentifier();

        boolean isBuiltin();

        boolean load();

        boolean unload();

        boolean isLoaded();

        PointerBase findSymbol(String name);
    }

    public abstract NativeLibrary createLibrary(String canonical, boolean builtIn);

    public abstract PointerBase findBuiltinSymbol(String name);

    /**
     * Initializes built-in libraries during isolate creation.
     *
     * @see Isolates#isCurrentFirst()
     */
    public abstract boolean initializeBuiltinLibraries();
}
