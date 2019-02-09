/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
