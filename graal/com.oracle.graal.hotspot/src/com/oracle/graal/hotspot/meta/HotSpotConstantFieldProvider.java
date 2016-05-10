/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements the default constant folding semantics for Java fields in the HotSpot VM.
 */
public class HotSpotConstantFieldProvider implements ConstantFieldProvider {

    static class Options {
        @Option(help = "Determines whether to treat final fields with default values as constant.")//
        public static final OptionValue<Boolean> TrustFinalDefaultFields = new OptionValue<>(true);
    }

    private final HotSpotVMConfig config;

    public HotSpotConstantFieldProvider(HotSpotVMConfig config) {
        this.config = config;
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;

        if (hotspotField.isStatic()) {
            if (isStaticFieldConstant(hotspotField)) {
                JavaConstant value = tool.readValue();
                if (hotspotField.isStable() && !value.isDefaultForKind()) {
                    return tool.foldStableArray(value, getArrayDimension(hotspotField.getType()), hotspotField.isDefaultStable());
                } else {
                    return tool.foldConstant(value);
                }
            }
        } else {
            if (hotspotField.isFinal()) {
                JavaConstant value = tool.readValue();
                if (isFinalInstanceFieldValueConstant(value, tool.getReceiver())) {
                    return tool.foldConstant(value);
                }
            } else if (hotspotField.isStable() && config.foldStableValues) {
                JavaConstant value = tool.readValue();
                if (isStableInstanceFieldValueConstant(value, tool.getReceiver())) {
                    return tool.foldStableArray(value, getArrayDimension(hotspotField.getType()), hotspotField.isDefaultStable());
                }
            }
        }
        return null;
    }

    private static int getArrayDimension(JavaType type) {
        int dimensions = 0;
        JavaType componentType = type;
        while ((componentType = componentType.getComponentType()) != null) {
            dimensions++;
        }
        return dimensions;
    }

    private static final String SystemClassName = "Ljava/lang/System;";

    /**
     * Determines if a static field is constant for the purpose of {@link #readConstantField}.
     */
    protected boolean isStaticFieldConstant(HotSpotResolvedJavaField staticField) {
        if (staticField.isFinal() || (staticField.isStable() && config.foldStableValues)) {
            ResolvedJavaType holder = staticField.getDeclaringClass();
            if (holder.isInitialized() && !holder.getName().equals(SystemClassName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if a final instance field is constant for the purpose of
     * {@link #readConstantField}.
     *
     * @param value
     * @param receiver
     */
    protected boolean isFinalInstanceFieldValueConstant(JavaConstant value, JavaConstant receiver) {
        return !value.isDefaultForKind() || Options.TrustFinalDefaultFields.getValue();
    }

    /**
     * Determines if a stable instance field is constant for the purpose of
     * {@link #readConstantField}.
     *
     * @param value
     * @param receiver
     */
    protected boolean isStableInstanceFieldValueConstant(JavaConstant value, JavaConstant receiver) {
        return !value.isDefaultForKind();
    }
}
