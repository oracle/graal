package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ExceptionsAttribute extends Attribute {

    private final int[] checkedExceptionsCPI;

    public ExceptionsAttribute(ByteString<Name> name, int[] checkedExceptionsCPI) {
        super(name, null);
        this.checkedExceptionsCPI = checkedExceptionsCPI;
    }

    public int[] getCheckedExceptionsCPI() {
        return checkedExceptionsCPI;
    }
}
