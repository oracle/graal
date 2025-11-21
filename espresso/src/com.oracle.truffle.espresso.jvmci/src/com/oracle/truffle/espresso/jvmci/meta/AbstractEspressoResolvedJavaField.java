/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType.DECLARED_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType.TYPE_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.ENUM;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.HIDDEN;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.STABLE_FIELD;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.SYNTHETIC;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.TRANSIENT;
import static java.lang.reflect.Modifier.VOLATILE;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.meta.annotation.AbstractAnnotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public abstract class AbstractEspressoResolvedJavaField extends AbstractAnnotated implements ResolvedJavaField {
    private static final int JVM_FIELDS_MODIFIERS = PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | VOLATILE | TRANSIENT | ENUM | SYNTHETIC;

    private final AbstractEspressoResolvedInstanceType holder;
    private String name;
    private JavaType type;

    protected AbstractEspressoResolvedJavaField(AbstractEspressoResolvedInstanceType holder) {
        this.holder = holder;
    }

    @Override
    public final int getModifiers() {
        return getFlags() & JVM_FIELDS_MODIFIERS;
    }

    protected abstract int getFlags();

    @Override
    public final boolean isInternal() {
        return (getFlags() & HIDDEN) != 0;
    }

    @Override
    public final boolean isSynthetic() {
        return (getFlags() & SYNTHETIC) != 0;
    }

    public final boolean isStable() {
        return (getFlags() & STABLE_FIELD) != 0;
    }

    @Override
    public final String getName() {
        if (name == null) {
            name = getName0();
        }
        return name;
    }

    protected abstract String getName0();

    @Override
    public final JavaType getType() {
        // Pull field into local variable to prevent a race causing
        // a ClassCastException below
        JavaType currentType = type;
        if (currentType == null || currentType instanceof UnresolvedJavaType) {
            // Don't allow unresolved types to hang around forever
            type = getType0((UnresolvedJavaType) currentType);
        }
        return type;
    }

    protected abstract JavaType getType0(UnresolvedJavaType unresolved);

    @Override
    public final AbstractEspressoResolvedInstanceType getDeclaringClass() {
        return holder;
    }

    @Override
    public final JavaConstant getConstantValue() {
        int constantValueIndex = getConstantValueIndex();
        if (constantValueIndex == 0) {
            return null;
        }
        return (JavaConstant) holder.getConstantPool().lookupConstant(constantValueIndex);
    }

    protected abstract int getConstantValueIndex();

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(DECLARED_ANNOTATIONS);
        AbstractEspressoResolvedInstanceType container = getDeclaringClass();
        return AnnotationsInfo.make(bytes, container.getConstantPool(), container);
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(TYPE_ANNOTATIONS);
        AbstractEspressoResolvedInstanceType container = getDeclaringClass();
        return AnnotationsInfo.make(bytes, container.getConstantPool(), container);
    }

    protected abstract byte[] getRawAnnotationBytes(int category);

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractEspressoResolvedJavaField that = (AbstractEspressoResolvedJavaField) o;
        return equals0(that);
    }

    protected abstract boolean equals0(AbstractEspressoResolvedJavaField that);

    @Override
    public abstract int hashCode();

    @Override
    public final String toString() {
        return format("EspressoResolvedJavaField<%H.%n %t:") + getOffset() + ">";
    }
}
