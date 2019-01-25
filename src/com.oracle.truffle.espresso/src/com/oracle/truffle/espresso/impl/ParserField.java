package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ParserField {
    private final int flags;
    private final int nameIndex;
    private final int typeIndex;

    public int getFlags() {
        return flags;
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public int getTypeIndex() {
        return typeIndex;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public ParserField(int flags, int nameIndex, int typeIndex, Attribute[] attributes) {
        this.flags = flags;
        this.nameIndex = nameIndex;
        this.typeIndex = typeIndex;
        this.attributes = attributes;
    }
}
