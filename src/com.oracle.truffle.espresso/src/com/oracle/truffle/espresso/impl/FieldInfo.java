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
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.descriptors.TypeDescriptor;

import java.lang.reflect.Modifier;

/**
 * Represents a resolved Espresso field. FieldInfo instances can be safely compared using ==.
 */
public class FieldInfo implements ModifiersProvider {

    public static final FieldInfo[] EMPTY_ARRAY = new FieldInfo[0];

    private final Klass holder;
    private final TypeDescriptor typeDescriptor;
    private final String name;
    private final int offset;
    private final short slot;
    private Attribute runtimeVisibleAnnotations;

    @CompilerDirectives.CompilationFinal private Klass type;

    /**
     * This value contains all flags as stored in the VM including internal ones.
     */
    private final int modifiers;

    FieldInfo(Klass holder, String name, TypeDescriptor typeDescriptor, long offset, int modifiers, int slot, Attribute runtimeVisibleAnnotations) {
        this.holder = holder;
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.slot = (short) slot;
        this.offset = (int) offset;
        this.modifiers = modifiers;
        this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
        assert this.slot == slot;
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

    public int getSlot() {
        return slot;
    }

    public TypeDescriptor getTypeDescriptor() {
        return typeDescriptor;
    }

    public int offset() {
        return offset;
    }

    public boolean isInternal() {
        // No internal fields.
        return false;
    }

    @Override
    public String toString() {
        return "EspressoField<" + getDeclaringClass() + "." + getName() + ">";
    }

    public int getFlags() {
        return modifiers;
    }

    public Attribute getRuntimeVisibleAnnotations() {
        return runtimeVisibleAnnotations;
    }

    public static class Builder implements BuilderBase<FieldInfo> {
        private Klass declaringClass;
        private String name;
        private TypeDescriptor type;
        private long offset;
        private int modifiers;
        private int slot;
        private Attribute runtimeVisibleAnnotations;

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

        public Builder setSlot(int slot) {
            this.slot = slot;
            return this;
        }

        public Builder setRuntimeVisibleAnnotations(Attribute runtimeVisibleAnnotations) {
            this.runtimeVisibleAnnotations = runtimeVisibleAnnotations;
            return this;
        }

        @Override
        public FieldInfo build() {
            return new FieldInfo(declaringClass, name, type, offset, modifiers, slot, runtimeVisibleAnnotations);
        }

        public boolean isStatic() {
            return Modifier.isStatic(modifiers);
        }
    }
}
