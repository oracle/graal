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
package com.oracle.truffle.espresso.ffi.nfi;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.Buffer;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.TruffleByteBuffer;
import com.oracle.truffle.espresso.impl.EmptyKeysArray;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.substitutions.Collect;

/**
 * Isolated native linking namespace based on glibc's dlmopen.
 *
 * <p>
 * dlmopen was ported from Solaris, but the glibc port is buggy and crash-prone; to improve the
 * situation, a tiny native library (libeden.so) is used to hook certain methods and avoid crashes.
 *
 * The isolated namespaces have limitations:
 * <ul>
 * <li>Maximum of 16 namespaces (hardcoded in glibc), isolated namespaces cannot be reused.
 * <li>malloc/free cannot cross the namespace boundary e.g. malloc outside, free inside.
 * <li>External threads TLS storage is not initialized correctly for libraries inside the linking
 * namespaces.
 * <li>Spurious crashes when binding non-existing symbols. Use <code>LD_DEBUG=unused</code> as a
 * workaround.
 * </ul>
 */
public final class NFIIsolatedNativeAccess extends NFINativeAccess {

    private final @Pointer TruffleObject edenLibrary;
    private final @Pointer TruffleObject malloc;
    private final @Pointer TruffleObject free;
    private final @Pointer TruffleObject realloc;
    private final @Pointer TruffleObject ctypeInit;
    private final @Pointer TruffleObject dlsym;
    private final DefaultLibrary defaultLibrary;

    NFIIsolatedNativeAccess(TruffleLanguage.Env env) {
        super(env);
        // libeden.so must be the first library loaded in the isolated namespace.
        Path espressoLibraryPath = EspressoLanguage.getEspressoLibs(env);
        this.edenLibrary = loadLibrary(Collections.singletonList(espressoLibraryPath), "eden", true);
        this.malloc = lookupAndBindSymbol(edenLibrary, "malloc", NativeSignature.create(NativeType.POINTER, NativeType.LONG));
        this.realloc = lookupAndBindSymbol(edenLibrary, "realloc", NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.LONG));
        this.free = lookupAndBindSymbol(edenLibrary, "free", NativeSignature.create(NativeType.VOID, NativeType.POINTER));
        this.dlsym = lookupAndBindSymbol(edenLibrary, "dlsym", NativeSignature.create(NativeType.POINTER, NativeType.POINTER, NativeType.POINTER));
        this.ctypeInit = lookupAndBindSymbol(edenLibrary, "eden_ctypeInit", NativeSignature.create(NativeType.VOID));
        /*
         * The default library provided by NFI does not work inside (dlmopen) isolated namespaces
         * because is based on calling dlsym located outside the isolated namespace. libeden.so,
         * loaded inside the isolated namespace provides a dlsym shim inside the namespace.
         */
        this.defaultLibrary = new DefaultLibrary(this.dlsym, rtldDefault());
    }

    private TruffleObject rtldDefault() {
        TruffleObject edenRtldDefault = lookupAndBindSymbol(edenLibrary, "eden_RTLD_DEFAULT", NativeSignature.create(NativeType.POINTER));
        try {
            TruffleObject result = (TruffleObject) InteropLibrary.getUncached().execute(edenRtldDefault);
            assert InteropLibrary.getUncached().isPointer(result);
            return result;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    protected @Pointer TruffleObject loadLibrary0(Path libraryPath) {
        String nfiSource = String.format("load(RTLD_LAZY|RTLD_LOCAL|ISOLATED_NAMESPACE) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        return defaultLibrary;
    }

    @ExportLibrary(InteropLibrary.class)
    static class DefaultLibrary implements TruffleObject {

        final @Pointer TruffleObject dlsym;
        final @Pointer TruffleObject rtldDefault;

        DefaultLibrary(@Pointer TruffleObject dlsym, @Pointer TruffleObject rtldDefault) {
            this.dlsym = Objects.requireNonNull(dlsym);
            this.rtldDefault = Objects.requireNonNull(rtldDefault);
        }

        @ExportMessage
        boolean isMemberReadable(@SuppressWarnings("unused") String member) {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return EmptyKeysArray.INSTANCE;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object readMember(String member,
                        @CachedLibrary("this.dlsym") InteropLibrary interop,
                        @CachedLibrary(limit = "2") InteropLibrary isNullInterop,
                        @Cached BranchProfile error) throws UnknownIdentifierException {
            try {
                Object result = interop.execute(dlsym, this.rtldDefault, TruffleByteBuffer.allocateDirectStringUTF8(member));
                if (isNullInterop.isNull(result)) {
                    error.enter();
                    throw UnknownIdentifierException.create(member);
                }
                return result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        Object toDisplayString(boolean allowSideEffects) {
            return "nfi-dlmopen default library";
        }
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
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Collect(NativeAccess.class)
    public static final class Provider implements NativeAccess.Provider {

        public static final String ID = "nfi-dlmopen";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NFIIsolatedNativeAccess(env);
        }
    }
}
