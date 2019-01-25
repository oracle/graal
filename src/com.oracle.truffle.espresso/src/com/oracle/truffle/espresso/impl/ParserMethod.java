package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.CodeAttribute;
import com.oracle.truffle.espresso.runtime.Attribute;

// Unresolved unlinked.
public final class ParserMethod {

    private final int flags;
    private final int nameIndex;
    private final int signatureIndex;
    private final CodeAttribute code;

    public int getFlags() {
        return flags;
    }

    public CodeAttribute getCode() {
        return code;
    }

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    // Shared quickening recipes.
    // Stores BC + arguments in compact form.
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
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

    public Attribute[] getAttributes() {
        return attributes;
    }

    public long[] getRecipes() {
        return recipes;
    }

    public ParserMethod(int flags, int nameIndex, int signatureIndex, Attribute[] attributes) {
        this.flags = flags;
        this.nameIndex = nameIndex;
        this.signatureIndex = signatureIndex;
        this.attributes = attributes;

        for (Attribute attr : attributes) {
            if (attr.getName().equals("Code")) {
                this.code = (CodeAttribute) attr;
                return ;
            }
        }
        this.code = null; // none
    }
}
