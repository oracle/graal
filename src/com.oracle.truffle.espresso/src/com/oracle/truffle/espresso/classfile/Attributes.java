package com.oracle.truffle.espresso.classfile;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class Attributes {
    private static final EconomicMap<Symbol<Name>, Attribute> EMPTY = EconomicMap.create(0);

    private final EconomicMap<Symbol<Name>, Attribute> map;

    public Attributes(final Attribute[] attributes) {
        if (attributes.length == 0) {
            map = EMPTY;
        } else {
            map = EconomicMap.create(attributes.length);
        }
        for (Attribute a : attributes) {
            map.put(a.getName(), a);
        }
    }

    public Attribute get(Symbol<Name> name) {
        return map.get(name);
    }
}
