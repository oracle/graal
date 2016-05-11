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
        if (field.isStatic() || receiver.isNonNull()) {
            if (field.getType().getJavaKind() == JavaKind.Object) {
                CompilationFinal compilationFinal = field.getAnnotation(CompilationFinal.class);
                if (compilationFinal != null) {
                    int stableDimensions = actualStableDimensions(field, compilationFinal.dimensions());
                    return tool.foldStableArray(tool.readValue(), stableDimensions, true);
                } else if (!field.isStatic() && field.getAnnotation(Children.class) != null) {
                    int stableDimensions = field.getType().isArray() ? 1 : 0;
                    return tool.foldStableArray(verifyFieldValue(field, tool.readValue()), stableDimensions, true);
                } else if (field.isFinal() || (!field.isStatic() && field.getAnnotation(Child.class) != null)) {
                    return tool.foldConstant(verifyFieldValue(field, tool.readValue()));
                }
            } else if (field.isFinal() || (field.getAnnotation(CompilationFinal.class)) != null) {
                return tool.foldConstant(tool.readValue());
            }
        }

        return graalConstantFieldProvider.readConstantField(field, tool);
    }

    private static int actualStableDimensions(ResolvedJavaField field, int dimensions) {
        if (dimensions == 0) {
            return 0;
        }
        int arrayDim = getArrayDimensions(field.getType());
        if (dimensions < 0) {
            assert dimensions == -1 : "Negative @CompilationFinal dimensions";
            return arrayDim;
        }
        assert dimensions <= arrayDim : String.format("@CompilationFinal(dimensions=%d) exceeds declared array dimensions (%d) of field %s", dimensions, arrayDim, field);
        return Math.min(dimensions, arrayDim);
    }

    private static int getArrayDimensions(JavaType type) {
        int dimensions = 0;
        for (JavaType componentType = type; componentType.isArray(); componentType = componentType.getComponentType()) {
            dimensions++;
        }
        return dimensions;
    }

    private JavaConstant verifyFieldValue(ResolvedJavaField field, JavaConstant constant) {
        assert field.getAnnotation(Child.class) == null || constant.isNull() ||
                        metaAccess.lookupJavaType(com.oracle.truffle.api.nodes.Node.class).isAssignableFrom(metaAccess.lookupJavaType(constant)) : String.format(
                                        "@Child field value must be a Node: %s, but was: %s", field, constant);
        assert field.getAnnotation(Children.class) == null || constant.isNull() ||
                        metaAccess.lookupJavaType(constant).isArray() : String.format("@Children field value must be an array: %s, but was: %s", field, constant);
        return constant;
    }
}
