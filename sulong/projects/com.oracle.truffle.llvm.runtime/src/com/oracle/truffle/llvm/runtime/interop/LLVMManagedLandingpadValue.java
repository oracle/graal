package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(LLVMManagedReadLibrary.class)
public class LLVMManagedLandingpadValue extends LLVMInternalTruffleObject {
    private final LLVMPointer unwindHeader;
    private final int clauseId;

    public LLVMManagedLandingpadValue(LLVMPointer unwindHeader, int clauseId) {
        this.unwindHeader = unwindHeader;
        this.clauseId = clauseId;
    }

    @ExportMessage
    public LLVMPointer readPointer(long offset) {
        // System.out.printf("\tread Pointer of landingpad at offset %d\n", offset);
        return unwindHeader;
    }

    @ExportMessage
    public int readI32(long offset) {
        // System.out.printf("\tread i32 of landingpad[%d] - returning %d\n", offset, clauseId);
        return clauseId;
    }

    @ExportMessage
    final boolean isReadable() {
        return true;
    }

    @ExportMessage
    final byte readI8(long offset) {
        // System.out.printf("\tread i8 %d\n", offset);
        return (byte) 0;
    }

    @ExportMessage
    final short readI16(long offset) {
        // System.out.printf("\tread i16 %d\n", offset);
        return (short) 0;
    }

    @ExportMessage
    final Object readGenericI64(long offset) {

        // System.out.printf("\tread generic %d\n", offset);
        return 0L;
        // TODO magic value
    }

}
