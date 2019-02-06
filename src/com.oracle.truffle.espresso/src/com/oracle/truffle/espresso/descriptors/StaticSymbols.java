package com.oracle.truffle.espresso.descriptors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Global symbols for Espresso.
 * 
 * <p>
 * To be populated mostly in static initializers, always before the first runtime symbol table is
 * spawned.
 *
 * Once the first runtime symbol table is created, this table will be frozen and no more symbols can
 * be added. The frozen symbols will be used as seed for runtime symbol tables.
 */
public final class StaticSymbols {

    private Map<SymbolKey, Symbol<?>> symbols = new ConcurrentHashMap<>();
    private static boolean frozen = false;

    public static Symbol<Symbol.Name> putName(String name) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        throw EspressoError.unimplemented();
    }

    public static Symbol<Type> putType(String name) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        throw EspressoError.unimplemented();
    }

    public static Symbol<Type> putType(Class<?> clazz) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        throw EspressoError.unimplemented();
    }

    @SafeVarargs
    public static Symbol<Signature> putSignature(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        throw EspressoError.unimplemented();
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public HashMap<SymbolKey, Symbol<?>> freeze() {
        symbols = Collections.unmodifiableMap(symbols);
    }
}
