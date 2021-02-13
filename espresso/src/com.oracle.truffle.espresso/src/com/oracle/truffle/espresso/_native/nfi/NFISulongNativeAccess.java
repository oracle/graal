package com.oracle.truffle.espresso._native.nfi;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso._native.NativeAccess;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso.runtime.EspressoContext;

import java.nio.file.Path;

public final class NFISulongNativeAccess extends NFINativeAccess {

    public NFISulongNativeAccess(EspressoContext context) {
        super(context);
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        String nfiSource = String.format("with llvm load(RTLD_LAZY) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    public static final class Provider implements NativeAccess.Provider {
        @Override
        public String id() {
            return "nfi-sulong";
        }

        @Override
        public NativeAccess create(EspressoContext context) {
            return new NFISulongNativeAccess(context);
        }
    }
}
