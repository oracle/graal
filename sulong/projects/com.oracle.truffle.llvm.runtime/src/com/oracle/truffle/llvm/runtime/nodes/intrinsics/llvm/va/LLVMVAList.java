package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
public class LLVMVAList implements TruffleObject {

    protected final Object vaListStorage;

    public LLVMVAList(Object vaListStorage) {
        this.vaListStorage = vaListStorage;
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    @ExportMessage
    byte readI8(long offset, @CachedLibrary(value = "this.vaListStorage") LLVMManagedReadLibrary managedReadLib) {
        return managedReadLib.readI8(vaListStorage, offset);
    }

    @ExportMessage
    short readI16(long offset, @CachedLibrary(value = "this.vaListStorage") LLVMManagedReadLibrary managedReadLib) {
        return managedReadLib.readI16(vaListStorage, offset);
    }

    @ExportMessage
    int readI32(long offset, @CachedLibrary(value = "this.vaListStorage") LLVMManagedReadLibrary managedReadLib) {
        return managedReadLib.readI32(vaListStorage, offset);
    }

    @ExportMessage
    LLVMPointer readPointer(long offset, @CachedLibrary(value = "this.vaListStorage") LLVMManagedReadLibrary managedReadLib) {
        return managedReadLib.readPointer(vaListStorage, offset);
    }

    @ExportMessage
    Object readGenericI64(long offset, @CachedLibrary(value = "this.vaListStorage") LLVMManagedReadLibrary managedReadLib) {
        return managedReadLib.readGenericI64(vaListStorage, offset);
    }

    @ExportMessage
    void writeI8(long offset, byte value, @CachedLibrary(value = "this.vaListStorage") LLVMManagedWriteLibrary managedWriteLib) {
        managedWriteLib.writeI8(vaListStorage, offset, value);
    }

    @ExportMessage
    void writeI16(long offset, short value, @CachedLibrary(value = "this.vaListStorage") LLVMManagedWriteLibrary managedWriteLib) {
        managedWriteLib.writeI16(vaListStorage, offset, value);
    }

    @ExportMessage
    void writeI32(long offset, int value, @CachedLibrary(value = "this.vaListStorage") LLVMManagedWriteLibrary managedWriteLib) {
        managedWriteLib.writeI32(vaListStorage, offset, value);
    }

    @ExportMessage
    void writeGenericI64(long offset, Object value, @CachedLibrary(value = "this.vaListStorage") LLVMManagedWriteLibrary managedWriteLib) {
        managedWriteLib.writeGenericI64(vaListStorage, offset, value);
    }

}
