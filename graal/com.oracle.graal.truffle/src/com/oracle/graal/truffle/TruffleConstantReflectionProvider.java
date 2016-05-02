/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

public class TruffleConstantReflectionProvider implements ConstantReflectionProvider {
    private final ConstantReflectionProvider graalConstantReflection;
    private final MetaAccessProvider metaAccess;

    public TruffleConstantReflectionProvider(ConstantReflectionProvider graalConstantReflection, MetaAccessProvider metaAccess) {
        this.graalConstantReflection = graalConstantReflection;
        this.metaAccess = metaAccess;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return graalConstantReflection.constantEquals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        return graalConstantReflection.readArrayLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return graalConstantReflection.readArrayElement(array, index);
    }

    @Override
    public JavaConstant readConstantArrayElement(JavaConstant array, int index) {
        return graalConstantReflection.readConstantArrayElement(array, index);
    }

    @Override
    public JavaConstant readConstantArrayElementForOffset(JavaConstant array, long offset) {
        return graalConstantReflection.readConstantArrayElementForOffset(array, offset);
    }

    @Override
    public JavaConstant readConstantFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        if (!field.isStatic() && receiver.isNonNull()) {
            JavaType fieldType = field.getType();
            if (field.isFinal() || field.getAnnotation(CompilationFinal.class) != null ||
                            (fieldType.getJavaKind() == JavaKind.Object && (field.getAnnotation(Child.class) != null || field.getAnnotation(Children.class) != null))) {
                final JavaConstant constant;
                if (fieldType.getJavaKind() == JavaKind.Object && fieldType instanceof ResolvedJavaType && ((ResolvedJavaType) fieldType).isArray() &&
                                (field.getAnnotation(CompilationFinal.class) != null || field.getAnnotation(Children.class) != null)) {
                    constant = graalConstantReflection.readStableFieldValue(field, receiver, true);
                } else {
                    constant = graalConstantReflection.readFieldValue(field, receiver);
                }
                assert verifyFieldValue(field, constant);
                return constant;
            }
        } else if (field.isStatic()) {
            if (field.getAnnotation(CompilationFinal.class) != null) {
                return graalConstantReflection.readStableFieldValue(field, receiver, true);
            }
        }
        return graalConstantReflection.readConstantFieldValue(field, receiver);
    }

    private boolean verifyFieldValue(ResolvedJavaField field, JavaConstant constant) {
        assert field.getAnnotation(Child.class) == null || constant.isNull() ||
                        metaAccess.lookupJavaType(com.oracle.truffle.api.nodes.Node.class).isAssignableFrom(metaAccess.lookupJavaType(constant)) : "@Child field value must be a Node: " + field +
                                        ", but was: " + constant;
        assert field.getAnnotation(Children.class) == null || constant.isNull() || metaAccess.lookupJavaType(constant).isArray() : "@Children field value must be an array: " + field + ", but was: " +
                        constant;
        return true;
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return graalConstantReflection.readFieldValue(field, receiver);
    }

    @Override
    public JavaConstant readStableFieldValue(ResolvedJavaField field, JavaConstant receiver, boolean isDefaultStable) {
        return graalConstantReflection.readStableFieldValue(field, receiver, isDefaultStable);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        return graalConstantReflection.boxPrimitive(source);
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        return graalConstantReflection.unboxPrimitive(source);
    }

    @Override
    public JavaConstant forString(String value) {
        return graalConstantReflection.forString(value);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        return graalConstantReflection.asJavaType(constant);
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return graalConstantReflection.getMethodHandleAccess();
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return graalConstantReflection.getMemoryAccessProvider();
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return graalConstantReflection.asJavaClass(type);
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        return graalConstantReflection.asObjectHub(type);
    }
}
