/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class TruffleConstantFieldProvider implements ConstantFieldProvider {
    private final ConstantFieldProvider graalConstantFieldProvider;
    private final MetaAccessProvider metaAccess;

    public TruffleConstantFieldProvider(ConstantFieldProvider graalConstantFieldProvider, MetaAccessProvider metaAccess) {
        this.graalConstantFieldProvider = graalConstantFieldProvider;
        this.metaAccess = metaAccess;
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        JavaConstant receiver = tool.getReceiver();
        if (!field.isStatic() && receiver.isNonNull()) {
            JavaType fieldType = field.getType();
            if (field.isFinal() || field.getAnnotation(CompilationFinal.class) != null ||
                            (fieldType.getJavaKind() == JavaKind.Object && (field.getAnnotation(Child.class) != null || field.getAnnotation(Children.class) != null))) {
                final JavaConstant constant = tool.readValue();
                assert verifyFieldValue(field, constant);
                if (fieldType.getJavaKind() == JavaKind.Object && fieldType instanceof ResolvedJavaType && ((ResolvedJavaType) fieldType).isArray() &&
                                (field.getAnnotation(CompilationFinal.class) != null || field.getAnnotation(Children.class) != null)) {
                    return tool.foldStableArray(constant, getArrayDimension(fieldType), true);
                } else {
                    return tool.foldConstant(constant);
                }
            }
        } else if (field.isStatic()) {
            if (field.getAnnotation(CompilationFinal.class) != null) {
                JavaConstant constant = tool.readValue();
                return tool.foldStableArray(constant, getArrayDimension(field.getType()), true);
            }
        }

        return graalConstantFieldProvider.readConstantField(field, tool);
    }

    private static int getArrayDimension(JavaType type) {
        int dimensions = 0;
        JavaType componentType = type;
        while ((componentType = componentType.getComponentType()) != null) {
            dimensions++;
        }
        return dimensions;
    }

    private boolean verifyFieldValue(ResolvedJavaField field, JavaConstant constant) {
        assert field.getAnnotation(Child.class) == null || constant.isNull() ||
                        metaAccess.lookupJavaType(com.oracle.truffle.api.nodes.Node.class).isAssignableFrom(metaAccess.lookupJavaType(constant)) : "@Child field value must be a Node: " + field +
                                        ", but was: " + constant;
        assert field.getAnnotation(Children.class) == null || constant.isNull() || metaAccess.lookupJavaType(constant).isArray() : "@Children field value must be an array: " + field + ", but was: " +
                        constant;
        return true;
    }
}
