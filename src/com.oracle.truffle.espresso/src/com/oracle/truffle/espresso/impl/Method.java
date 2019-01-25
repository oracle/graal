package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Signature;
import com.oracle.truffle.espresso.mudball.impl.Klass;
import com.oracle.truffle.espresso.descriptors.SignatureDescriptor;

public final class Method {
    public static final Method[] EMPTY_ARRAY = new Method[0];

    private final LinkedMethod linkedMethod;
    private final Klass declaringKlass;
    private final ConstantPool pool;

    // can have a different constant pool than it's declaring class
    public ConstantPool getConstantPool() {
        return pool != null
                ? pool
                : declaringKlass.getConstantPool();
    }

    public Klass getDeclaringKlass() {
        return declaringKlass;
    }

    public ByteString<Name> getName() {
        return getConstantPool().utf8At(linkedMethod.parserMethod.nameIndex);
    }

    public ByteString<Signature> getSignature() {
        return getConstantPool();
    }
}
