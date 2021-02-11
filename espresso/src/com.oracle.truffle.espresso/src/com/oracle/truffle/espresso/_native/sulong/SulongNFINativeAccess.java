package com.oracle.truffle.espresso._native.sulong;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso._native.nfi.NFINativeAccess;
import com.oracle.truffle.espresso.runtime.EspressoContext;

import java.nio.file.Path;

public final class SulongNFINativeAccess extends NFINativeAccess {

    public SulongNFINativeAccess(EspressoContext context) {
        super(context);
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        String nfiSource = String.format("with llvm load(RTLD_LAZY) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }
}
