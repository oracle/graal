/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;

/**
 * Immutable raw representation of classes in Espresso, this is the output of the parser, super
 * klass/interfaces are not resolved.
 */
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

    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    /**
     * Unresolved constant pool, only trivial entries (with no resolution involved) are computed.
     */
    private final ParserConstantPool pool;

    private final char majorVersion;
    private final char minorVersion;

    private final int thisKlassIndex;
    private final long hiddenKlassId;

    public ParserKlass(ParserConstantPool pool,
                    int flags,
                    Symbol<Name> name,
                    Symbol<Type> type,
                    Symbol<Type> superKlass,
                    Symbol<Type>[] superInterfaces,
                    ParserMethod[] methods,
                    ParserField[] fields,
                    Attribute[] attributes,
                    int thisKlassIndex,
                    int majorVersion,
                    int minorVersion,
                    long hiddenKlassId) {
        assert majorVersion == (char) majorVersion;
        assert minorVersion == (char) minorVersion;
        this.pool = pool;
        this.flags = flags;
        this.name = name;
        this.type = type;
        this.superKlass = superKlass;
        this.superInterfaces = superInterfaces;
        this.methods = methods;
        this.fields = fields;
        this.attributes = attributes;
        this.thisKlassIndex = thisKlassIndex;
        this.majorVersion = (char) majorVersion;
        this.minorVersion = (char) minorVersion;
        this.hiddenKlassId = hiddenKlassId;
    }

    /**
     * Returns class flags, it includes VM internal flags that should be filtered-out.
     */
    public int getFlags() {
        return flags;
    }

    /**
     * The class name.
     *
     * @return class name
     */
    public Symbol<Name> getName() {
        return name;
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

    public ParserConstantPool getConstantPool() {
        return pool;
    }

    public ParserMethod[] getMethods() {
        return methods;
    }

    public ParserField[] getFields() {
        return fields;
    }

    public Attribute getAttribute(Symbol<Name> attributeName) {
        for (Attribute attribute : attributes) {
            if (attributeName.equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    public int getThisKlassIndex() {
        return thisKlassIndex;
    }

    public long getHiddenKlassId() {
        return hiddenKlassId;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    @Override
    public String toString() {
        return "ParserKlass<" + getType() + ">";
    }
}
