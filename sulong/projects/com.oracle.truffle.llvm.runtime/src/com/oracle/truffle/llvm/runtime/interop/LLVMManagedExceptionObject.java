package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(LLVMManagedReadLibrary.class)
// @ExportLibrary(LLVMManagedWriteLibrary.class)
public class LLVMManagedExceptionObject extends LLVMInternalTruffleObject {
    private final Object exceptionObject;

    public LLVMManagedExceptionObject(Object exceptionObject) {
        this.exceptionObject = exceptionObject;
    }

    @ExportMessage
    public LLVMPointer readPointer(long offset) {
        if (offset == 8) {
            return LLVMManagedPointer.create(exceptionObject);
        }
        // System.out.println("\tread Pointer of mmo at offset" + offset);
        return LLVMNativePointer.createNull();
    }

    @ExportMessage
    public int readI32(long offset) {
        int ret = 17;
        // System.out.printf("\tread i32 of mmo at offset %d\n", offset);
        return ret;
    }

// @ExportMessage
// public void writePointer(long offset, LLVMPointer value) {
// System.out.printf("\twrite Pointer %d %s\n", offset, value);
// }
//
// @ExportMessage
// public void writeI32(long offset, int value) {
// System.out.printf("\twrite i32 %d %d\n", offset, value);
// }

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
        // System.out.println("\tread generic i64 of mmo at offset " + offset);
        if (offset == 0) {
            return 98765L;// TODO
        } else if (offset == 8) {
            return LLVMManagedPointer.create(exceptionObject);
        } else {
            return 0L;
        }
    }

}
