/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * For deoptimization testing. The node performs a deoptimization, but the lowering to
 * DeoptimizeNode is delayed, so that the analysis sees a return from original method.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public class TestDeoptimizeNode extends FixedWithNextNode implements Canonicalizable {
    public static final NodeClass<TestDeoptimizeNode> TYPE = NodeClass.create(TestDeoptimizeNode.class);

    public TestDeoptimizeNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (SubstrateUtil.HOSTED) {
            if (!BuildPhaseProvider.isAnalysisFinished()) {
                return this;
            }
            return new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter);
        }
        throw VMError.shouldNotReachHere("Should only be used in hosted.");
    }
}
