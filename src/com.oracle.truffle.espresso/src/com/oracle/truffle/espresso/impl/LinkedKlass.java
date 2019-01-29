package com.oracle.truffle.espresso.impl;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.impl.ByteString.Type;

// Structural shareable klass (superklass in superinterfaces resolved and linked)
// contains shape, field locations.
// Klass shape, vtable and field locations can be computed at the structural level.
public final class LinkedKlass {

    private final ParserKlass parserKlass;

    // Linked structural references.
    private final LinkedKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final LinkedKlass[] interfaces;

    @CompilationFinal(dimensions = 1) //
    private final LinkedMethod[] methods; // v-table computed

    @CompilationFinal(dimensions = 1) //
    private final LinkedField[] fields; // field offsets and locations computed

    // Shape shape;

    public LinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces, LinkedMethod[] methods, LinkedField[] fields) {
        this.parserKlass = parserKlass;
        this.superKlass = superKlass;
        this.interfaces = interfaces;
        this.methods = methods;
        this.fields = fields;
    }

    public boolean equals(LinkedKlass other) {
        return parserKlass == other.parserKlass &&
                        superKlass == other.superKlass &&
                        /* reference equals */ Arrays.equals(interfaces, other.interfaces);
    }

    int getFlags() {
        return parserKlass.getFlags();
    }

    ConstantPool getConstantPool() {
        return parserKlass.getConstantPool();
    }

    ByteString<Type> getType() {
        ConstantPool pool = getConstantPool();
        return pool.classAt(parserKlass.getThisKlassIndex(), "this").getType(pool);
    }
}
