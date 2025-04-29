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

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Support for deoptimization with virtual Vector API objects in the state. This class is a
 * placeholder until GR-59869 is done.
 */
public abstract class VectorAPIDeoptimizationSupport {

    /**
     * If the {@code hub} refers to a Vector API vector, materialize its payload array. That is,
     * allocate a primitive array of the appropriate element type and length for the Vector API
     * value. Read the vector's entries from the stack and store them in the array.
     *
     * @param deoptState state for accessing values on the stack
     * @param hub the hub of the object to be materialized
     * @param vectorEncoding describes the location of the vector on the stack
     * @param sourceFrame the source frame containing the vector
     * @return a materialized primitive array if the object to be materialized is a Vector API
     *         vector; {@code null} otherwise
     */
    public Object materializePayload(DeoptState deoptState, DynamicHub hub, FrameInfoQueryResult.ValueInfo vectorEncoding, FrameInfoQueryResult sourceFrame) {
        throw VMError.intentionallyUnimplemented();
    }

    protected static JavaConstant readValue(DeoptState deoptState, FrameInfoQueryResult.ValueInfo valueInfo, FrameInfoQueryResult sourceFrame) {
        return deoptState.readValue(valueInfo, sourceFrame);
    }

    protected static void writeValueInMaterializedObj(Object materializedObj, UnsignedWord offsetInObj, JavaConstant constant, FrameInfoQueryResult frameInfo) {
        Deoptimizer.writeValueInMaterializedObj(materializedObj, offsetInObj, constant, frameInfo);
    }
}
