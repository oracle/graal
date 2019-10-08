package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.meta.ModifiersProvider;

public abstract class Member<T extends Descriptor> implements ModifiersProvider {

    protected final Symbol<Name> name;
    protected final Symbol<T> descriptor;

    protected Member(Symbol<T> descriptor, Symbol<Name> name) {
        this.name = name;
        this.descriptor = descriptor;
    }

    public Symbol<Name> getName() {
        return name;
    }

    public final Symbol<T> getDescriptor() {
        return descriptor;
    }

    public abstract ObjectKlass getDeclaringKlass();
}
