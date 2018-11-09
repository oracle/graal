/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.types.TypeDescriptor;

/**
 * Represents a resolved Espresso field. FieldInfo instances can be safely compared using ==.
 */
public class FieldInfo implements ModifiersProvider {

    public static final FieldInfo[] EMPTY_ARRAY = new FieldInfo[0];

    private final Klass holder;
    private final TypeDescriptor typeDescriptor;
    private final String name;
    private final int offset;
    private final short index;

    @CompilerDirectives.CompilationFinal private Klass type;

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int modifiers;

    FieldInfo(Klass holder, String name, TypeDescriptor typeDescriptor, long offset, int modifiers, int index) {
        this.holder = holder;
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.index = (short) index;
        this.offset = (int) offset;
        this.modifiers = modifiers;
        assert this.index == index;
        assert offset != -1;
        assert offset == (int) offset : "offset larger than int";
    }

    public JavaKind getKind() {
        return typeDescriptor.toKind();
    }

    public Klass getType() {
        if (type == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            type = getDeclaringClass().getContext().getRegistries().resolve(getTypeDescriptor(), getDeclaringClass().getClassLoader());
        }
        return type;
    }

    public int getModifiers() {
        return modifiers & ModifiersProvider.jvmFieldModifiers();
    }

    public Klass getDeclaringClass() {
        return holder;
    }

    public String getName() {
        return name;
    }

    public TypeDescriptor getTypeDescriptor() {
        return typeDescriptor;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "EspressoField<" + getDeclaringClass() + "." + getName() + ">";
    }

    public int getFlags() {
        return modifiers;
    }

    public static class Builder implements BuilderBase<FieldInfo> {
        private Klass declaringClass;
        private String name;
        private TypeDescriptor type;
        private long offset;
        private int modifiers;
        private int index;

        public Builder setDeclaringClass(Klass declaringClass) {
            this.declaringClass = declaringClass;
            return this;
        }

        public Builder setType(TypeDescriptor type) {
            this.type = type;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setOffset(long offset) {
            this.offset = offset;
            return this;
        }

        public Builder setModifiers(int modifiers) {
            this.modifiers = modifiers;
            return this;
        }

        public Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        @Override
        public FieldInfo build() {
            return new FieldInfo(declaringClass, name, type, offset, modifiers, index);
        }
    }
}
