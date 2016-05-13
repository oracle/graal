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
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements the default constant folding semantics for Java fields in the HotSpot VM.
 */
public class HotSpotConstantFieldProvider implements ConstantFieldProvider {

    static class Options {
        @Option(help = "Mark well-known stable fields as such.")//
        public static final OptionValue<Boolean> ImplicitStableValues = new OptionValue<>(true);
        @Option(help = "Determines whether to treat final fields with default values as constant.")//
        public static final OptionValue<Boolean> TrustFinalDefaultFields = new OptionValue<>(true);
    }

    private final HotSpotVMConfig config;

    public HotSpotConstantFieldProvider(HotSpotVMConfig config, MetaAccessProvider metaAccess) {
        this.config = config;
        try {
            this.stringValueField = metaAccess.lookupJavaField(String.class.getDeclaredField("value"));
        } catch (NoSuchFieldException | SecurityException e) {
            throw new GraalError(e);
        }
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;

        if (hotspotField.isStatic()) {
            if (isStaticFieldConstant(hotspotField)) {
                JavaConstant value = tool.readValue();
                if (isStableField(hotspotField) && !value.isDefaultForKind()) {
                    return tool.foldStableArray(value, getArrayDimension(hotspotField.getType()), isDefaultStableField(hotspotField));
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
                    return tool.foldStableArray(value, getArrayDimension(hotspotField.getType()), isDefaultStableField(hotspotField));
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
        if (staticField.isFinal() || (isStableField(staticField) && config.foldStableValues)) {
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

    private static boolean isArray(ResolvedJavaField field) {
        JavaType fieldType = field.getType();
        return fieldType instanceof ResolvedJavaType && ((ResolvedJavaType) fieldType).isArray();
    }

    private boolean isStableField(HotSpotResolvedJavaField field) {
        if (field.isStable()) {
            return true;
        }
        if (Options.ImplicitStableValues.getValue()) {
            if (isSyntheticEnumSwitchMap(field)) {
                return true;
            }
            if (isWellKnownImplicitStableField(field)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDefaultStableField(HotSpotResolvedJavaField field) {
        assert isStableField(field);
        if (isSyntheticEnumSwitchMap(field)) {
            return true;
        }
        return false;
    }

    private static boolean isSyntheticEnumSwitchMap(ResolvedJavaField field) {
        if (field.isSynthetic() && field.isStatic() && isArray(field)) {
            String name = field.getName();
            if (field.isFinal() && name.equals("$VALUES") || name.equals("ENUM$VALUES")) {
                // generated int[] field for EnumClass::values()
                return true;
            } else if (name.startsWith("$SwitchMap$") || name.startsWith("$SWITCH_TABLE$")) {
                // javac and ecj generate a static field in an inner class for a switch on an enum
                // named $SwitchMap$p$k$g$EnumClass and $SWITCH_TABLE$p$k$g$EnumClass, respectively
                return true;
            }
        }
        return false;
    }

    private final ResolvedJavaField stringValueField;

    private boolean isWellKnownImplicitStableField(HotSpotResolvedJavaField field) {
        return field.equals(stringValueField);
    }
}
