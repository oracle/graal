package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Signature;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class LinkedMethod {
    private final ParserMethod parserMethod;
    private final LinkedKlass declaringLinkedKlass;

    // int vtableSlot; // not all methods have vtable entry
    protected int getFlags() {
        return parserMethod.getFlags();
    }

    ConstantPool getConstantPool() {
        return declaringLinkedKlass.getConstantPool();
    }

    protected ByteString<Signature> getRawSignature() {
        return getConstantPool().utf8At(parserMethod.getSignatureIndex(), "signature");
    }

    ParserMethod getParserMethod() {
        return parserMethod;
    }

    protected ByteString<Name> getName() {
        return getConstantPool().utf8At(parserMethod.getNameIndex(), "name");
    }

    LinkedMethod(ParserMethod parserMethod, LinkedKlass declaringLinkedKlass) {
        this.parserMethod = parserMethod;
        this.declaringLinkedKlass = declaringLinkedKlass;
    }

    public Attribute getAttribute(ByteString<Name> name) {
        return parserMethod.getAttribute(name);
    }
}
