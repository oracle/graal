package com.oracle.truffle.espresso.classfile;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class Attributes {
    private static final EconomicMap<ByteString<Name>, Attribute> EMPTY = EconomicMap.create(0);

    private final EconomicMap<ByteString<Name>, Attribute> map;

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

    public Attribute get(ByteString<Name> name) {
        return map.get(name);
    }
}
