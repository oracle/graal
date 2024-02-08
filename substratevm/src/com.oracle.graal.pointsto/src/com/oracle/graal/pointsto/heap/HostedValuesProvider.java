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
package com.oracle.graal.pointsto.heap;

import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class HostedValuesProvider {
    protected final AnalysisUniverse universe;

    public HostedValuesProvider(AnalysisUniverse universe) {
        this.universe = universe;
    }

    public ValueSupplier<JavaConstant> readFieldValue(AnalysisField field, JavaConstant receiver) {
        return ValueSupplier.eagerValue(doReadValue(field, receiver));
    }

    public JavaConstant readFieldValueWithReplacement(AnalysisField field, JavaConstant receiver) {
        return replaceObject(doReadValue(field, receiver));
    }

    private JavaConstant doReadValue(AnalysisField field, JavaConstant receiver) {
        field.beforeFieldValueAccess();
        return interceptHosted(GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(field.wrapped, receiver));
    }

    public Integer readArrayLength(JavaConstant array) {
        return GraalAccess.getOriginalProviders().getConstantReflection().readArrayLength(array);
    }

    public JavaConstant readArrayElement(JavaConstant array, int index) {
        return GraalAccess.getOriginalProviders().getConstantReflection().readArrayElement(array, index);
    }

    /**
     * Run all registered object replacers.
     */
    public JavaConstant replaceObject(JavaConstant value) {
        if (value == JavaConstant.NULL_POINTER) {
            return JavaConstant.NULL_POINTER;
        }
        if (value instanceof ImageHeapConstant) {
            /* The value is replaced when the object is snapshotted. */
            return value;
        }
        if (value.getJavaKind() == JavaKind.Object) {
            Object oldObject = asObject(Object.class, value);
            Object newObject = universe.replaceObject(oldObject);
            if (newObject != oldObject) {
                return validateReplacedConstant(forObject(newObject));
            }
        }
        return value;
    }

    /** Hook to run validation checks on the replaced value. */
    public JavaConstant validateReplacedConstant(JavaConstant value) {
        return value;
    }

    public JavaConstant forObject(Object object) {
        return GraalAccess.getOriginalProviders().getSnippetReflection().forObject(object);
    }

    public <T> T asObject(Class<T> type, JavaConstant constant) {
        return GraalAccess.getOriginalProviders().getSnippetReflection().asObject(type, constant);
    }

    /**
     * Intercept the HotSpotObjectConstant and if it wraps an {@link ImageHeapConstant} unwrap it
     * and return the original constant. The ImageHeapObject likely comes from reading a field of a
     * normal object that is referencing a simulated object. The originalConstantReflection provider
     * is not aware of simulated constants, and it always wraps them into a HotSpotObjectConstant
     * when reading fields.
     * </p>
     * This method will return null if the input constant is null.
     */
    public JavaConstant interceptHosted(JavaConstant constant) {
        if (constant != null && constant.getJavaKind().isObject() && !constant.isNull()) {
            Object original = asObject(Object.class, constant);
            if (original instanceof ImageHeapConstant heapConstant) {
                return heapConstant;
            }
        }
        /* Return the input constant. */
        return constant;
    }
}
