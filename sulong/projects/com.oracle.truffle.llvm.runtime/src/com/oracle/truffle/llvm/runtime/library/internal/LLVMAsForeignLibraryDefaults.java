package com.oracle.truffle.llvm.runtime.library.internal;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(value = LLVMAsForeignLibrary.class, receiverType = Object.class)
public class LLVMAsForeignLibraryDefaults {

    @ExportMessage
    static boolean isForeign(Object receiver) {
        return !(receiver instanceof LLVMInternalTruffleObject) && !LLVMPointer.isInstance(receiver);
    }

    @ExportMessage
    static Object asForeign(Object receiver) {
        return receiver;
    }
}
