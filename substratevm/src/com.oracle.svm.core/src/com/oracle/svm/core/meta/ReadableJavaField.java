/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability.ValueAvailability;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public interface ReadableJavaField extends ResolvedJavaField {

    static JavaConstant readFieldValue(MetaAccessProvider metaAccess, ConstantReflectionProvider originalConstantReflection, ResolvedJavaField javaField, JavaConstant javaConstant) {
        if (javaField instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) javaField;
            assert readableField.isValueAvailable() : "Field " + readableField.format("%H.%n") + " value not available for reading.";
            return readableField.readValue(metaAccess, javaConstant);
        } else {
            return originalConstantReflection.readFieldValue(javaField, javaConstant);
        }
    }

    JavaConstant readValue(MetaAccessProvider metaAccess, JavaConstant receiver);

    /**
     * When this method returns true, image heap snapshotting can access the value before analysis.
     * If the field is final, then the value can also be constant folded before analysis.
     *
     * The introduction of this method pre-dates {@link ValueAvailability}, i.e., we could combine
     * this method and {@link #isValueAvailable} into a single method that returns the
     * {@link ValueAvailability} of the field.
     */
    boolean isValueAvailableBeforeAnalysis();

    default boolean isValueAvailable() {
        return isValueAvailableBeforeAnalysis() || BuildPhaseProvider.isAnalysisFinished();
    }

    boolean injectFinalForRuntimeCompilation();

    static boolean injectFinalForRuntimeCompilation(ResolvedJavaField original) {
        if (original instanceof ReadableJavaField) {
            return ((ReadableJavaField) original).injectFinalForRuntimeCompilation();
        } else {
            return false;
        }
    }
}
