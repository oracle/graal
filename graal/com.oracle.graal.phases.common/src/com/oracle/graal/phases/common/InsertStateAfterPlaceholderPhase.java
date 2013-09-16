/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.common;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;

public class InsertStateAfterPlaceholderPhase extends Phase {

    private static class PlaceholderNode extends AbstractStateSplit implements StateSplit, IterableNodeType, LIRLowerable, Canonicalizable {

        public PlaceholderNode() {
            super(StampFactory.forVoid());
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            // nothing to do
        }

        @Override
        public boolean hasSideEffect() {
            return false;
        }

        @Override
        public ValueNode canonical(CanonicalizerTool tool) {
            if (!usages().isEmpty()) {
                return this;
            }
            if (stateAfter() == null) {
                return null;
            }
            FixedNode next = next();
            if (next instanceof PlaceholderNode && ((PlaceholderNode) next).stateAfter() != null) {
                return null;
            }
            return this;
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        boolean needsPlaceHolder = false;
        for (Node node : graph.getNodes().filterInterface(StateSplit.class)) {
            StateSplit stateSplit = (StateSplit) node;
            if (stateSplit.hasSideEffect() && stateSplit.stateAfter() != null) {
                needsPlaceHolder = true;
            }
        }
        if (needsPlaceHolder) {
            for (ReturnNode ret : graph.getNodes().filter(ReturnNode.class)) {
                PlaceholderNode p = graph.add(new PlaceholderNode());
                p.setStateAfter(graph.add(new FrameState(FrameState.AFTER_BCI)));
                graph.addBeforeFixed(ret, p);
            }
        }
    }

}
