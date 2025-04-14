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

package com.oracle.svm.hosted.webimage.wasm.phases;

import java.util.List;

import com.oracle.svm.hosted.webimage.wasm.snippets.SingleThreadedAtomics;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;

/**
 * {@link SingleThreadedAtomicsPhase} implementation for WasmLM backend.
 * <p>
 * Here, CAS nodes are replaced with an equivalent foreign call to a method in
 * {@link SingleThreadedAtomics}.
 */
public class WasmLMSingleThreadedAtomicsPhase extends SingleThreadedAtomicsPhase {
    @Override
    protected void processCAS(CoreProviders providers, AbstractCompareAndSwapNode cas) {
        StructuredGraph graph = cas.graph();
        boolean isLogic = cas instanceof LogicCompareAndSwapNode;

        ForeignCallDescriptor target = switch (cas.getExpectedValue().getStackKind()) {
            case Int -> {
                var stamp = (IntegerStamp) cas.stamp(NodeView.DEFAULT);
                if (stamp.getBits() == 8) {
                    yield isLogic ? SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_BYTE : SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_BYTE;
                } else if (stamp.getBits() == 16) {
                    yield isLogic ? SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_CHAR : SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_CHAR;
                } else {
                    yield isLogic ? SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_INT : SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_INT;
                }
            }
            case Long -> isLogic ? SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_LONG : SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_LONG;
            case Object -> isLogic ? SingleThreadedAtomics.LOGIC_COMPARE_AND_SWAP_OBJECT : SingleThreadedAtomics.VALUE_COMPARE_AND_SWAP_OBJECT;
            default -> throw GraalError.unimplemented("CAS with kind " + cas.getExpectedValue().getStackKind()); // ExcludeFromJacocoGeneratedReport
        };

        graph.replaceFixedWithFixed(cas, graph.add(new ForeignCallNode(target, cas.stamp(NodeView.DEFAULT), List.of(cas.getAddress(), cas.getExpectedValue(), cas.getNewValue()))));
    }
}
