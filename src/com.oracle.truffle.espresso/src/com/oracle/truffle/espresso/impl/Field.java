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

import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Represents a resolved Espresso field.
 */
public final class Field implements ModifiersProvider {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    private final LinkedField linkedField;
    private final ObjectKlass holder;
    private final Symbol<Type> type;
    private final Symbol<Name> name;
    private volatile Klass typeKlassCache;

    public Symbol<Type> getType() {
        return type;
    }

    public Field(LinkedField linkedField, ObjectKlass holder) {
        this.linkedField = linkedField;
        this.holder = holder;
        this.type = linkedField.getType();
        this.name = linkedField.getName();
    }

    public JavaKind getKind() {
        return Types.getJavaKind(getType());
    }

    public int getModifiers() {
        return linkedField.getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS;
    }

    public ObjectKlass getDeclaringKlass() {
        return holder;
    }

    public int getSlot() {
        return linkedField.getSlot();
    }

    @Override
    public String toString() {
        return "EspressoField<" + getDeclaringKlass() + "." + getName() + " -> " + getType() + ">";
    }

    public Object get(StaticObject self) {
        assert getDeclaringKlass().isAssignableFrom(self.getKlass());
        // @formatter:off
        // Checkstyle: stop
        switch (getKind()) {
            case Boolean : return InterpreterToVM.getFieldBoolean(self, this);
            case Byte    : return InterpreterToVM.getFieldByte(self, this);
            case Short   : return InterpreterToVM.getFieldShort(self, this);
            case Char    : return InterpreterToVM.getFieldChar(self, this);
            case Int     : return InterpreterToVM.getFieldInt(self, this);
            case Float   : return InterpreterToVM.getFieldFloat(self, this);
            case Long    : return InterpreterToVM.getFieldLong(self, this);
            case Double  : return InterpreterToVM.getFieldDouble(self, this);
            case Object  : return InterpreterToVM.getFieldObject(self, this);
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public void set(StaticObject self, Object value) {
        assert value != null;
        assert getDeclaringKlass().isAssignableFrom(self.getKlass());
        // @formatter:off
        // Checkstyle: stop
        switch (getKind()) {
            case Boolean : InterpreterToVM.setFieldBoolean((boolean) value, self, this); break;
            case Byte    : InterpreterToVM.setFieldByte((byte) value, self, this);       break;
            case Short   : InterpreterToVM.setFieldShort((short) value, self, this);     break;
            case Char    : InterpreterToVM.setFieldChar((char) value, self, this);       break;
            case Int     : InterpreterToVM.setFieldInt((int) value, self, this);         break;
            case Float   : InterpreterToVM.setFieldFloat((float) value, self, this);     break;
            case Long    : InterpreterToVM.setFieldLong((long) value, self, this);       break;
            case Double  : InterpreterToVM.setFieldDouble((double) value, self, this);   break;
            case Object  : InterpreterToVM.setFieldObject((StaticObject) value, self, this); break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public Symbol<Name> getName() {
        return name;
    }

    public final Klass resolveTypeKlass() {
        Klass tk = typeKlassCache;
        if (tk == null) {
            synchronized (this) {
                tk = typeKlassCache;
                if (tk == null) {
                    tk = getDeclaringKlass().getRegistries().loadKlass(getType(), getDeclaringKlass().getDefiningClassLoader());
                    typeKlassCache = tk;
                }
            }
        }
        return typeKlassCache;
    }

    public Attribute getAttribute(Symbol<Name> attrName) {
        return linkedField.getAttribute(attrName);
    }
}
