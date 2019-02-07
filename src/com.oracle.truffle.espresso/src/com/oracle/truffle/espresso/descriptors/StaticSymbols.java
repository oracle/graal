package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Global symbols for Espresso.
 * 
 * <p>
 * To be populated in static initializers, always before the first runtime symbol table is spawned.
 *
 * Once the first runtime symbol table is created, this table is frozen and no more symbols can be
 * added. The frozen symbols are used as seed to create new runtime symbol tables.
 */
public final class StaticSymbols {

    private StaticSymbols() {
        /* no instances */
    }

    private static boolean frozen = false;
    private static final Symbols symbols = new Symbols();

    public static Symbol<Name> putName(String name) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        EspressoError.guarantee(!name.isEmpty(), "empty name");
        return symbols.symbolify(ByteSequence.create(name));
    }

    public static Symbol<Type> putType(String internalName) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(ByteSequence.create(Types.checkType(internalName)));
    }

    public static Symbol<Type> putType(Class<?> clazz) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(ByteSequence.create(Types.internalFromClassName(clazz.getName())));
    }

    @SafeVarargs
    public static Symbol<Signature> putSignature(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(ByteSequence.wrap(Signatures.buildSignatureBytes(returnType, parameterTypes)));
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static Symbols freeze() {
        frozen = true;
        return symbols;
    }
}
