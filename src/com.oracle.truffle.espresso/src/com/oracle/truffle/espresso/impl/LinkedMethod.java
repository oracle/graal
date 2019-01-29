package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Signature;

public final class LinkedMethod {
    private final ParserMethod parserMethod;
    private final LinkedKlass declaringLinkedKlass;

    // int vtableSlot; // not all methods have vtable entry

    protected int getFlags() {
        return parserMethod.getFlags();
    }

    protected ByteString<Signature> getSignature() {
        return declaringLinkedKlass.getSignatureIndex();
    }

    protected ByteString<Name> getName() {
    }

    LinkedMethod(ParserMethod parserMethod, LinkedKlass declaringLinkedKlass) {
        this.parserMethod = parserMethod;
        this.declaringLinkedKlass = declaringLinkedKlass;
    }
}
