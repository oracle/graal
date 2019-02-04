package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.Attributes;
import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

// Unresolved unlinked.
public final class ParserMethod {

    public static final ParserMethod[] EMPTY_ARRAY = new ParserMethod[0];

    private final int flags;
    private final int nameIndex;
    private final int signatureIndex;

    public int getFlags() {
        return flags;
    }

    private final Attributes attributes;

    // Shared quickening recipes.
    // Stores BC + arguments in compact form.
    @CompilationFinal(dimensions = 1) //
    private long[] recipes;

    public static ParserMethod create(int flags, int nameIndex, int signatureIndex, Attribute[] attributes) {
        return new ParserMethod(flags, nameIndex, signatureIndex, attributes);
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public int getSignatureIndex() {
        return signatureIndex;
    }

    public Attribute getAttribute(ByteString<Name> name) {
        return attributes.get(name);
    }

    public long[] getRecipes() {
        return recipes;
    }

    public ParserMethod(int flags, int nameIndex, int signatureIndex, Attribute[] attributes) {
        this.flags = flags;
        this.nameIndex = nameIndex;
        this.signatureIndex = signatureIndex;
        this.attributes = new Attributes(attributes);
    }
}
