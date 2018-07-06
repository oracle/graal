package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.ClassConstant;

public class ExceptionHandlerEntry {
    private final int startPc;
    private final int endPc;
    private final int handlerPc;
    private final int catchTypeIndex;

    public ExceptionHandlerEntry(int startPc, int endPc, int handlerPc, int catchTypeIndex) {
        this.startPc = startPc;
        this.endPc = endPc;
        this.handlerPc = handlerPc;
        this.catchTypeIndex = catchTypeIndex;
    }
}
