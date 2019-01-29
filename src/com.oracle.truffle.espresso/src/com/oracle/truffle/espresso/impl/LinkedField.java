package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Type;

public final class LinkedField {
    private final ParserField parserField;
    private final LinkedKlass holderLinkedKlass;

    protected ConstantPool getConstantPool() {
        return holderLinkedKlass.getConstantPool();
    }

    int slot; // already computed here
    // Location location;

    public LinkedField(ParserField parserField, LinkedKlass holderLinkedKlass) {
        this.parserField = parserField;
        this.holderLinkedKlass = holderLinkedKlass;
    }

    public ByteString<Type> getType() {
        return getConstantPool().utf8At(parserField.getTypeIndex(), "type");
    }

    public ByteString<Name> getName() {
        return getConstantPool().utf8At(parserField.getNameIndex(), "name");
    }
}
