/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;

import com.oracle.svm.core.meta.SharedMethod;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * For deoptimzation testing. The node performs a deoptimization in a normally compiled method, but
 * is a no-op in the deoptimization target method.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public class TestDeoptimizeNode extends FixedWithNextNode implements Canonicalizable {
    public static final NodeClass<TestDeoptimizeNode> TYPE = NodeClass.create(TestDeoptimizeNode.class);

    public TestDeoptimizeNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (graph().method() instanceof SharedMethod) {
            SharedMethod method = (SharedMethod) graph().method();
            if (method.isDeoptTarget()) {
                /* no-op for deoptimization target methods. */
                return null;
            } else {
                /* deoptimization for all other methods. */
                return new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.TransferToInterpreter);
            }
        }
        return this;
    }
}
