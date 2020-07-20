/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * This class is used to model the elements type flow for array objects.
 */
public class ArrayElementsTypeFlow extends TypeFlow<AnalysisType> {

    /** The array object. */
    private AnalysisObject object;

    public ArrayElementsTypeFlow(AnalysisObject sourceObject) {
        super(sourceObject.type(), sourceObject.type().getComponentType());
        this.object = sourceObject;
    }

    public AnalysisObject getSourceObject() {
        return object;
    }

    @Override
    public TypeFlow<AnalysisType> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        throw shouldNotReachHere("The mixed elements flow should not be cloned. Use Load/StoreFlows.");
    }

    @Override
    public boolean canSaturate() {
        return false;
    }

    @Override
    protected void onInputSaturated(BigBang bb, TypeFlow<?> input) {
        /*
         * When an array store is saturated conservativelly assume that the array can contain any
         * subtype of its declared type.
         */
        getDeclaredType().getTypeFlow(bb, true).addUse(bb, this);
    }

    @Override
    public TypeState filter(BigBang bb, TypeState update) {
        if (declaredType.equals(bb.getObjectType())) {
            /* No need to filter. */
            return update;
        } else {
            /*
             * Filter out the objects not compatible with the declared type, i.e., those objects
             * whose type cannot be converted to the component type of the this array by assignment
             * conversion. At runtime that will throw an ArrayStoreException but during the analysis
             * we can detect such cases and filter out the incompatible types.
             */
            return TypeState.forIntersection(bb, update, declaredType.getTypeFlow(bb, true).getState());
        }
    }

    public AnalysisObject object() {
        return object;
    }

    @Override
    public String toString() {
        return "MixedElementsFlow<" + source.getName() + "\n" + getState() + ">";
    }

}
