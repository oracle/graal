package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class SignatureAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.Signature;

    private final int signatureIndex;

    public SignatureAttribute(Symbol<Name> name, int signatureIndex) {
        super(name, null);
        this.signatureIndex = signatureIndex;
    }

    public int getSignatureIndex() {
        return signatureIndex;
    }
}
