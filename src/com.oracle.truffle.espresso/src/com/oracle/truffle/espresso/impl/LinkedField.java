package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;

public final class LinkedField {
    ParserField getParserField() {
        return parserField;
    }

    private final ParserField parserField;
    private final LinkedKlass holderLinkedKlass;

    protected ConstantPool getConstantPool() {
        return holderLinkedKlass.getConstantPool();
    }

    private final int slot; // already computed here

    public LinkedField(ParserField parserField, LinkedKlass holderLinkedKlass, int slot) {
        this.parserField = parserField;
        this.holderLinkedKlass = holderLinkedKlass;
        this.slot = slot;
    }

    public Symbol<Type> getType() {
        return parserField.getType();
    }

    public Symbol<Name> getName() {
        return parserField.getName();
    }

    public int getSlot() {
        return slot;
    }

    public int getFlags() {
        return parserField.getFlags();
    }
}
