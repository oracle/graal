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
import java.nio.file.Paths;
import java.util.List;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.meta.EspressoError;
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

    @TruffleBoundary
    default String mapLibraryName(String libname) {
        return System.mapLibraryName(libname);
    }

    default boolean isBuiltIn(@SuppressWarnings("unused") String libname) {
        return false;
    }

    /**
     * Similar to dlclose. Uses the native mechanism to close, or rather decrement the reference
     * count to a native library obtained using {@link #loadLibrary(Path)}.
     */
    void unloadLibrary(@Pointer TruffleObject library);

    default @Pointer TruffleObject loadLibrary(List<Path> searchPaths, String shortName, boolean notFoundIsFatal) {
        if (isBuiltIn(shortName)) {
            TruffleObject library = loadLibrary(Paths.get(shortName));
            if (library != null) {
                return library;
            }
        }
        for (Path path : searchPaths) {
            Path libPath = path.resolve(mapLibraryName(shortName));
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

    /**
     * Prepares a signature object that can be used for calling a symbol later using
     * {@link #callSignature}. This is useful when the symbol that will be called is not know yet.
     *
     * @param nativeSignature a description of the signature that this object should be able to
     *            call.
     * @param forFallbackSymbol whether the symbols that will be called with this signature are
     *            {@linkplain #isFallbackSymbol fallback symbols} or not.
     */
    Object getCallableSignature(NativeSignature nativeSignature, boolean forFallbackSymbol);

    /**
     * Checks whether this native access can ever return true for
     * {@link #isFallbackSymbol(TruffleObject)}.
     */
    boolean hasFallbackSymbols();

    /**
     * Determines if the symbol is a fallback symbol.
     * <p>
     * Some implementations might not be able to load native libraries in the mode they expect
     * (e.g., the sulong native access might not be able to find bitcode in a library). When that
     * happens the libraries will still work but return fallback symbols (e.g., in that case sulong
     * would return libffi symbols). Callable signatures for such symbols must be prepared specially
     * by passing <code>true</code> to the <code>forFallbackSymbol</code> parameter of
     * {@link #getCallableSignature} because signature only work for a specific type of symbol
     * (GR-37607).
     *
     * @param symbol a symbol obtained from {@link #lookupSymbol}.
     */
    boolean isFallbackSymbol(TruffleObject symbol);

    /**
     * If the current native access has a {@link #isFallbackSymbol fallback mode}, this provides a
     * native access that works like the fallback mode.
     * <p>
     * This can be used to load a {@link com.oracle.truffle.espresso.jni.NativeEnv} with the
     * fallback access in case it is necessary to provide a fallback environment for libraries with
     * fallback symbols. This is necessary when a native env containing variadic function pointers
     * is passed to a fallback symbol (see libffi/libffi#388).
     *
     * @return a native access that corresponds to the way fallback symbols are implemented if the
     *         current native access {@linkplain #hasFallbackSymbols() has fallback symbols},
     *         <code>null</code> otherwise.
     */
    NativeAccess getFallbackAccess();

    Object callSignature(Object signature, @Pointer TruffleObject symbol, Object... args) throws UnsupportedMessageException, UnsupportedTypeException, ArityException;

    SignatureCallNode createSignatureCall(NativeSignature nativeSignature);

    default @Pointer TruffleObject lookupAndBindSymbol(@Pointer TruffleObject library, String symbolName, NativeSignature nativeSignature) {
        return lookupAndBindSymbol(library, symbolName, nativeSignature, false, false);
    }

    default @Pointer TruffleObject lookupAndBindSymbol(@Pointer TruffleObject library, String symbolName, NativeSignature nativeSignature, boolean allowFallback, boolean allowMissing) {
        @Pointer
        TruffleObject symbol = lookupSymbol(library, symbolName);
        if (symbol == null) {
            if (!allowMissing) {
                throw EspressoError.shouldNotReachHere("Failed to locate required symbol: " + symbolName);
            }
            // not found
            return null;
        }
        if (!allowFallback && isFallbackSymbol(symbol)) {
            String message = "Unexpected fallback symbol: " + symbolName + "\n" +
                            "A likely explanation is that a core espresso library was expected to contain bitcode but it doesn't.\n" +
                            "Core JDK libraries with LLVM bitcode are currently only available on linux-amd64 and darwin-amd64.\n" +
                            "On linux-aarch64 you could instead try to set `java.NativeBackend` to `nfi-dlmopen`.";
            if (EspressoOptions.RUNNING_ON_SVM) {
                message += "\nIn a native-image, if a single espresso context is used, it's recommended to use the `nfi-native` backend.";
            } else {
                message += "\nOn other platforms, you can try to run your embedding of espresso as a native-image if a single espresso context is used.";
            }
            throw EspressoError.shouldNotReachHere(message);
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
