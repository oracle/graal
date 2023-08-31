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
package com.oracle.truffle.espresso.ffi;

import java.nio.file.Path;
import java.util.List;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoProperties;

/**
 * Encapsulates minimal functionality required to interface with the native world in the JVM.
 *
 * <p>
 * Includes similar functionality provided by libdl to load and peek into native libraries: dlopen,
 * dlclose and dlsym.
 * <p>
 * Memory management: malloc and free.
 * <p>
 * Lifting and sinking: A native method can be "lifted" to Java. A Java method can be "sunk" to the
 * native world e.g. as native pointer that can be called.
 */
public interface NativeAccess {
    /**
     * Similar to dlopen. Tries to load a native library from the provided path.
     *
     * @return <code>null</code> if the library cannot be loaded, otherwise a {@link TruffleObject
     *         handle} that can be used to lookup (and bind) symbols from the library.
     */
    @Pointer
    TruffleObject loadLibrary(Path libraryPath);

    /**
     * Returns the "default" library. Some backends may not be able to provide such functionality
     * e.g. dlmopen-based namespaces cannot access the global namespace.
     * 
     * @return <code>null</code> if the library cannot be loaded, otherwise a {@link TruffleObject
     *         handle} that can be used to lookup (and bind) symbols from the library.
     */
    @Pointer
    TruffleObject loadDefaultLibrary();

    /**
     * Similar to dlclose. Uses the native mechanism to close, or rather decrement the reference
     * count to a native library obtained using {@link #loadLibrary(Path)}.
     */
    void unloadLibrary(@Pointer TruffleObject library);

    default @Pointer TruffleObject loadLibrary(List<Path> searchPaths, String shortName, boolean notFoundIsFatal) {
        for (Path path : searchPaths) {
            Path libPath = path.resolve(System.mapLibraryName(shortName));
            @Pointer
            TruffleObject library = loadLibrary(libPath.toAbsolutePath());
            if (library != null) {
                return library;
            }
        }
        if (notFoundIsFatal) {
            throw EspressoError.shouldNotReachHere("Cannot load library: " + shortName + "\nSearch path: " + searchPaths);
        }
        return null;
    }

    /**
     * Similar to dlsym. The "default" namespace may not be supported.
     *
     * @return <code>null</code> if the symbol is not found, otherwise a pointer that can be called
     *         directly from native code or {@link #bindSymbol(TruffleObject, NativeSignature)
     *         bound} to a specific signature.
     */
    @Pointer
    TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName);

    /**
     * Native closure to Java (lifting). Returns an {@link InteropLibrary#isExecutable(Object)
     * executable} {@link TruffleObject object}.
     *
     * <p>
     * The returned object is managed by the Java GC, to keep it alive, including its native
     * component, keep a strong reference to it.
     */
    @Pointer
    TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeSignature nativeSignature);

    Object getCallableSignature(NativeSignature nativeSignature, boolean fromJava);

    Object callSignature(Object signature, @Pointer TruffleObject symbol, Object... args) throws UnsupportedMessageException, UnsupportedTypeException, ArityException;

    SignatureCallNode createSignatureCall(NativeSignature nativeSignature, boolean fromJava);

    default @Pointer TruffleObject lookupAndBindSymbol(@Pointer TruffleObject library, String symbolName, NativeSignature nativeSignature) {
        @Pointer
        TruffleObject symbol = lookupSymbol(library, symbolName);
        if (symbol == null) {
            // not found
            return null;
        }
        return bindSymbol(symbol, nativeSignature);
    }

    /**
     * Similar to malloc. The result of allocating a 0-sized buffer is an implementation detail.
     *
     * <h3>Lifetime
     *
     * @return <code>null</code> if the memory cannot be allocated. Otherwise, a
     *         {@link InteropLibrary#hasBufferElements(Object) buffer}.
     * @throws IllegalArgumentException if the size is negative
     */
    @Buffer
    TruffleObject allocateMemory(long size);

    /**
     * Similar to realloc. The result of allocating a 0-sized buffer is an implementation detail.
     *
     * @return <code>null</code> if the memory cannot be re-allocated. Otherwise, a
     *         {@link InteropLibrary#hasBufferElements(Object) buffer}.
     * @throws IllegalArgumentException if the size is negative
     */
    @Buffer
    TruffleObject reallocateMemory(@Pointer TruffleObject buffer, long newSize);

    /**
     * Similar to free. Accessing the buffer after free may cause explosive undefined behavior.
     */
    void freeMemory(@Pointer TruffleObject buffer);

    /**
     * Sinking, make a Java method accessible to the native world. Returns an
     * {@link InteropLibrary#isPointer(Object) pointer} {@link TruffleObject object}, callable from
     * native code.
     *
     * <p>
     * The returned object is managed by the Java GC, to keep it alive, including its native
     * component, keep a strong reference to it.
     */
    @Pointer
    TruffleObject createNativeClosure(TruffleObject executable, NativeSignature nativeSignature);

    static NativeType kindToNativeType(JavaKind kind) {
        // @formatter:off
        switch (kind) {
            case Boolean: return NativeType.BOOLEAN;
            case Byte:    return NativeType.BYTE;
            case Short:   return NativeType.SHORT;
            case Char:    return NativeType.CHAR;
            case Int:     return NativeType.INT;
            case Long:    return NativeType.LONG;
            case Float:   return NativeType.FLOAT;
            case Double:  return NativeType.DOUBLE;
            case Void:    return NativeType.VOID;
            case Object:  return NativeType.OBJECT;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("unexpected kind: " + kind);
        }
        // @formatter:on
    }

    /**
     * Allows the native backend to modify Espresso VM properties before creation e.g. inject/change
     * paths.
     */
    @SuppressWarnings("unused")
    default void updateEspressoProperties(EspressoProperties.Builder builder, OptionValues options) {
        // nop
    }

    /**
     * Hook called when starting guest threads, some native backends may need to external threads
     * (e.g. initialize TLS storage).
     */
    void prepareThread();

    /**
     * NativeAccess SPI.
     */
    interface Provider {
        String id();

        NativeAccess create(TruffleLanguage.Env env);
    }
}
