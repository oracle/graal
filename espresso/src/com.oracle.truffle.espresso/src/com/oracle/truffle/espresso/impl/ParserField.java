/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

import java.lang.reflect.Modifier;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;

public final class ParserField {

    public static final ParserField[] EMPTY_ARRAY = new ParserField[0];
    // re-use the Constants.ACC_FINALIZER flag to mark hidden fields
    public static final int HIDDEN = ACC_FINALIZER;

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int flags;
    private final Symbol<Name> name;
    private final Symbol<Type> type;
    @CompilationFinal(dimensions = 1) //
    private final Attribute[] attributes;

    public ParserField withFlags(int newFlags) {
        return new ParserField(flags | newFlags, name, type, attributes);
    }

    public int getFlags() {
        return flags;
    }

    public Symbol<Name> getName() {
        return name;
    }

    public Symbol<Type> getType() {
        return type;
    }

    public Attribute[] getAttributes() {
        return attributes;
    }

    public ParserField(int flags, Symbol<Name> name, Symbol<Type> type, final Attribute[] attributes) {
        this.flags = flags;
        this.name = name;
        this.type = type;
        this.attributes = attributes;
    }

    public boolean isHidden() {
        return (flags & HIDDEN) != 0;
    }

    public boolean isStatic() {
        return Modifier.isStatic(flags);
    }

    public boolean isFinal() {
        return Modifier.isFinal(flags);
    }

    public JavaKind getKind() {
        return Types.getJavaKind(type);
    }

    public Class<?> getPropertyType() {
        if (type.length() == 1) {
            char ch = (char) type.byteAt(0);
            switch (ch) {
                case 'Z':
                    return boolean.class;
                case 'C':
                    return char.class;
                case 'F':
                    return float.class;
                case 'D':
                    return double.class;
                case 'B':
                    return byte.class;
                case 'S':
                    return short.class;
                case 'I':
                    return int.class;
                case 'J':
                    return long.class;
                default:
                    throw new IllegalArgumentException("unknown primitive or void type character: " + ch);
            }
        }
        return isHidden() ? Object.class : StaticObject.class;
    }
}
