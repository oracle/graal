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
package org.graalvm.compiler.truffle;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;

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
        boolean isStaticField = field.isStatic();
        if (!isStaticField && tool.getReceiver().isNull()) {
            // can't be optimized
            return null;
        }

        boolean isArrayField = field.getType().isArray();
        if (!isArrayField) {
            // The fast way does not require any annotation processing but only covers the most
            // frequent cases. It must not be used for array fields as it might return an incorrect
            // value for the number of stable dimensions.
            T ret = readConstantFieldFast(field, tool);
            if (ret != null) {
                return ret;
            }
        }

        boolean hasObjectKind = field.getType().getJavaKind() == JavaKind.Object;
        if (!isStaticField && hasObjectKind && field.getAnnotation(Child.class) != null) {
            return tool.foldConstant(verifyFieldValue(field, tool.readValue()));
        }

        CompilationFinal compilationFinal = field.getAnnotation(CompilationFinal.class);
        if (compilationFinal != null) {
            if (isArrayField) {
                int stableDimensions = actualStableDimensions(field, compilationFinal.dimensions());
                return tool.foldStableArray(tool.readValue(), stableDimensions, true);
            } else {
                return tool.foldConstant(tool.readValue());
            }
        }

        if (!isStaticField && hasObjectKind && field.getAnnotation(Children.class) != null) {
            int stableDimensions = isArrayField ? 1 : 0;
            return tool.foldStableArray(verifyFieldValue(field, tool.readValue()), stableDimensions, true);
        }

        if (isArrayField) {
            return readConstantFieldFast(field, tool);
        }
        return null;
    }

    private <T> T readConstantFieldFast(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        T ret = graalConstantFieldProvider.readConstantField(field, tool);
        if (ret == null && field.isFinal()) {
            ret = tool.foldConstant(tool.readValue());
        }
        return ret;
    }

    private static int actualStableDimensions(ResolvedJavaField field, int dimensions) {
        if (dimensions == 0) {
            return 0;
        }
        int arrayDim = getArrayDimensions(field.getType());
        if (dimensions < 0) {
            if (dimensions != -1) {
                throw new IllegalArgumentException("Negative @CompilationFinal dimensions");
            }
            return arrayDim;
        }
        if (dimensions > arrayDim) {
            throw new IllegalArgumentException(String.format("@CompilationFinal(dimensions=%d) exceeds declared array dimensions (%d) of field %s", dimensions, arrayDim, field));
        }
        return dimensions;
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
