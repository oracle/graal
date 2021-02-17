/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso._native.nfi;

import java.nio.file.Path;
import java.util.Collections;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso._native.NativeAccess;
import com.oracle.truffle.espresso._native.NativeSignature;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso._native.Buffer;
import com.oracle.truffle.espresso._native.NativeType;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso._native.TruffleByteBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import org.graalvm.home.HomeFinder;

final class NFIIsolatedNativeAccess extends NFINativeAccess {

    final @Pointer TruffleObject edenLibrary;
    final @Pointer TruffleObject malloc;
    final @Pointer TruffleObject free;
    final @Pointer TruffleObject realloc;
    final @Pointer TruffleObject ctypeInit;

    public NFIIsolatedNativeAccess(TruffleLanguage.Env env) {
        super(env);
        // libeden.so must be the first library loaded in the isolated namespace.
        Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
        Path espressoLibraryPath = espressoHome.resolve("lib");
        this.edenLibrary = loadLibrary(Collections.singletonList(espressoLibraryPath), "eden", true);
        this.malloc = lookupAndBindSymbol(edenLibrary, "malloc", NativeSignature.create(NativeType.POINTER, NativeType.LONG));
        this.realloc = lookupAndBindSymbol(edenLibrary, "realloc", NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.LONG));
        this.free = lookupAndBindSymbol(edenLibrary, "free", NativeSignature.create(NativeType.VOID, NativeType.POINTER));
        this.ctypeInit = lookupAndBindSymbol(edenLibrary, "ctypeInit", NativeSignature.create(NativeType.VOID));
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        String nfiSource = String.format("load(RTLD_LAZY|ISOLATED_NAMESPACE) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    @Override
    public @Buffer TruffleObject allocateMemory(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("negative buffer length: " + size);
        }
        try {
            @Pointer
            TruffleObject address = (TruffleObject) uncachedInterop.execute(malloc, size);
            if (InteropLibrary.getUncached().isNull(address)) {
                // malloc returned NULL
                return null;
            }
            return TruffleByteBuffer.wrap(address, Math.toIntExact(size));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public void freeMemory(@Pointer TruffleObject buffer) {
        assert InteropLibrary.getUncached().isPointer(buffer);
        try {
            uncachedInterop.execute(free, buffer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Pointer TruffleObject buffer, long newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("negative buffer length: " + newSize);
        }
        assert InteropLibrary.getUncached().isPointer(buffer);
        try {
            @Pointer
            TruffleObject address = (TruffleObject) uncachedInterop.execute(realloc, buffer, newSize);
            if (InteropLibrary.getUncached().isNull(address)) {
                // realloc returned NULL
                return null;
            }
            return TruffleByteBuffer.wrap(address, Math.toIntExact(newSize));
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public void prepareThread() {
        try {
            uncachedInterop.execute(ctypeInit);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static final class Provider implements NativeAccess.Provider {
        @Override
        public String id() {
            return "nfi-dlmopen";
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NFIIsolatedNativeAccess(env);
        }
    }
}
