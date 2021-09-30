package com.oracle.svm.core.meta;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import jdk.vm.ci.meta.VMConstant;

public class SubstrateMethodVMConstant implements VMConstant {

    private final CFunctionPointer pointer;

    public SubstrateMethodVMConstant(CFunctionPointer pointer) {
        this.pointer = pointer;
    }

    public CFunctionPointer pointer() {
        return pointer;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public String toValueString() {
        return "SVMConstant";
    }
}
