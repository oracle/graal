package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.runtime.Attribute;

// Straight out of the parser, loading.
// superKlass and superInterfaces not resolved.
public final class ParserKlass {

    private final int flags;
    private final int thisKlassIndex; // ClassConstant
    private final int superKlassIndex; // unresolved

    @CompilationFinal(dimensions = 1) //
    private final int[] superInterfacesIndices; // unresolved

    @CompilationFinal(dimensions = 1) //
    private final ParserMethod[] methods; // name + signature + attributes

    @CompilationFinal(dimensions = 1) //
    private final ParserField[] fields; // name + type + attributes

    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public int getFlags() {
        return flags;
    }

    public int getThisKlassIndex() {
        return getThisKlassIndex();
    }

    public int getSuperKlassIndex() {
        return superKlassIndex;
    }

    public int[] getSuperInterfacesIndices() {
        return superInterfacesIndices;
    }

    public ParserMethod[] getMethods() {
        return methods;
    }

    public ParserField[] getFields() {
        return fields;
    }

    public ConstantPool getConstantPool() {
        return pool;
    }

    /**
     * Unresolved constant pool, only the trivial entries (no resolution involved) could be
     * resolved.
     */
    private final ConstantPool pool;

    public ParserKlass(ConstantPool pool, int flags, int thisKlassIndex, int superKlassIndex, int[] superInterfacesIndices, ParserMethod[] methods, ParserField[] fields, Attribute[] attributes) {
        this.pool = pool;
        this.flags = flags;
        this.thisKlassIndex = thisKlassIndex;
        this.superKlassIndex = superKlassIndex;
        this.superInterfacesIndices = superInterfacesIndices;
        this.methods = methods;
        this.fields = fields;
        this.attributes = attributes;
    }
}
