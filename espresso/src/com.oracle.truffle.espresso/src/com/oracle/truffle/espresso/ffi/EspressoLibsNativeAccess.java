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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.libs.Lib;
import com.oracle.truffle.espresso.libs.Libs;
import com.oracle.truffle.espresso.libs.SubstitutionFactoryWrapper;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Uses Espresso's implementation of standard JDK libraries, falling back to a delegate if the
 * implementation is missing.
 * <p>
 * This can be used in conjunction with option {@code 'java.GuestNativeAccess=no-native'} or
 * {@code env.isNativeAccessAllowed() == false} to prevent guest code from accessing the native
 * world, but still allowing the java standard library to work.
 *
 * @see com.oracle.truffle.espresso.libs.Libs
 */
public class EspressoLibsNativeAccess extends ContextAccessImpl implements NativeAccess {

    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, EspressoLibsNativeAccess.class);

    private static TruffleLogger getLogger() {
        return logger;
    }

    private final Libs libs;
    private final NativeAccess delegate;

    public EspressoLibsNativeAccess(EspressoContext ctx, NativeAccess delegate) {
        super(ctx);
        this.delegate = delegate;
        this.libs = new Libs();
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        Path libname = libraryPath.getFileName();
        if (libname != null && libs.isKnown(libraryPath.toString())) {
            getLogger().fine(() -> "Loading espresso lib: " + libname);
            return libs.loadLibrary(getContext(), libname.toString());

        }
        // Failed to find library in known libs:
        // try to find an actual library (can be null)
        getLogger().fine(() -> "Could not find espresso lib '" + libname + "', trying delegate Native Access...");
        return delegate.loadLibrary(libraryPath);
    }

    @Override
    @TruffleBoundary
    public String mapLibraryName(String libname) {
        if (libs.isKnown(libname)) {
            return libname;
        }
        return delegate.mapLibraryName(libname);
    }

    @Override
    @TruffleBoundary
    public boolean isBuiltIn(String libname) {
        return libs.isKnown(libname);
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        return delegate.loadDefaultLibrary();
    }

    @Override
    public void unloadLibrary(@Pointer TruffleObject library) {
        delegate.unloadLibrary(library);
    }

    @Override
    public @Pointer TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName) {
        if (library instanceof Lib lib) {
            TruffleObject result = lib.find(symbolName);
            if (result != null) {
                getLogger().fine(() -> "Found symbol '" + symbolName + "' in espresso lib " + lib.name());
                return result;
            }
            getLogger().fine(() -> "Failed to locate symbol '" + symbolName + "' in espresso lib " + lib.name());
        } else {
            // Delegate library
            getLogger().fine(() -> "Espresso libs delegating for: " + symbolName);
            return delegate.lookupSymbol(library, symbolName);
        }
        return null;
    }

    @Override
    public @Pointer TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeSignature nativeSignature) {
        if (symbol instanceof SubstitutionFactoryWrapper) {
            return symbol;
        }
        return delegate.bindSymbol(symbol, nativeSignature);
    }

    @Override
    public Object getCallableSignature(NativeSignature nativeSignature, boolean fromJava) {
        return delegate.getCallableSignature(nativeSignature, fromJava);
    }

    @Override
    public boolean hasFallbackSymbols() {
        return delegate.hasFallbackSymbols();
    }

    @Override
    public boolean isFallbackSymbol(TruffleObject symbol) {
        if (symbol instanceof SubstitutionFactoryWrapper) {
            return false;
        }
        return delegate.isFallbackSymbol(symbol);
    }

    @Override
    public NativeAccess getFallbackAccess() {
        return delegate.getFallbackAccess();
    }

    @Override
    public Object callSignature(Object signature, @Pointer TruffleObject symbol, Object... args) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        return delegate.callSignature(signature, symbol, args);
    }

    @Override
    public SignatureCallNode createSignatureCall(NativeSignature nativeSignature) {
        return delegate.createSignatureCall(nativeSignature);
    }

    @Override
    public @Buffer TruffleObject allocateMemory(long size) {
        return delegate.allocateMemory(size);
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Pointer TruffleObject buffer, long newSize) {
        return delegate.reallocateMemory(buffer, newSize);
    }

    @Override
    public void freeMemory(@Pointer TruffleObject buffer) {
        delegate.freeMemory(buffer);
    }

    @Override
    public @Pointer TruffleObject createNativeClosure(TruffleObject executable, NativeSignature nativeSignature) {
        return delegate.createNativeClosure(executable, nativeSignature);
    }

    @Override
    public void prepareThread() {
        delegate.prepareThread();
    }

    @TruffleBoundary
    public boolean isKnownBootLibrary(String path) {
        Path p = Path.of(path);
        Path name = p.getFileName();
        Path dir = p.getParent();
        if (getContext().getVmProperties().bootLibraryPath().contains(dir)) {
            return libs.isKnown(name.toString());
        }
        return false;
    }
}
