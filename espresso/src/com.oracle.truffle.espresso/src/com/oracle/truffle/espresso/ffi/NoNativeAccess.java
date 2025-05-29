/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ffi;

import java.nio.file.Path;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.substitutions.Collect;

public class NoNativeAccess implements NativeAccess {
    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, NoNativeAccess.class);

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        getLogger().fine(() -> "Attempting to load library when no native access is allowed: " + libraryPath);
        return null;
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        getLogger().fine(() -> "Attempting to load default library when no native access is allowed");
        return null;
    }

    @Override
    public void unloadLibrary(@Pointer TruffleObject library) {
        getLogger().fine(() -> "Attempting to unload library when no native access is allowed");
    }

    @Override
    public @Pointer TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName) {
        getLogger().fine(() -> "Symbol lookup when no native access is allowed: " + symbolName);
        return null;
    }

    @Override
    public @Pointer TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeSignature nativeSignature) {
        getLogger().warning(() -> "Attempting to bind symbol when no native access is allowed");
        return null;
    }

    @Override
    public Object getCallableSignature(NativeSignature nativeSignature, boolean fromJava) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.fatal("Panama not supported without native access.");
    }

    @Override
    public boolean hasFallbackSymbols() {
        return false;
    }

    @Override
    public boolean isFallbackSymbol(TruffleObject symbol) {
        return false;
    }

    @Override
    public NativeAccess getFallbackAccess() {
        return null;
    }

    @Override
    public Object callSignature(Object signature, @Pointer TruffleObject symbol, Object... args) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.fatal("Panama not supported without native access.");
    }

    @Override
    public SignatureCallNode createSignatureCall(NativeSignature nativeSignature) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.fatal("Panama not supported without native access.");
    }

    @Override
    public @Buffer TruffleObject allocateMemory(long size) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.fatal("AllocateMemory not supported without native access.");
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Pointer TruffleObject buffer, long newSize) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.fatal("AllocateMemory not supported without native access.");
    }

    @Override
    public void freeMemory(@Pointer TruffleObject buffer) {
        getLogger().warning(() -> "Attempting to free memory without native access.");
    }

    @Override
    public @Pointer TruffleObject createNativeClosure(TruffleObject executable, NativeSignature nativeSignature) {
        getLogger().warning(() -> "Attempting to create native closure without native access.");
        return null;
    }

    @Override
    public void prepareThread() {
    }

    private static TruffleLogger getLogger() {
        return logger;
    }

    @Collect(NativeAccess.class)
    public static final class Provider implements NativeAccess.Provider {

        public static final String ID = "no-native";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NoNativeAccess();
        }
    }
}
