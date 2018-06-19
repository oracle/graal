package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.Utf8Constant;
import com.oracle.truffle.espresso.types.SignatureDescriptor;

public class MethodInfo {

    public Klass getDeclaringClass() {
        throw EspressoLanguage.unimplemented();
    }

    public Utf8Constant getName() {
        throw EspressoLanguage.unimplemented();
    }

    public SignatureDescriptor getSignature() {
        throw EspressoLanguage.unimplemented();
    }
}
