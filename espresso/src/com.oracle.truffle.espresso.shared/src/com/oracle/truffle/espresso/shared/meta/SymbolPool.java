package com.oracle.truffle.espresso.shared.meta;

import com.oracle.truffle.espresso.classfile.descriptors.Names;
import com.oracle.truffle.espresso.classfile.descriptors.Signatures;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.classfile.descriptors.Types;

public interface SymbolPool {
    Names getNames();

    Types getTypes();

    Signatures getSignatures();

    default Symbol<Type>[] getParsedSignature(MethodAccess<?, ?, ?> method) {
        return getSignatures().parsed(method.getSymbolicSignature());
    }
}