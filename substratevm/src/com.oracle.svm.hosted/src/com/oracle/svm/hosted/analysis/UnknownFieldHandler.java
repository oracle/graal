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

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;

import jdk.vm.ci.meta.JavaKind;

public abstract class UnknownFieldHandler {
    protected final BigBang bb;
    private Set<AnalysisField> handledUnknownValueFields = new HashSet<>();
    private final AnalysisMetaAccess metaAccess;

    public UnknownFieldHandler(BigBang bb, AnalysisMetaAccess metaAccess) {
        this.bb = bb;
        this.metaAccess = metaAccess;
    }

    public void handleUnknownValueField(AnalysisField field) {
        if (handledUnknownValueFields.contains(field)) {
            return;
        }
        /*
         * Only process fields that are accessed. In particular, we must not register types listed
         * in the @UnknownObjectField annotation as allocated when the field is not yet accessed.
         */
        assert field.isAccessed();

        UnknownObjectField unknownObjectField = field.getAnnotation(UnknownObjectField.class);
        UnknownPrimitiveField unknownPrimitiveField = field.getAnnotation(UnknownPrimitiveField.class);
        if (unknownObjectField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownObjectField annotated field " + field.format("%H.%n") + " cannot be final";
            assert field.getJavaKind() == JavaKind.Object;

            field.setCanBeNull(unknownObjectField.canBeNull());

            List<AnalysisType> aAnnotationTypes = extractAnnotationTypes(field, unknownObjectField);

            for (AnalysisType type : aAnnotationTypes) {
                type.registerAsAllocated(null);
            }

            /*
             * Use the annotation types, instead of the declared type, in the UnknownObjectField
             * annotated fields initialization.
             */
            handleUnknownObjectField(field, aAnnotationTypes.toArray(new AnalysisType[0]));

        } else if (unknownPrimitiveField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownPrimitiveField annotated field " + field.format("%H.%n") + " cannot be final";
            /*
             * Register a primitive field as containing unknown values(s), i.e., is usually written
             * only in hosted code.
             */

            field.registerAsWritten(null);
        }

        handledUnknownValueFields.add(field);
    }

    private List<AnalysisType> extractAnnotationTypes(AnalysisField field, UnknownObjectField unknownObjectField) {
        List<Class<?>> annotationTypes = new ArrayList<>(Arrays.asList(unknownObjectField.types()));
        for (String annotationTypeName : unknownObjectField.fullyQualifiedTypes()) {
            try {
                Class<?> annotationType = Class.forName(annotationTypeName);
                annotationTypes.add(annotationType);
            } catch (ClassNotFoundException e) {
                throw shouldNotReachHere("Annotation type not found " + annotationTypeName);
            }
        }

        List<AnalysisType> aAnnotationTypes = new ArrayList<>();
        AnalysisType declaredType = field.getType();

        for (Class<?> annotationType : annotationTypes) {
            AnalysisType aAnnotationType = metaAccess.lookupJavaType(annotationType);

            assert !WordBase.class.isAssignableFrom(annotationType) : "Annotation type must not be a subtype of WordBase: field: " + field + " | declared type: " + declaredType +
                            " | annotation type: " + annotationType;
            assert declaredType.isAssignableFrom(aAnnotationType) : "Annotation type must be a subtype of the declared type: field: " + field + " | declared type: " + declaredType +
                            " | annotation type: " + annotationType;
            assert aAnnotationType.isArray() || (aAnnotationType.isInstanceClass() && !Modifier.isAbstract(aAnnotationType.getModifiers())) : "Annotation type cannot be abstract: field: " + field +
                            " | annotation type " + aAnnotationType;

            aAnnotationTypes.add(aAnnotationType);
        }
        return aAnnotationTypes;
    }

    protected abstract void handleUnknownObjectField(AnalysisField aField, AnalysisType... declaredTypes);

    public void cleanupAfterAnalysis() {
        handledUnknownValueFields = null;
    }
}
