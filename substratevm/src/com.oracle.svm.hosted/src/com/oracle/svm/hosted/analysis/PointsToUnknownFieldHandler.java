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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaKind;

public class PointsToUnknownFieldHandler extends UnknownFieldHandler {
    public PointsToUnknownFieldHandler(BigBang bb, AnalysisMetaAccess metaAccess) {
        super(bb, metaAccess);
    }

    /**
     * Register a field as containing unknown object(s), i.e., is usually written only in hosted
     * code. It can have multiple declared types provided via annotation.
     */
    @Override
    protected void handleUnknownObjectField(AnalysisField aField, AnalysisType... declaredTypes) {
        NativeImagePointsToAnalysis analysis = (NativeImagePointsToAnalysis) bb;
        assert aField.getJavaKind() == JavaKind.Object;

        aField.registerAsWritten(null);

        /* Link the field with all declared types. */
        for (AnalysisType fieldDeclaredType : declaredTypes) {
            TypeFlow<?> fieldDeclaredTypeFlow = fieldDeclaredType.getTypeFlow(analysis, true);
            if (aField.isStatic()) {
                fieldDeclaredTypeFlow.addUse(analysis, aField.getStaticFieldFlow());
            } else {
                fieldDeclaredTypeFlow.addUse(analysis, aField.getInitialInstanceFieldFlow());
                if (fieldDeclaredType.isArray()) {
                    AnalysisType fieldComponentType = fieldDeclaredType.getComponentType();
                    aField.getInitialInstanceFieldFlow().addUse(analysis, aField.getInstanceFieldFlow());
                    // TODO is there a better way to signal that the elements type flow of a unknown
                    // object field is not empty?
                    if (!fieldComponentType.isPrimitive()) {
                        /*
                         * Write the component type abstract object into the field array elements
                         * type flow, i.e., the array elements type flow of the abstract object of
                         * the field declared type.
                         *
                         * This is required so that the index loads from this array return all the
                         * possible objects that can be stored in the array.
                         */
                        TypeFlow<?> elementsFlow = fieldDeclaredType.getContextInsensitiveAnalysisObject().getArrayElementsFlow(analysis, true);
                        fieldComponentType.getTypeFlow(analysis, false).addUse(analysis, elementsFlow);

                        /*
                         * In the current implementation it is not necessary to do it it recursively
                         * for multidimensional arrays since we don't model individual array
                         * elements, so from the point of view of the static analysis the field's
                         * array elements value is non null (in the case of a n-dimensional array
                         * that value is another array, n-1 dimensional).
                         */
                    }
                }
            }
        }
    }
}
