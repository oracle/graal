package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.Attributes;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ParserKlass {

    private final Symbol<Name> name;
    private final Symbol<Type> type;
    private final Symbol<Type> superKlass;

    @CompilationFinal(dimensions = 1) //
    private final Symbol<Type>[] superInterfaces;

    private final int flags;

    @CompilationFinal(dimensions = 1) //
    private final ParserMethod[] methods; // name + signature + attributes

    @CompilationFinal(dimensions = 1) //
    private final ParserField[] fields; // name + type + attributes

    private final Attributes attributes;

    public int getFlags() {
        return flags;
    }

    public Symbol<Type> getType() {
        return type;
    }

    public Symbol<Type> getSuperKlass() {
        return superKlass;
    }

    public Symbol<Type>[] getSuperInterfaces() {
        return superInterfaces;
    }

    ParserMethod[] getMethods() {
        return methods;
    }

    ParserField[] getFields() {
        return fields;
    }

    public ConstantPool getConstantPool() {
        return pool;
    }

    /**
     * Unresolved constant pool, only trivial entries (with no resolution involved) are computed.
     */
    private final ConstantPool pool;

    public ParserKlass(ConstantPool pool,
                    int flags,
                    Symbol<Name> name,
                    Symbol<Type> type,
                    Symbol<Type> superKlass,
                    final Symbol<Type>[] superInterfaces,
                    final ParserMethod[] methods,
                    final ParserField[] fields,
                    Attribute[] attributes) {
        this.pool = pool;
        this.flags = flags;
        this.name = name;
        this.type = type;
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.methods = methods;
        this.fields = fields;
        this.attributes = new Attributes(attributes);
    }

    Attribute getAttribute(Symbol<Name> name) {
        return attributes.get(name);
    }

    public Symbol<Name> getName() {
        return name;
    }
}
