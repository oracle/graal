package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Signature;

public final class LinkedMethod {
    private final ParserMethod parserMethod;
    private final LinkedKlass declaringLinkedKlass;
    private final ConstantPool pool;

    // int vtableSlot; // not all methods have vtable entry
    protected int getFlags() {
        return parserMethod.getFlags();
    }

    ConstantPool getConstantPool() {
        return pool;
    }

    protected ByteString<Signature> getRawSignature() {
        return getConstantPool().utf8At(parserMethod.getSignatureIndex(), "signature");
    }

    protected ByteString<Name> getName() {
        return getConstantPool().utf8At(parserMethod.getNameIndex(), "name");
    }

    LinkedMethod(ParserMethod parserMethod, LinkedKlass declaringLinkedKlass) {
        this(parserMethod, declaringLinkedKlass, declaringLinkedKlass.getConstantPool());
    }

    LinkedMethod(ParserMethod parserMethod, LinkedKlass declaringLinkedKlass, ConstantPool pool) {
        this.parserMethod = parserMethod;
        this.declaringLinkedKlass = declaringLinkedKlass;
        this.pool = pool;
    }
}
