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

import static com.oracle.truffle.espresso.classfile.Constants.FIELD_ID_OBFUSCATE;
import static com.oracle.truffle.espresso.classfile.Constants.FIELD_ID_TYPE;

import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.ParserField;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

final class LinkedField extends StaticProperty {

    enum IdMode {
        REGULAR,
        WITH_TYPE,
        OBFUSCATED,
    }

    private final ParserField parserField;
    private final int slot;

    LinkedField(ParserField parserField, int slot, IdMode mode) {
        this.parserField = maybeCorrectParserField(parserField, mode);
        this.slot = slot;
    }

    private static ParserField maybeCorrectParserField(ParserField parserField, IdMode mode) {
        switch (mode) {
            case REGULAR:
                return parserField;
            case WITH_TYPE:
                return parserField.withFlags(FIELD_ID_TYPE);
            case OBFUSCATED:
                return parserField.withFlags(FIELD_ID_OBFUSCATE);
        }
        throw EspressoError.shouldNotReachHere();
    }

    /**
     * This method is required by the Static Object Model. In Espresso we should rather call
     * `getName()` and use Symbols.
     */
    @Override
    protected String getId() {
        Symbol<Name> name = getName();
        switch (idMode()) {
            case WITH_TYPE:
                // Field name and type.
                return idFromNameAndType(name, getType());
            case OBFUSCATED:
                // "{primitive, hidden, reference}Field{slot}"
                return (getKind().isPrimitive() ? "primitive" : (isHidden() ? "hidden" : "reference")) + "Field" + slot;
            case REGULAR:
                // Regular name
                return name.toString();
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private IdMode idMode() {
        int flags = getFlags();
        if ((flags & FIELD_ID_TYPE) == FIELD_ID_TYPE) {
            // Field name and type.
            return IdMode.WITH_TYPE;
        } else if ((flags & FIELD_ID_OBFUSCATE) == FIELD_ID_OBFUSCATE) {
            return IdMode.OBFUSCATED;
        } else {
            return IdMode.REGULAR;
        }
    }

    static String idFromNameAndType(Symbol<Name> name, ByteSequence t) {
        // Strip 'L' and ';' from the type symbol.
        int arrayDims = TypeSymbols.getArrayDimensions(t);
        if (arrayDims > 0) {
            // Component string
            StringBuilder typeString = new StringBuilder(idFromNameAndType(name, t.subSequence(arrayDims)));
            typeString.append('_');
            // Append a number of ']'
            while (arrayDims > 0) {
                typeString.append(']');
                arrayDims--;
            }
            return typeString.toString();
        }
        String typeString = t.toString();
        if (TypeSymbols.isReference(t)) {
            typeString = typeString.substring(1, typeString.length() - 1);
            typeString = typeString.replace('/', '_');
        }
        return name.toString() + "_" + typeString;
    }

    public Symbol<Name> getName() {
        // no need to go through getParserField(), since name
        // can't change on redefinition on a linked field
        return parserField.getName();
    }

    /**
     * The slot is the position in the `fieldTable` of the ObjectKlass.
     */
    public int getSlot() {
        return slot;
    }

    public Symbol<Type> getType() {
        return getParserField().getType();
    }

    public int getFlags() {
        return getParserField().getFlags();
    }

    public JavaKind getKind() {
        // no need to go through getParserField(), since kind
        // can't change on redefinition on a linked field
        return parserField.getKind();
    }

    public Attribute getAttribute(Symbol<Name> name) {
        for (Attribute a : getParserField().getAttributes()) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    public boolean isHidden() {
        return getParserField().isHidden();
    }

    ParserField getParserField() {
        return parserField;
    }

    public static Class<?> getPropertyType(ParserField parserField) {
        Symbol<Type> type = parserField.getType();
        if (type.length() == 1) {
            char ch = (char) type.byteAt(0);
            return switch (ch) {
                case 'Z' -> boolean.class;
                case 'C' -> char.class;
                case 'F' -> float.class;
                case 'D' -> double.class;
                case 'B' -> byte.class;
                case 'S' -> short.class;
                case 'I' -> int.class;
                case 'J' -> long.class;
                default -> throw EspressoError.shouldNotReachHere("unknown primitive or void type character: " + ch);
            };
        }
        return parserField.isHidden() ? Object.class : StaticObject.class;
    }
}
