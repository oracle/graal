/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************/

package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public final class Klass {

    private final TypeDescriptor name;
    private final boolean primitive;
    private final boolean array;
    private final boolean iface;
    private final ConstantPool pool = null;

    /**
     * Instance and static variable information, starts with 6-tuples of shorts
     * [access, name index, sig index, initval index, low_offset, high_offset]
    * for all fields, followed by the generic signature data at the end of
    * the array. Only fields with generic signature attributes have the generic
    * signature data set in the array. The fields array looks like following:
    * //@formatter:off
    *
    * f1: [access, nameANDtYPE index, initial value index, low_offset, high_offset]
    * f2: [access, name index, sig index, initial value index, low_offset, high_offset]
    *      ...
    * fn: [access, name index, sig index, initial value index, low_offset, high_offset]
    *     [generic signature index]
    *     [generic signature index]
    *     ...
    * //@formatter:on
    */
    @CompilationFinal(dimensions = 1) private final short[] fields;
    private final Klass superclass;
    private final Klass[] interfaces;

    @CompilationFinal private boolean initialized;
    @CompilationFinal private int modifiers;

    @CompilationFinal private Shape shape;
    @CompilationFinal private DynamicObject javaLangClassObject;
    @CompilationFinal private DynamicObject staticClassObject;
    @CompilationFinal private Klass componentType;

    public static Klass create(TypeDescriptor name, Klass superclass, Klass[] interfaces, short[] fields, boolean isInterface, int modifiers) {
        CompilerAsserts.neverPartOfCompilation();
        return new Klass(name, superclass, interfaces, fields, false, isInterface, modifiers);
    }

    /**
     * The private constructor. Use {@link #create} instead from outside.
     *
     * @param name
     * @param superclass
     * @param interfaces
     * @param fields
     * @param primitive
     * @param isInterface
     * @param modifiers
     */
    private Klass(TypeDescriptor name, Klass superclass, Klass[] interfaces, short[] fields, boolean primitive, boolean isInterface, int modifiers) {
        this.name = name;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.fields = fields;
        this.primitive = primitive;
        this.iface = isInterface;
        this.array = name.toString().charAt(0) == '[';
        this.modifiers = modifiers;
        this.initialized = false;
    }

    public TypeDescriptor getName() {
        return name;
    }

    public boolean isInterface() {
        return iface;
    }

    public boolean isPrimitive() {
        return primitive;
    }

    public boolean isArray() {
        return array;
    }

    public Klass getSuperclass() {
        return superclass;
    }

    public Klass[] getInterfaces() {
        return interfaces;
    }

    public int getModifiers() {
        return modifiers;
    }

    public Field[] getDeclaredFields() {
        return null;
    }

    @Override
    public String toString() {
        return "class " + name;
    }

    /**
     * Given the field name and type, finds the {@link Field} that is either a member to this class
     * or to a superclass.
     *
     * @param fieldName
     * @param fieldType
     * @return the field or null if not found
     */
    public Field findField(String fieldName, String fieldType) {
        CompilerAsserts.neverPartOfCompilation();
        // TODO: might want to use a map here for better performance.
        if (superclass == null) {
            return null;
        } else {
            return superclass.findField(fieldName, fieldType);
        }
    }

    /**
     * The interpreter representation of {@link java.lang.reflect.Field}.
     */
    public static class Field {
        private String name;
        private String type;
        private int modifiers;
        private Klass declaringClass;

        public Field(String name, String type, int modifiers) {
            this.name = name;
            this.type = type;
            this.modifiers = modifiers;
            declaringClass = null;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public int getModifiers() {
            return modifiers;
        }

        public Klass getDeclaringClass() {
            return declaringClass;
        }

        private void setDeclaringClass(Klass clazz) {
            declaringClass = clazz;
        }

    }
}
