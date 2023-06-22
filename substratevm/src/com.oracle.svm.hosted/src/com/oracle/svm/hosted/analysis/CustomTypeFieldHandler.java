/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.hosted.substitute.ComputedValueField;

public abstract class CustomTypeFieldHandler {
    protected final BigBang bb;
    private final AnalysisMetaAccess metaAccess;
    private Set<AnalysisField> processedFields = ConcurrentHashMap.newKeySet();

    public CustomTypeFieldHandler(BigBang bb, AnalysisMetaAccess metaAccess) {
        this.bb = bb;
        this.metaAccess = metaAccess;
    }

    public void handleField(AnalysisField field) {
        if (processedFields.contains(field)) {
            return;
        }
        /*
         * Only process fields that are accessed. In particular, we must not register the custom
         * types as allocated when the field is not yet accessed.
         */
        assert field.isAccessed();
        if (field.wrapped instanceof ComputedValueField computedField) {
            if (!computedField.isValueAvailableBeforeAnalysis() && field.getJavaKind().isObject()) {
                injectFieldTypes(field, field.getType());
            }
        } else if (field.isComputedValue()) {
            if (!field.getStorageKind().isPrimitive()) {
                field.setCanBeNull(field.computedValueCanBeNull());
                injectFieldTypes(field, transformTypes(field, field.computedValueTypes()));
            }
        }
        processedFields.add(field);
    }

    private void injectFieldTypes(AnalysisField field, List<AnalysisType> customTypes) {
        for (AnalysisType type : customTypes) {
            if (!type.isPrimitive()) {
                type.registerAsAllocated("Is declared as the type of an unknown object field.");
            }
        }

        /* Use the annotation types, instead of the declared type, in the field initialization. */
        injectFieldTypes(field, customTypes.toArray(new AnalysisType[0]));
    }

    protected abstract void injectFieldTypes(AnalysisField aField, AnalysisType... customTypes);

    private List<AnalysisType> transformTypes(AnalysisField field, Class<?>[] types) {
        List<AnalysisType> customTypes = new ArrayList<>();
        AnalysisType declaredType = field.getType();

        for (Class<?> customType : types) {
            AnalysisType aCustomType = metaAccess.lookupJavaType(customType);

            assert !WordBase.class.isAssignableFrom(customType) : "Custom type must not be a subtype of WordBase: field: " + field + " | declared type: " + declaredType +
                            " | custom type: " + customType;
            assert declaredType.isAssignableFrom(aCustomType) : "Custom type must be a subtype of the declared type: field: " + field + " | declared type: " + declaredType +
                            " | custom type: " + customType;
            assert aCustomType.isPrimitive() || isConcreteType(aCustomType) : "Custom type cannot be abstract: field: " + field + " | custom type " + aCustomType;

            customTypes.add(aCustomType);
        }
        return customTypes;
    }

    public static boolean isConcreteType(AnalysisType type) {
        return type.isArray() || (type.isInstanceClass() && !type.isAbstract());
    }

    public void cleanupAfterAnalysis() {
        processedFields = null;
    }
}
