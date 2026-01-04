/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.deopt;

import java.lang.reflect.Array;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Support for deoptimization with virtual Vector API objects in the state.
 */
public class VectorAPIDeoptimizationSupport {

    /**
     * Materialize the payload array of a Vector API class. That is, allocate a primitive array of
     * the appropriate element type and length for the Vector API value. Read the vector's entries
     * from the stack and store them in the array.
     *
     * @param deoptState state for accessing values on the stack
     * @param layout non-null payload layout from {@link #getLayout}
     * @param vectorEncoding describes the location of the vector on the stack
     * @param sourceFrame the source frame containing the vector
     * @return a materialized primitive array if the object to be materialized is a Vector API
     *         vector; {@code null} otherwise
     */
    public Object materializePayload(DeoptState deoptState, PayloadLayout layout, FrameInfoQueryResult.ValueInfo vectorEncoding, FrameInfoQueryResult sourceFrame) {
        /*
         * Read values from the stack and write them to an array of the same element type. Note that
         * vector masks in states are already represented as vectors of byte-sized 0 or 1 values,
         * this is ensured by the VectorAPIExpansionPhase. Therefore, this code does not need to
         * worry about the target's representation of vector masks; an element type of boolean in
         * the layout will allow us to handle masks correctly.
         */
        JavaKind elementKind = JavaKind.fromJavaClass(layout.elementType);
        Object array = Array.newInstance(layout.elementType, layout.vectorLength);
        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();
        UnsignedWord curOffset = Word.unsigned(objectLayout.getArrayBaseOffset(elementKind));
        for (int i = 0; i < layout.vectorLength; i++) {
            FrameInfoQueryResult.ValueInfo elementEncoding = vectorEncoding.copyForElement(elementKind, i * elementKind.getByteCount());
            JavaConstant con = readValue(deoptState, elementEncoding, sourceFrame);
            writeValueInMaterializedObj(array, curOffset, con, sourceFrame);
            curOffset = curOffset.add(objectLayout.sizeInBytes(elementKind));
        }
        return array;
    }

    protected static JavaConstant readValue(DeoptState deoptState, FrameInfoQueryResult.ValueInfo valueInfo, FrameInfoQueryResult sourceFrame) {
        return deoptState.readValue(valueInfo, sourceFrame);
    }

    protected static void writeValueInMaterializedObj(Object materializedObj, UnsignedWord offsetInObj, JavaConstant constant, FrameInfoQueryResult frameInfo) {
        Deoptimizer.writeValueInMaterializedObj(materializedObj, offsetInObj, constant, frameInfo);
    }

    /**
     * Describes the layout of a vector on the stack during deoptimization: The vector elements will
     * be represented by {@code vectorLength} consecutive values of the primitive
     * {@code elementType}.
     */
    public record PayloadLayout(Class<?> elementType, int vectorLength) {

    }

    /**
     * Map from Vector API vector types, e.g., {@code Double256Vector.class}, to the corresponding
     * payload layout.
     */
    private final EconomicMap<Class<?>, PayloadLayout> typeMap = EconomicMap.create();

    public void putLayout(Class<?> vectorClass, PayloadLayout layout) {
        typeMap.put(vectorClass, layout);
    }

    public PayloadLayout getLayout(Class<?> vectorClass) {
        return typeMap.get(vectorClass);
    }
}
