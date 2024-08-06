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
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class HostedValuesProvider {
    protected final AnalysisMetaAccess metaAccess;
    protected final AnalysisUniverse universe;

    public HostedValuesProvider(AnalysisMetaAccess metaAccess, AnalysisUniverse universe) {
        this.metaAccess = metaAccess;
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
            JavaConstant replacedConstant = universe.replaceObjectWithConstant(oldObject);
            if (!replacedConstant.equals(value)) {
                return validateReplacedConstant(replacedConstant);
            }
        }
        return value;
    }

    /** Hook to run validation checks on the replaced value. */
    public JavaConstant validateReplacedConstant(JavaConstant value) {
        return value;
    }

    public JavaConstant forObject(Object object) {
        return GraalAccess.getOriginalSnippetReflection().forObject(object);
    }

    public <T> T asObject(Class<T> type, JavaConstant constant) {
        return GraalAccess.getOriginalSnippetReflection().asObject(type, constant);
    }

    /** Hook to allow subclasses to intercept hosted constants. */
    public JavaConstant interceptHosted(JavaConstant constant) {
        return constant;
    }
}
