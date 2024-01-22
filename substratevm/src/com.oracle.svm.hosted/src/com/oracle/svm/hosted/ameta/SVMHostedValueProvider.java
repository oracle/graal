/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import java.lang.reflect.Array;

import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.RelocatableConstant;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

public class SVMHostedValueProvider extends HostedValuesProvider {

    private final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();

    public SVMHostedValueProvider(AnalysisUniverse universe) {
        super(universe);
    }

    /**
     * Read the field value and wrap it in a value supplier without performing any replacements. The
     * replacements are applied when the value is reached in the shadow heap. The shadow heap
     * doesn't directly store simulated values. The simulated values are only accessible via
     * {@link SimulateClassInitializerSupport#getSimulatedFieldValue(AnalysisField)}. The shadow
     * heap is a snapshot of the hosted state; simulated values are a level above the shadow heap.
     */
    @Override
    public ValueSupplier<JavaConstant> readFieldValue(AnalysisField field, JavaConstant receiver) {
        if (fieldValueInterceptionSupport.isValueAvailable(field)) {
            /* Materialize and return the value. */
            return ValueSupplier.eagerValue(doReadValue(field, receiver));
        }
        /*
         * Return a lazy value. First, this applies to fields annotated with
         * RecomputeFieldValue.Kind.FieldOffset and RecomputeFieldValue.Kind.Custom whose value
         * becomes available during hosted universe building and is installed by calling
         * ComputedValueField.processSubstrate() or ComputedValueField.readValue(). Secondly, this
         * applies to fields annotated with @UnknownObjectField whose value is set directly either
         * during analysis or in a later phase. Attempts to materialize the value before it becomes
         * available will result in an error.
         */
        return ValueSupplier.lazyValue(() -> doReadValue(field, receiver), () -> fieldValueInterceptionSupport.isValueAvailable(field));
    }

    /** Returns the hosted field value with replacements applied. */
    @Override
    public JavaConstant readFieldValueWithReplacement(AnalysisField field, JavaConstant receiver) {
        return replaceObject(doReadValue(field, receiver));
    }

    private JavaConstant doReadValue(AnalysisField field, JavaConstant receiver) {
        JavaConstant hostedReceiver = universe.toHosted(receiver);
        return universe.fromHosted(fieldValueInterceptionSupport.readFieldValue(field, hostedReceiver));
    }

    /**
     * This method returns the hosted array element value without any replacement. The replacements
     * are applied when the value is reached in the shadow heap.
     */
    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        Object a = SubstrateObjectConstant.asObject(array);

        if (!a.getClass().isArray() || index < 0 || index >= Array.getLength(a)) {
            return null;
        }

        if (a instanceof Object[]) {
            Object element = ((Object[]) a)[index];
            return forObject(element);
        } else {
            return JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        Object a = SubstrateObjectConstant.asObject(array);
        if (!a.getClass().isArray()) {
            return null;
        }
        return java.lang.reflect.Array.getLength(a);
    }

    /**
     * {@link #forObject} replaces relocatable pointers with {@link RelocatableConstant} and regular
     * {@link WordBase} values with {@link PrimitiveConstant}. No other {@link WordBase} values can
     * be reachable at this point.
     */
    @Override
    public JavaConstant validateReplacedConstant(JavaConstant value) {
        VMError.guarantee(value instanceof RelocatableConstant || !universe.getBigbang().getMetaAccess().isInstanceOf(value, WordBase.class));
        return value;
    }

    @Override
    public JavaConstant forObject(Object object) {
        if (object instanceof RelocatedPointer pointer) {
            return new RelocatableConstant(pointer);
        } else if (object instanceof WordBase word) {
            return JavaConstant.forIntegerKind(ConfigurationValues.getWordKind(), word.rawValue());
        }
        HostedSnippetReflectionProvider.validateRawObjectConstant(object);
        return SubstrateObjectConstant.forObject(object);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant instanceof RelocatableConstant relocatable) {
            return type.cast(relocatable.getPointer());
        }
        return SubstrateObjectConstant.asObject(type, constant);
    }

}
