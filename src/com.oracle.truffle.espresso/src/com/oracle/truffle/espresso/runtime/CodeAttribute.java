package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.meta.ExceptionHandler;

public class CodeAttribute extends AttributeInfo {
    private final int maxStack;
    private final int maxLocals;
    private final byte[] code;
    private final ExceptionHandler[] exceptionHandlerEntries;
    private final AttributeInfo[] attributes;

    public CodeAttribute(String name, int maxStack, int maxLocals, byte[] code, ExceptionHandler[] exceptionHandlerEntries, AttributeInfo[] attributes) {
        super(name, null);
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.code = code;
        this.exceptionHandlerEntries = exceptionHandlerEntries;
        this.attributes = attributes;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public byte[] getCode() {
        return code;
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlerEntries;
    }
}
