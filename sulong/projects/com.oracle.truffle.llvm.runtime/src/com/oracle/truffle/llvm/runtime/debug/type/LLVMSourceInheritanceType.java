package com.oracle.truffle.llvm.runtime.debug.type;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public class LLVMSourceInheritanceType extends LLVMSourceMemberType {
    public LLVMSourceInheritanceType(String name, long size, long align, long offset, LLVMSourceLocation location) {
        this(name, size, align, offset, LLVMSourceType.UNKNOWN, location, false);
    }

    private boolean virtual;

    private LLVMSourceInheritanceType(String name, long size, long align, long offset, LLVMSourceType elementType, LLVMSourceLocation location, boolean virtual) {
        super(name, size, align, offset, location);
        setElementType(elementType);
        this.virtual = virtual;
    }

    public boolean isVirtual() {
        return virtual;
    }

    public void setVirtual(boolean virtual) {
        CompilerAsserts.neverPartOfCompilation();
        this.virtual = virtual;
    }

}
