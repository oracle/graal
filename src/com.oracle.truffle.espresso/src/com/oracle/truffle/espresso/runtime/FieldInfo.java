package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.Utf8Constant;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public class FieldInfo {
    private final Utf8Constant name;
    private final int flags;
    private final TypeDescriptor descriptor;
    private final AttributeInfo[] attributes;

    public FieldInfo(Utf8Constant name, int flags, TypeDescriptor descriptor, AttributeInfo[] attributes) {

        this.name = name;
        this.flags = flags;
        this.descriptor = descriptor;
        this.attributes = attributes;
    }
}
