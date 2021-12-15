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
package com.oracle.graal.pointsto;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This interface contains hooks for constant scanning events.
 *
 * When scanning a field there are three possible cases: the field value is a relocated pointer, the
 * field value is null or the field value is non-null. When scanning array elements there are two
 * possible cases: the element value is either null or the field value is non-null. The implementers
 * of this API will call the appropriate method during scanning.
 */
@SuppressWarnings("unused")
public interface ObjectScanningObserver {

    /**
     * Hook for relocated pointer scanned field value.
     * <p>
     * For relocated pointers the value is only known at runtime after methods are relocated, which
     * is pretty much the same as a field written at runtime: we do not have a constant value.
     */
    default boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        return false;
    }

    /**
     * Hook for scanned null field value.
     */
    default boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ScanReason reason) {
        return false;
    }

    /**
     * Hook for scanned non-null field value.
     * 
     * @return true if value is consumed, false otherwise
     */
    default boolean forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        return false;
    }

    /**
     * Hook for scanned null element value.
     */
    default boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ScanReason reason) {
        return false;
    }

    /**
     * Hook for scanned non-null element value.
     * 
     * @return true if value is consumed, false otherwise
     */
    default boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex, ScanReason reason) {
        return false;
    }

    /**
     * Hook for scanned embedded root.
     */
    default void forEmbeddedRoot(JavaConstant root, ScanReason reason) {
    }

    /**
     * Hook for scanned constant.
     */
    default void forScannedConstant(JavaConstant scannedValue, ScanReason reason) {
    }
}
