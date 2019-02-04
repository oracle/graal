package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.Attributes;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.ByteString;
import com.oracle.truffle.espresso.descriptors.ByteString.Name;
import com.oracle.truffle.espresso.descriptors.ByteString.Type;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class ParserKlass {

    private final ByteString<Type> type;
    private final ByteString<Type> superKlass;
    @CompilationFinal(dimensions = 1) //
    private final ByteString<Type>[] superInterfaces;

    private final int flags;
    // private final int thisKlassIndex; // ClassConstant
// private final int superKlassIndex; // unresolved
//
// @CompilationFinal(dimensions = 1) //
// private final int[] superInterfacesIndices; // unresolved

    @CompilationFinal(dimensions = 1) //
    private final ParserMethod[] methods; // name + signature + attributes

    @CompilationFinal(dimensions = 1) //
    private final ParserField[] fields; // name + type + attributes

    private final Attributes attributes;

    public int getFlags() {
        return flags;
    }

    public ByteString<Type> getType() {
        return type;
    }

    public ByteString<Type> getSuperKlass() {
        return superKlass;
    }

    public ByteString<Type>[] getSuperInterfaces() {
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
                    ByteString<Type> type,
                    ByteString<Type> superKlass,
                    final ByteString<Type>[] superInterfaces,
                    final ParserMethod[] methods,
                    final ParserField[] fields,
                    Attribute[] attributes) {
        this.pool = pool;
        this.flags = flags;
        this.type = type;
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.methods = methods;
        this.fields = fields;
        this.attributes = new Attributes(attributes);
    }

    Attribute getAttribute(ByteString<Name> name) {
        return attributes.get(name);
    }
}
