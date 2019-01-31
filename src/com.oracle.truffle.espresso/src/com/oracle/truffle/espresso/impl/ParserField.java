package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
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

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public ParserField(int flags, ByteString<Name> name, ByteString<Type> type, Attribute[] attributes) {
        this.flags = flags;
        this.name = name;
        this.type = type;
        this.attributes = attributes;
    }
}
