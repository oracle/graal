package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ExceptionsAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.Exceptions;

    private final int[] checkedExceptionsCPI;

    public ExceptionsAttribute(Symbol<Name> name, int[] checkedExceptionsCPI) {
        super(name, null);
        this.checkedExceptionsCPI = checkedExceptionsCPI;
    }

    public int[] getCheckedExceptionsCPI() {
        return checkedExceptionsCPI;
    }
}
