package com.oracle.truffle.espresso._native.nfi;

import java.nio.file.Path;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso._native.NativeAccess;
import com.oracle.truffle.espresso._native.Pointer;

public final class NFISulongNativeAccess extends NFINativeAccess {

    public NFISulongNativeAccess(TruffleLanguage.Env env) {
        super(env);
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        String nfiSource = String.format("with llvm load(RTLD_LAZY) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        return null; // not supported
    }

    public static final class Provider implements NativeAccess.Provider {
        @Override
        public String id() {
            return "nfi-sulong";
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NFISulongNativeAccess(env);
        }
    }
}
