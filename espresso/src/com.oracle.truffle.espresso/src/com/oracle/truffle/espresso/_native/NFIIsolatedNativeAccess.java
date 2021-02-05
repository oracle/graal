package com.oracle.truffle.espresso._native;

import java.nio.file.Path;
import java.util.Collections;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class NFIIsolatedNativeAccess extends NFINativeAccess {

    final @Pointer TruffleObject edenLibrary;
    final @Pointer TruffleObject malloc;
    final @Pointer TruffleObject free;
    final @Pointer TruffleObject realloc;
    final @Pointer TruffleObject dlsym;

    public NFIIsolatedNativeAccess(EspressoContext context) {
        super(context);
        OptionValues options = context.getEnv().getOptions();
        boolean dlmopen = options.get(EspressoOptions.UseTruffleNFIIsolatedNamespace);
        EspressoError.guarantee(dlmopen, "Native isolation is not enabled (--java.UseTruffleNFIIsolatedNamespace)");
        // libeden.so must be the first library loaded in the isolated namespace.
        Path espressoLibraryPath = context.getVmProperties().espressoHome().resolve("lib");
        this.edenLibrary = loadLibrary(Collections.singletonList(espressoLibraryPath), "eden", true);
        this.malloc = lookupAndBindSymbol(edenLibrary, "malloc", NativeType.POINTER, NativeType.LONG);
        this.realloc = lookupAndBindSymbol(edenLibrary, "realloc", NativeType.POINTER, NativeType.POINTER, NativeType.LONG);
        this.free = lookupAndBindSymbol(edenLibrary, "free", NativeType.VOID, NativeType.POINTER);
        this.dlsym = lookupAndBindSymbol(edenLibrary, "dlsym", NativeType.POINTER, NativeType.POINTER, NativeType.POINTER);
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
            return (TruffleObject) INTEROP.execute(malloc, size);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public void freeMemory(@Buffer TruffleObject buffer) {
        try {
            INTEROP.execute(free, buffer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Buffer TruffleObject buffer, long newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("negative buffer length: " + newSize);
        }
        try {
            return (TruffleObject) INTEROP.execute(realloc, buffer, newSize);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
