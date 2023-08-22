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

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.util.function.BiFunction;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;

/**
 * Interface allowing looking up symbols from libraries. Currently, only
 * {@link java.lang.foreign.SymbolLookup#loaderLookup} is supported. Support for the other lookups
 * ({@link java.lang.foreign.SymbolLookup#libraryLookup} and {@link Linker#defaultLookup()}) will
 * come later.
 * <p>
 * Note that {@link java.lang.foreign.SymbolLookup#loaderLookup} will have a behavior which is
 * slightly different from the one in the JDK: like loadLibrary, the lookup is classloader agnostic,
 * which means that loading a library in a classloader and then looking it up from another one will
 * succeed. See
 * {@link com.oracle.svm.core.jdk.Target_java_lang_ClassLoader#loadLibrary(java.lang.Class, java.lang.String)}
 */
@TargetClass(className = "java.lang.foreign.SymbolLookup")
public final class Target_java_lang_foreign_SymbolLookup {

    @Substitute
    private static <Z> SymbolLookup libraryLookup(Z libDesc, BiFunction<RawNativeLibraries, Z, NativeLibrary> loadLibraryFunc, Arena libArena) {
        throw unsupportedFeature("Library symbol lookups are not yet supported.");
    }
}
