package com.oracle.truffle.llvm.runtime.library.internal;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(value = LLVMAsForeignLibrary.class, receiverType = Object.class)
public class LLVMAsForeignLibraryDefaults {

    @ExportMessage
    static boolean isForeign(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    @ExportMessage
    static Object asForeign(@SuppressWarnings("unused") Object receiver) {
        return null;
    }
}
