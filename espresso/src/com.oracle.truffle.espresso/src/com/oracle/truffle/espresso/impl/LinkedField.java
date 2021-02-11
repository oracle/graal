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

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.Attribute;

final class LinkedField extends StaticProperty {
    private final ParserField parserField;
    private final int slot;

    LinkedField(ParserField parserField, int slot, int offset) {
        super(offset);
        this.parserField = parserField;
        this.slot = slot;
    }

    public static LinkedField createHidden(Symbol<Name> name, int slot, int offset) {
        return new LinkedField(new ParserField(ParserField.HIDDEN, name, Type.java_lang_Object, null), slot, offset);
    }

    /**
     * The slot is the position in the `fieldTable` of the ObjectKlass.
     */
    public int getSlot() {
        return slot;
    }

    public Symbol<Type> getType() {
        return parserField.getType();
    }

    public Symbol<Name> getName() {
        return parserField.getName();
    }

    public int getFlags() {
        return parserField.getFlags();
    }

    public JavaKind getKind() {
        return parserField.getKind();
    }

    public Attribute getAttribute(Symbol<Name> name) {
        for (Attribute a : parserField.getAttributes()) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    public boolean isHidden() {
        return parserField.isHidden();
    }

    ParserField getParserField() {
        return parserField;
    }
}
