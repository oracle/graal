/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Produces AnyPrimitive state that leads to immediate saturation of all uses. Used to represent any
 * operation on primitives that is not explicitly modeled by the analysis.
 * </p>
 * This flow can be either global (source == null) or local (source != null).
 */
public final class AnyPrimitiveSourceTypeFlow extends TypeFlow<BytecodePosition> implements PrimitiveFlow {

    public AnyPrimitiveSourceTypeFlow(BytecodePosition source, AnalysisType type) {
        super(source, type, TypeState.anyPrimitiveState());
    }

    private AnyPrimitiveSourceTypeFlow(MethodFlowsGraph methodFlows, AnyPrimitiveSourceTypeFlow original) {
        super(original, methodFlows, TypeState.anyPrimitiveState());
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        assert isLocal() : "Global flow should never be cloned: " + this;
        return new AnyPrimitiveSourceTypeFlow(methodFlows, this);
    }

    private boolean isLocal() {
        return source != null;
    }

    @Override
    public boolean canSaturate(PointsToAnalysis bb) {
        /*
         * AnyPrimitiveSourceTypeFlow can be used as a global flow that should always propagate
         * values. The global version can be identified be having source == null, and it should
         * never saturate.
         * 
         * The local versions of this flow have a concrete bytecode position and can saturate.
         */
        return isLocal();
    }
}
