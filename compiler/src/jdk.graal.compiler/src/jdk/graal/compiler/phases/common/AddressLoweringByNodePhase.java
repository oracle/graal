/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * A phase the visits each address node and lowers into a machine dependent form.
 */
public class AddressLoweringByNodePhase extends AddressLoweringPhase {

    public abstract static class AddressLowering {

        @SuppressWarnings("unused")
        public void preProcess(StructuredGraph graph, LoopsDataProvider loopsDataProvider) {
        }

        @SuppressWarnings("unused")
        public void postProcess(AddressNode lowered) {
        }

        public abstract AddressNode lower(ValueNode base, ValueNode offset);
    }

    private final AddressLoweringByNodePhase.AddressLowering lowering;

    public AddressLoweringByNodePhase(AddressLoweringByNodePhase.AddressLowering lowering) {
        this.lowering = lowering;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        lowering.preProcess(graph, providers.getLoopsDataProvider());
        for (Node node : graph.getNodes()) {
            AddressNode lowered;
            if (node instanceof OffsetAddressNode) {
                OffsetAddressNode address = (OffsetAddressNode) node;
                lowered = lowering.lower(address.getBase(), address.getOffset());
                lowering.postProcess(lowered);
            } else {
                continue;
            }
            node.replaceAtUsages(lowered);
            GraphUtil.killWithUnusedFloatingInputs(node);
        }
    }
}
