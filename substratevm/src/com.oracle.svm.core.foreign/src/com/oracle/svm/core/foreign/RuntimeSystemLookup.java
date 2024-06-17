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

import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.util.BasedOnJDKClass;

/**
 * Separated from {@link Target_jdk_internal_foreign_SystemLookup} to allow (forced) runtime
 * initialization.
 */
@BasedOnJDKClass(jdk.internal.foreign.SystemLookup.class)
public final class RuntimeSystemLookup {
    static final SymbolLookup INSTANCE = makeSystemLookup();

    public static SymbolLookup makeSystemLookup() {
        if (OS.WINDOWS.isCurrent()) {
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
            return Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, List.of(stdLib));
        } else {
            /*
             * This list of libraries was obtained by examining the dependencies of libsystemlookup,
             * which is a native library included with the JDK.
             */
            return Util_java_lang_foreign_SymbolLookup.libraryLookup(LookupNativeLibraries::loadLibraryPlatformSpecific, List.of("libc.so.6", "libm.so.6", "libdl.so.2"));
        }
    }
}
