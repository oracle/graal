package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.object.Shape;

import java.util.Arrays;

// Structural shareable klass (superklass in superinterfaces resolved and linked)
// contains shape, field locations.
// Klass shape, vtable and field locations can be computed at the structural level.
public final class LinkedKlass {

    private final ParserKlass parserKlass;

    // Linked structural references.
    private final LinkedKlass superKlass;

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private final LinkedKlass[] interfaces;

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private final LinkedMethod[] methods; // v-table computed

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private final LinkedField[] fields; // field offsets and locations computed


    Shape shape;

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

    public int getFlags() {
        return parserKlass.getFlags();
    }

    public int getName() {
        // return parserKlass.getConstantPool();
    }
}
