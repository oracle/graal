package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.Utf8Constant;

public class CodeAttribute extends AttributeInfo {
    private final int maxStack;
    private final int maxLocals;
    private final byte[] code;
    private final ExceptionHandlerEntry[] exceptionHandlerEntries;
    private final AttributeInfo[] attributes;

    public CodeAttribute(Utf8Constant name, int maxStack, int maxLocals, byte[] code, ExceptionHandlerEntry[] exceptionHandlerEntries, AttributeInfo[] attributes) {
        super(name, null);
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.code = code;
        this.exceptionHandlerEntries = exceptionHandlerEntries;
        this.attributes = attributes;
    }
}
