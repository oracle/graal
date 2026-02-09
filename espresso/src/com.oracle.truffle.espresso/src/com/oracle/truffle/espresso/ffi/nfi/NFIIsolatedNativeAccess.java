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

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.MemoryAllocationException;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.NativeType;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.TruffleByteBuffer;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory;
import com.oracle.truffle.espresso.ffi.memory.UnsafeNativeMemory;
import com.oracle.truffle.espresso.impl.EmptyKeysArray;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
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
        super(env, new IsolatedUnsafeNativeMemory());
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
        ((IsolatedUnsafeNativeMemory) nativeMemory).init(this);
        this.defaultLibrary = new DefaultLibrary(this.dlsym, rtldDefault(), nativeMemory());

    }

    private TruffleObject rtldDefault() {
        TruffleObject edenRtldDefault = lookupAndBindSymbol(edenLibrary, "eden_RTLD_DEFAULT", NativeSignature.create(NativeType.POINTER));
        try {
            TruffleObject result = (TruffleObject) UNCACHED_INTEROP.execute(edenRtldDefault);
            assert UNCACHED_INTEROP.isPointer(result);
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
        final NativeMemory nativeMemory;

        DefaultLibrary(@Pointer TruffleObject dlsym, @Pointer TruffleObject rtldDefault, NativeMemory nativeMemory) {
            this.dlsym = Objects.requireNonNull(dlsym);
            this.rtldDefault = Objects.requireNonNull(rtldDefault);
            this.nativeMemory = nativeMemory;
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
                        @Bind Node node,
                        @Cached InlinedBranchProfile error,
                        @Cached InlinedBranchProfile oome) throws UnknownIdentifierException {
            TruffleByteBuffer buffer = null;
            try {
                // prepare interop call by creating the string buffer
                try {
                    buffer = TruffleByteBuffer.allocateDirectStringUTF8(member, nativeMemory);
                } catch (MemoryAllocationException e) {
                    oome.enter(node);
                    EspressoContext context = EspressoContext.get(node);
                    Meta meta = context.getMeta();
                    throw meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, e.getMessage(), context);
                }
                Object result = interop.execute(dlsym, this.rtldDefault, buffer);
                if (isNullInterop.isNull(result)) {
                    error.enter(node);
                    throw UnknownIdentifierException.create(member);
                }
                return result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            } finally {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }

        @ExportMessage
        @SuppressWarnings({"static-method", "unused"})
        Object toDisplayString(boolean allowSideEffects) {
            return "nfi-dlmopen default library";
        }
    }

    @Override
    public void prepareThread() {
        try {
            UNCACHED_INTEROP.execute(ctypeInit);
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

    private static final class IsolatedUnsafeNativeMemory extends UnsafeNativeMemory {
        /*
         * This can not be final since it would leak the "this" reference before the class is
         * initialized.
         */
        @CompilationFinal private NFIIsolatedNativeAccess nfiIsolatedNativeAccess;

        public void init(NFIIsolatedNativeAccess nativeAccess) {
            assert this.nfiIsolatedNativeAccess == null;
            this.nfiIsolatedNativeAccess = nativeAccess;
        }

        @Override
        public long allocateMemory(long bytes) {
            long size = bytes == 0 ? 1 : bytes;
            try {
                @Pointer
                TruffleObject address = (TruffleObject) UNCACHED_INTEROP.execute(nfiIsolatedNativeAccess.malloc, size);
                if (UNCACHED_INTEROP.isNull(address)) {
                    // malloc returned NULL
                    return 0L;
                }
                return UNCACHED_INTEROP.asPointer(address);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public void freeMemory(long address) {
            if (address == 0) {
                return;
            }
            try {
                UNCACHED_INTEROP.execute(nfiIsolatedNativeAccess.free, RawPointer.create(address));
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Override
        public long reallocateMemory(long address, long newSize) {
            long size = newSize == 0 ? 1 : newSize;
            try {
                @Pointer
                TruffleObject newAddress = (TruffleObject) UNCACHED_INTEROP.execute(nfiIsolatedNativeAccess.realloc, address, size);
                if (UNCACHED_INTEROP.isNull(address)) {
                    // realloc returned NULL
                    return 0L;
                }
                return UNCACHED_INTEROP.asPointer(newAddress);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }
}
