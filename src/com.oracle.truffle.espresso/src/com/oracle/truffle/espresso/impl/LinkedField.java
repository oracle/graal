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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.Attribute;

import java.lang.reflect.Modifier;

public final class LinkedField {
    public static final LinkedField[] EMPTY_ARRAY = new LinkedField[0];

    private final ParserField parserField;
    // TODO(da): do we need to store a reference to the holder linked class?
    private final LinkedKlass holderLinkedKlass;
    private final JavaKind kind;

    @CompilerDirectives.CompilationFinal //
    private int fieldIndex = -1;

    @CompilerDirectives.CompilationFinal //
    private int slot = -1;

    public LinkedField(ParserField parserField, LinkedKlass holderLinkedKlass) {
        this.parserField = parserField;
        this.holderLinkedKlass = holderLinkedKlass;
        this.kind = Types.getJavaKind(getType());
    }

    private LinkedField(ParserField parserField, LinkedKlass holderLinkedKlass, int slot, int index) {
        this(parserField, holderLinkedKlass);
        setSlot(slot);
        setFieldIndex(index);
    }

    static LinkedField createHidden(LinkedKlass holder, int slot, int index, Symbol<Name> name) {
        return new LinkedField(new ParserField(ParserField.HIDDEN, name, Type.java_lang_Object, null), holder, slot, index);
    }

    ParserField getParserField() {
        return parserField;
    }

    protected ConstantPool getConstantPool() {
        return holderLinkedKlass.getConstantPool();
    }

    public Symbol<Type> getType() {
        return parserField.getType();
    }

    public Symbol<Name> getName() {
        return parserField.getName();
    }

    void setSlot(int slot) {
        CompilerAsserts.neverPartOfCompilation();
        this.slot = slot;
    }

    /**
     * The slot is the position in the `fieldTable` of the ObjectKlass.
     */
    public int getSlot() {
        return slot;
    }

    /**
     * The fieldIndex is the actual position in the field array of an actual instance.
     */
    public int getFieldIndex() {
        return fieldIndex;
    }

    void setFieldIndex(int index) {
        CompilerAsserts.neverPartOfCompilation();
        this.fieldIndex = index;
    }

    public int getFlags() {
        return parserField.getFlags();
    }

    public JavaKind getKind() {
        return kind;
    }

    public Attribute getAttribute(Symbol<Name> name) {
        for (Attribute a : parserField.getAttributes()) {
            if (name.equals(a.getName())) {
                return a;
            }
        }
        return null;
    }

    boolean isStatic() {
        return Modifier.isStatic(getParserField().getFlags());
    }

    boolean isHidden() {
        return parserField.isHidden();
    }
}
