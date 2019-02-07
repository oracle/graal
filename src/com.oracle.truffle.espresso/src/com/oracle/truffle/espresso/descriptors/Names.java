package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;

public final class Names {
    private final Symbols symbols;

    public Names(Symbols symbols) {
        this.symbols = symbols;
    }

    public final Symbol<Name> lookup(ByteSequence bytes) {
        return symbols.lookup(bytes);
    }

    public final Symbol<Name> lookup(String name) {
        return lookup(ByteSequence.create(name));
    }

    public final Symbol<Name> getOrCreate(String name) {
        return symbols.symbolify(ByteSequence.create(name));
    }

//    public final Symbol<Name> symbolify(ByteSequence bytes) {
//        return symbols.symbolify(bytes);
//    }

}
