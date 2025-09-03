/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.DARWIN;
import org.graalvm.nativeimage.Platform.WINDOWS;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.BasedOnJDKClass;

import jdk.graal.compiler.word.Word;
import jdk.internal.foreign.Utils;

/**
 * Separated from {@link Target_jdk_internal_foreign_SystemLookup} to allow (forced) runtime
 * initialization.
 */
@BasedOnJDKClass(jdk.internal.foreign.SystemLookup.class)
public final class RuntimeSystemLookup {
    static final SymbolLookup INSTANCE = makeSystemLookup();

    public static SymbolLookup makeSystemLookup() {
        if (Platform.includedIn(WINDOWS.class)) {
            /*
             * Windows support has some subtleties: one would ideally load ucrtbase.dll, but some
             * old installs might not have it, in which case msvcrt.dll should be loaded instead. If
             * ucrt is used, then some symbols (the printf family) are inline, which means that the
             * symbols cannot be looked up. HotSpot's solution is to create a dummy library which
             * packs all these methods in an array, and retrieves the function's address from there,
             * which thus requires an external library (and requires synchronization between the
             * external library and the java code).
             */
            Path system32 = Path.of(System.getenv("SystemRoot"), "System32");
            Path ucrtbase = system32.resolve("ucrtbase.dll");
            Path msvcrt = system32.resolve("msvcrt.dll");
            boolean useUCRT = Files.exists(ucrtbase);
            Path stdLib = useUCRT ? ucrtbase : msvcrt;

            SymbolLookup lookup = Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, List.of(stdLib));
            if (useUCRT) {
                // use a fallback lookup to look up inline functions
                Function<String, Optional<MemorySegment>> fallbackLookup = name -> {
                    Pointer ptr = getWindowsFallbackSymbol(name);
                    if (ptr.isNonNull()) {
                        return Optional.of(MemorySegment.ofAddress(ptr.rawValue()));
                    }
                    return Optional.empty();
                };
                final SymbolLookup finalLookup = lookup;
                lookup = name -> {
                    Objects.requireNonNull(name);
                    if (Utils.containsNullChars(name)) {
                        return Optional.empty();
                    }
                    return finalLookup.find(name).or(() -> fallbackLookup.apply(name));
                };
            }

            return lookup;
        } else if (Platform.includedIn(DARWIN.class)) {
            return Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, List.of("/usr/lib/libSystem.B.dylib"));
        } else {
            /*
             * This list of libraries was obtained by examining the dependencies of libsystemlookup,
             * which is a native library included with the JDK.
             */
            return Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, List.of("libc.so.6", "libm.so.6", "libdl.so.2"));
        }
    }

    /**
     * Returns the function pointer of the requested symbol specified with
     * {@link Target_jdk_internal_foreign_SystemLookup_WindowsFallbackSymbols}. See also file
     * 'com.oracle.svm.native.libchelper/src/syslookup.c'.
     */
    @Platforms(WINDOWS.class)
    @CLibrary(value = "libchelper", requireStatic = true)
    @CFunction(value = "__svm_get_syslookup_func", transition = Transition.NO_TRANSITION)
    public static native Pointer getSyslookupFunc(int i, int nExpected);

    @Platforms(WINDOWS.class)
    private static Pointer getWindowsFallbackSymbol(String name) {
        try {
            assert Target_jdk_internal_foreign_SystemLookup_WindowsFallbackSymbols.class.isEnum();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum value = Enum.valueOf(SubstrateUtil.cast(Target_jdk_internal_foreign_SystemLookup_WindowsFallbackSymbols.class, Class.class), name);
            Pointer func = getSyslookupFunc(value.ordinal(), Target_jdk_internal_foreign_SystemLookup_WindowsFallbackSymbols.class.getEnumConstants().length);
            assert func.isNonNull() : "Function pointer for " + value.name() + " is NULL but shouldn't.";
            return func;
        } catch (IllegalArgumentException e) {
            return Word.nullPointer();
        }
    }
}
