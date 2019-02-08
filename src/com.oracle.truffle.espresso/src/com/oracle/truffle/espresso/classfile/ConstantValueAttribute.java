package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ConstantValueAttribute extends Attribute {
    public static final Symbol<Name> NAME = Name.ConstantValue;
    private final int constantvalueIndex;

    public ConstantValueAttribute(int constantvalueIndex) {
        super(NAME, null);
        this.constantvalueIndex = constantvalueIndex;
    }

    public int getConstantvalueIndex() {
        return constantvalueIndex;
    }
}
