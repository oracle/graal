package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.runtime.AttributeInfo;

public class ExceptionsAttribute extends AttributeInfo {
    private final int[] checkedExceptionsCPI;

    public ExceptionsAttribute(String name, int[] checkedExceptionsCPI) {
        super(name, null);
        this.checkedExceptionsCPI = checkedExceptionsCPI;
    }

    public int[] getCheckedExceptionsCPI() {
        return checkedExceptionsCPI;
    }
}
