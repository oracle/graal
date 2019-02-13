/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.flow;

import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.NewInstanceTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.SVMTypeState;

/**
 * Type flow for pinned objects, i.e., objects that are always allocated in the old generation and
 * are not moved by the GC. The pinned new instance type flow is never analyzed context sensitively.
 */
public class PinnedNewInstanceTypeFlow extends NewInstanceTypeFlow {

    protected PinnedNewInstanceTypeFlow(Inflation bb, ValueNode node, AnalysisType type, BytecodeLocation allocationLabel) {
        super(node, type, allocationLabel, SVMTypeState.forPinned(bb, node, allocationLabel, type));
    }

    private PinnedNewInstanceTypeFlow(BigBang bb, PinnedNewInstanceTypeFlow original,
                    MethodFlowsGraph methodFlows) {
        super(bb, original, methodFlows);
    }

    @Override
    protected TypeState cloneSourceState(BigBang bb, MethodFlowsGraph methodFlows) {
        assert !this.isClone();

        /*
         * Pinned objects are never analyzed context sensitively, thus the clone will get the same
         * state as the original.
         */
        return this.sourceState;
    }

    @Override
    public TypeFlow<ValueNode> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new PinnedNewInstanceTypeFlow(bb, this, methodFlows);
    }
}
