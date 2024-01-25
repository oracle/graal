/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.truffle;

import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;

import com.oracle.truffle.compiler.ConstantFieldInfo;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

final class TruffleConstantFieldProvider implements ConstantFieldProvider {

    private final PartialEvaluator partialEvaluator;
    private final ConstantFieldProvider delegate;

    TruffleConstantFieldProvider(PartialEvaluator partialEvaluator, ConstantFieldProvider delegate) {
        this.partialEvaluator = partialEvaluator;
        this.delegate = delegate;
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
        ConstantFieldInfo info = partialEvaluator.getConstantFieldInfo(field);
        if (info != null) {
            if (info.isChildren()) {
                int stableDimensions = isArrayField ? 1 : 0;
                return tool.foldStableArray(verifyFieldValue(field, tool.readValue(), info), stableDimensions, true);
            } else if (!isStaticField && hasObjectKind) {
                if (info.isChild()) {
                    return tool.foldConstant(verifyFieldValue(field, tool.readValue(), info));
                }
            }
            if (isArrayField) {
                int dimensions = info.getDimensions();
                assert dimensions >= 0 : dimensions;
                return tool.foldStableArray(tool.readValue(), dimensions, true);
            } else {
                return tool.foldConstant(tool.readValue());
            }
        }
        if (isArrayField) {
            return readConstantFieldFast(field, tool);
        }
        return null;
    }

    private <T> T readConstantFieldFast(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        T ret = delegate.readConstantField(field, tool);
        if (ret == null && field.isFinal()) {
            ret = tool.foldConstant(tool.readValue());
        }
        return ret;
    }

    @Override
    public boolean maybeFinal(ResolvedJavaField field) {
        return delegate.maybeFinal(field);
    }

    private JavaConstant verifyFieldValue(ResolvedJavaField field, JavaConstant constant, ConstantFieldInfo info) {
        assert !info.isChild() || constant.isNull() ||
                        partialEvaluator.types.Node.isAssignableFrom(partialEvaluator.getProviders().getMetaAccess().lookupJavaType(constant)) : String.format(
                                        "@Child field value must be a Node: %s, but was: %s", field, constant);
        assert !info.isChildren() || constant.isNull() ||
                        partialEvaluator.getProviders().getMetaAccess().lookupJavaType(constant).isArray() : String.format("@Children field value must be an array: %s, but was: %s", field, constant);
        return constant;
    }
}
