/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ffi.nfi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.substitutions.Collect;

/**
 * Same as nfi-native, but libjvm statically links in (most) OpenJDK libraries. This avoids
 * namespace clashes if the host VM (e.g. HotSpot) depends on dynamically loaded OpenJDK libraries.
 * This is useful for platforms where namespace isolation is not available.
 */
public class NFIStaticLibNativeAccess extends NFINativeAccess {
    private TruffleObject mokapotLibrary = null;

    NFIStaticLibNativeAccess(TruffleLanguage.Env env) {
        super(env);
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        assert mokapotLibrary != null;
        return mokapotLibrary;
    }

    private static final Set<String> LOADED_BY_ESPRESSO = Set.of(
                    "java",
                    "jimage",
                    "verify",
                    "zip");

    private static boolean loadedByEspresso(String libname) {
        if (OS.getCurrent() != OS.Darwin) {
            throw EspressoError.shouldNotReachHere("nfi-staticlib is not supported yet " + OS.getCurrent());
        }

        return LOADED_BY_ESPRESSO.contains(libname);
    }

    @Override
    public @Pointer TruffleObject loadLibrary(List<Path> originalSearchPaths, String shortName, boolean notFoundIsFatal) {
        List<Path> searchPaths = originalSearchPaths;

        if (loadedByEspresso(shortName)) {
            EspressoError.guarantee(mokapotLibrary != null, "expected libjvm to be loaded first, but attempted to load: lib" + shortName);
            return mokapotLibrary;
        }

        if ("jvm".equals(shortName)) {
            List<Path> patched = new ArrayList<>(searchPaths.size());
            for (Path searchPath : searchPaths) {
                patched.add(searchPath.resolve("fatpot"));
            }
            searchPaths = patched;
        }

        /*
         * There are more libraries statically linked into fatpot, but those are captured by the
         * logic in Java_jdk_internal_loader_NativeLibraries_findBuiltinLib.
         */

        TruffleObject lib = super.loadLibrary(searchPaths, shortName, notFoundIsFatal);

        if (mokapotLibrary == null && "jvm".equals(shortName)) {
            mokapotLibrary = lib;
        }

        return lib;
    }

    @Collect(NativeAccess.class)
    public static final class Provider implements NativeAccess.Provider {

        public static final String ID = "nfi-staticlib";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NFIStaticLibNativeAccess(env);
        }
    }
}
