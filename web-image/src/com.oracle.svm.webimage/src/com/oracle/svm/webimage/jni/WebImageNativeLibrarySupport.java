/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.webimage.jni;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.graalvm.collections.EconomicMap;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSUndefined;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

public class WebImageNativeLibrarySupport extends PlatformNativeLibrarySupport {
    @Override
    public NativeLibrary createLibrary(String canonical, boolean builtIn) {
        return new JSNativeLibrary(canonical, builtIn);
    }

    @Override
    public PointerBase findBuiltinSymbol(String name) {
        return Word.nullPointer();
    }

    @Override
    public boolean initializeBuiltinLibraries() {
        return false;
    }

    @JS("return loadPrefetchedJSLibrary(content);")
    private static native JSObject loadPrefetchedJSLibrary(JSString content);

    @JS.Coerce
    @JS("return runtime.addToFuntab(f)")
    private static native int addFunctionToFuntab(JSObject f);

    class JSNativeLibrary implements NativeLibrary {
        private final String canonicalIdentifier;
        private final boolean builtin;
        private final EconomicMap<String, Integer> symbolAddresses;
        private JSObject symbolTable;

        JSNativeLibrary(String canonicalIdentifier, boolean builtin) {
            this.canonicalIdentifier = canonicalIdentifier;
            this.builtin = builtin;
            this.symbolTable = null;
            this.symbolAddresses = EconomicMap.create();
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
        public boolean isLoaded() {
            return symbolTable != null;
        }

        @Override
        public boolean load() {
            // The VM should never call load if load previously returned true.
            assert symbolTable == null : "Symbol table for library already loaded: " + symbolTable;
            // We do not have any builtin libraries in Web Image yet.
            assert !builtin;
            try {
                byte[] bytes = Files.readAllBytes(new File(canonicalIdentifier).toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                symbolTable = loadPrefetchedJSLibrary(JSString.of(content));
            } catch (Throwable ignored) {
                // Any failure during loading means that the library cannot be loaded,
                // and the native-library support with try a different path.
                return false;
            }
            return symbolTable != null;
        }

        @Override
        public boolean unload() {
            throw VMError.unimplemented("Unloading native libraries is not implemented on Web Image");
        }

        @Override
        public PointerBase findSymbol(String name) {
            Integer address = symbolAddresses.get(name);
            if (address == null) {
                Object entry = symbolTable.get(name);
                if (entry == null || entry == JSUndefined.undefined()) {
                    return Word.nullPointer();
                }
                address = addFunctionToFuntab((JSObject) entry);
                symbolAddresses.put(name, address);
            }
            return Word.pointer((long) address);
        }
    }
}
