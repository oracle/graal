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
package com.oracle.graal.pointsto.heap;

import java.util.Map;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Additional data for a {@link AnalysisType type} that is only available for types that are marked
 * as reachable. Computed lazily once the type is seen as reachable.
 */
public final class TypeData {
    /**
     * The raw values of all static fields, regardless of field reachability status. Evaluating the
     * {@link AnalysisFuture} runs
     * {@link ImageHeapScanner#onFieldValueReachable(AnalysisField, ValueSupplier, ObjectScanner.ScanReason)}
     * adds the result to the image heap}.
     */
    final Map<ResolvedJavaField, AnalysisFuture<JavaConstant>> staticFieldValues;

    public TypeData(Map<ResolvedJavaField, AnalysisFuture<JavaConstant>> staticFieldValues) {
        this.staticFieldValues = staticFieldValues;
    }

    /**
     * Return a task for transforming and snapshotting the field value, effectively a future for
     * {@link ImageHeapScanner#onFieldValueReachable(AnalysisField, ValueSupplier, ObjectScanner.ScanReason)}.
     */
    public AnalysisFuture<JavaConstant> getFieldTask(AnalysisField field) {
        return staticFieldValues.get(field);
    }

    /**
     * Read the field value, executing the field task in this thread if not already executed.
     */
    public JavaConstant readField(AnalysisField field) {
        return staticFieldValues.get(field).ensureDone();
    }

    public void setFieldTask(AnalysisField field, AnalysisFuture<JavaConstant> task) {
        staticFieldValues.put(field, task);
    }

}
