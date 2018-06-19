package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.Utf8Constant;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public class FieldInfo {

    public Klass getDeclaringClass() {
        return null;
    }

    public Utf8Constant getName() {
        throw EspressoLanguage.unimplemented();
    }

    public TypeDescriptor getType() {
        throw EspressoLanguage.unimplemented();
    }
}
