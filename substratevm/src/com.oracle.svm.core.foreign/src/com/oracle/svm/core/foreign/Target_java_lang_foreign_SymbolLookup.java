/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.graalvm.word.PointerBase;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.NativeLibraries;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jdk.Target_java_lang_Module;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * Interface allowing looking up symbols from libraries.
 *
 * Note that {@link java.lang.foreign.SymbolLookup#loaderLookup} will have a behavior which is
 * slightly different from the one in the JDK: like loadLibrary, the lookup is classloader agnostic,
 * which means that loading a library in a classloader and then looking it up from another one will
 * succeed. See
 * {@link com.oracle.svm.core.jdk.Target_java_lang_ClassLoader#loadLibrary(java.lang.Class, java.lang.String)}
 */
@TargetClass(className = "java.lang.foreign.SymbolLookup", onlyWith = ForeignFunctionsEnabled.class)
public final class Target_java_lang_foreign_SymbolLookup {

    @Substitute
    @CallerSensitive
    @NeverInline("Starting a stack walk in the caller frame")
    static SymbolLookup libraryLookup(String name, Arena arena) {
        Util_java_lang_foreign_SymbolLookup.ensureNativeAccess(StackTraceUtils.getCallerClass(KnownIntrinsics.readCallerStackPointer(), true), SymbolLookup.class, "libraryLookup");
        if (Utils.containsNullChars(name)) {
            throw new IllegalArgumentException("Cannot open library: " + name);
        }
        return Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, arena, List.of(name));
    }

    @Substitute
    @CallerSensitive
    @NeverInline("Starting a stack walk in the caller frame")
    static SymbolLookup libraryLookup(Path path, Arena arena) {
        Util_java_lang_foreign_SymbolLookup.ensureNativeAccess(StackTraceUtils.getCallerClass(KnownIntrinsics.readCallerStackPointer(), true), SymbolLookup.class, "libraryLookup");
        if (path.getFileSystem() != FileSystems.getDefault()) {
            throw new IllegalArgumentException("Path not in default file system: " + path);
        }
        return Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, arena, List.of(path));
    }

    @Delete
    private static native <Z> SymbolLookup libraryLookup(Z libDesc, BiFunction<RawNativeLibraries, Z, NativeLibrary> loadLibraryFunc, Arena libArena);
}

final class LookupNativeLibraries extends NativeLibraries {
    private final List<PlatformNativeLibrarySupport.NativeLibrary> knownLibraries = new ArrayList<>();

    @Override
    protected boolean addLibrary(String canonical, boolean builtin) {
        PlatformNativeLibrarySupport.NativeLibrary lib = PlatformNativeLibrarySupport.singleton().createLibrary(canonical, builtin);
        if (!lib.load()) {
            return false;
        }
        knownLibraries.add(lib);
        return true;
    }

    @Override
    public PointerBase findSymbol(String name) {
        return findSymbol(knownLibraries, name);
    }

    public void unloadAllLibraries() {
        for (PlatformNativeLibrarySupport.NativeLibrary known : knownLibraries) {
            if (known.isLoaded()) {
                if (!known.unload()) {
                    throw new IllegalStateException("Could not unload library: " + known.getCanonicalIdentifier());
                }
            }
        }
        knownLibraries.clear();
    }
}

final class Util_java_lang_foreign_SymbolLookup {
    /**
     * Calling {@link Reflection#ensureNativeAccess} directly results in an assertion error (due
     * to @ForceInline?), so we reimplement the method here.
     */
    @AlwaysInline("As in the JDK")
    static void ensureNativeAccess(Class<?> currentClass, Class<?> owner, String methodName) {
        /*
         * if there is no caller class, act as if the call came from unnamed module of system class
         * loader
         */
        Target_java_lang_Module module = SubstrateUtil.cast(currentClass != null ? currentClass.getModule() : ClassLoader.getSystemClassLoader().getUnnamedModule(),
                        Target_java_lang_Module.class);
        if (JavaVersionUtil.JAVA_SPEC <= 21) {
            module.ensureNativeAccess(owner, methodName);
        } else {
            module.ensureNativeAccess(owner, methodName, currentClass);
        }

    }

    static <Z> LookupNativeLibraries createNativeLibraries(BiConsumer<LookupNativeLibraries, Z> loadLibraryFunc, List<Z> libDescs) {
        // "Holds" the loaded libraries
        LookupNativeLibraries nativeLibraries = new LookupNativeLibraries();
        for (var libDesc : libDescs) {
            Objects.requireNonNull(libDesc);
            try {
                loadLibraryFunc.accept(nativeLibraries, libDesc);
            } catch (UnsatisfiedLinkError e) {
                // Throw the same exception type as the JDK
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        return nativeLibraries;
    }

    static SymbolLookup createLookup(LookupNativeLibraries nativeLibraries) {
        return new SymbolLookup() {
            @Override
            public Optional<MemorySegment> find(String name) {
                Objects.requireNonNull(name);
                if (Utils.containsNullChars(name)) {
                    return Optional.empty();
                }
                PointerBase addr = nativeLibraries.findSymbol(name);
                return addr.isNull() ? Optional.empty() : Optional.of(MemorySegment.ofAddress(addr.rawValue()));
            }
        };
    }

    static <Z> SymbolLookup libraryLookup(BiConsumer<LookupNativeLibraries, Z> loadLibraryFunc, List<Z> libDescs) {
        Objects.requireNonNull(libDescs);
        LookupNativeLibraries nativeLibraries = createNativeLibraries(loadLibraryFunc, libDescs);
        return createLookup(nativeLibraries);
    }

    @SuppressWarnings("restricted")
    static <Z> SymbolLookup libraryLookup(BiConsumer<LookupNativeLibraries, Z> loadLibraryFunc, Arena libArena, List<Z> libDescs) {
        Objects.requireNonNull(libDescs);
        Objects.requireNonNull(libArena);

        LookupNativeLibraries nativeLibraries = createNativeLibraries(loadLibraryFunc, libDescs);
        SymbolLookup baseLookup = createLookup(nativeLibraries);

        MemorySessionImpl.toMemorySession(libArena).addOrCleanupIfFail(new MemorySessionImpl.ResourceList.ResourceCleanup() {
            @Override
            public void cleanup() {
                nativeLibraries.unloadAllLibraries();
            }
        });

        return new SymbolLookup() {
            @Override
            public Optional<MemorySegment> find(String name) {
                return baseLookup.find(name).map(seg -> seg.reinterpret(libArena, null));
            }
        };
    }
}
