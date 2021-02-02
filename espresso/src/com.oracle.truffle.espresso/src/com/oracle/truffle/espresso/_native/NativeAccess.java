package com.oracle.truffle.espresso._native;

import java.nio.file.Path;
import java.util.List;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.jni.Buffer;
import com.oracle.truffle.espresso.jni.Pointer;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Encapsulates minimal functionality required to interface with the native world in the JVM.
 *
 * <p>
 * Includes similar functionality provided by libdl to load and peek into native libraries: dlopen,
 * dlclose and dlsym.
 * 
 * Memory management: malloc and free.
 * 
 * Lifting and sinking: A native method can be "lifted" to Java. A Java method can be "sunk" to the
 * native world e.g. as pointer that can be called.
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
     *         directly from native code or
     *         {@link #bindSymbol(TruffleObject, NativeType, NativeType...) bound} to a specific
     *         signature.
     */
    @Pointer
    TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName);

    /**
     * Native closure to Java (lifting). Returns an {@link InteropLibrary#isExecutable(Object)
     * executable} {@link TruffleObject object}.
     */
    @Pointer
    TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeType returnType, NativeType... parameterTypes);

    void unbindSymbol(@Pointer TruffleObject symbol);

    default @Pointer TruffleObject lookupAndBindSymbol(@Pointer TruffleObject library, String symbolName, NativeType returnType, NativeType... parameterTypes) {
        @Pointer
        TruffleObject symbol = lookupSymbol(library, symbolName);
        if (symbol == null) {
            // not found
            return null;
        }
        return bindSymbol(symbol, returnType, parameterTypes);
    }

    /**
     * Similar to malloc. The result of allocating a 0-sized buffer is an implementation detail.
     *
     * @throws IllegalArgumentException if the size is negative
     *
     * @return <code>null</code> if the memory cannot be allocated. Otherwise, a
     *         {@link InteropLibrary#hasBufferElements(Object) buffer}.
     */
    @Buffer
    TruffleObject allocateMemory(long size);

    /**
     * Similar to realloc. The result of allocating a 0-sized buffer is an implementation detail.
     *
     * @throws IllegalArgumentException if the size is negative
     *
     * @return <code>null</code> if the memory cannot be re-allocated. Otherwise, a
     *         {@link InteropLibrary#hasBufferElements(Object) buffer}.
     */
    @Buffer
    TruffleObject reallocateMemory(@Buffer TruffleObject buffer, long newSize);

    /**
     * Similar to free. Accessing the buffer after free causes undefined behavior.
     */
    void freeMemory(@Buffer TruffleObject buffer);

    /**
     * Sinking, make a Java method accessible to the native world. Returns an
     * {@link InteropLibrary#isPointer(Object) pointer} {@link TruffleObject object}, callable from
     * native code.
     */
    @Pointer
    TruffleObject createNativeClosure(TruffleObject executable, NativeType returnType, NativeType... parameterTypes);

    /**
     * Releases a native closure allocated with
     * {@link #createNativeClosure(TruffleObject, NativeType, NativeType...)}. Using the native
     * closure after release causes undefined behavior.
     */
    void releaseClosure(@Pointer TruffleObject closure);
}
