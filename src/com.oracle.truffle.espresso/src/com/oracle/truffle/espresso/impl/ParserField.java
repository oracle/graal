package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ParserField {

    public static final ParserField[] EMPTY_ARRAY = new ParserField[0];

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int flags;
    private final ByteString<Name> name;
    private final ByteString<Type> type;
    private final int typeIndex;
    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public int getFlags() {
        return flags;
    }

    public ByteString<Name> getName() {
        return name;
    }

    public ByteString<Type> getType() {
        return type;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    public ParserField(int flags, ByteString<Name> name, ByteString<Type> type, int typeIndex, final Attribute[] attributes) {
        this.flags = flags;
        this.name = name;
        this.type = type;
        // Used to resolve the field on the holder constant pool.
        this.typeIndex = typeIndex;
        this.attributes = attributes;
    }

    public int getTypeIndex() {
        return typeIndex;
    }
}
