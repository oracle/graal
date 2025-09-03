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
package com.oracle.svm.hosted.webimage.codegen.phase;

import java.util.List;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Utility functions for expanding control sink nodes.
 */
class ExpandControlSinkUtil {
    /**
     * Distribute the given controlSinkNode as described above if possible.
     */
    static <T extends ControlSinkNode> void distribute(T controlSinkNode, StructuredGraph g) {
        assert controlSinkNode instanceof ReturnNode || controlSinkNode instanceof UnwindNode : controlSinkNode;
        Node predecessor = controlSinkNode.predecessor();

        if (predecessor instanceof MergeNode) {
            MergeNode merge = (MergeNode) predecessor;
            List<EndNode> ends = merge.forwardEnds();

            assert ends != null;

            /*
             * Only cases with at most 1 phiNode are handled because otherwise the mergeNode cannot
             * simply be deleted. This is because the second phiNode would be used by another node
             * after the merge node or it would be used in some computation for the control sink
             * node. In either case we would need to duplicate code.
             */
            if (merge.phis().count() > 1) {
                return;
            }
            PhiNode phi = null;
            ValueNode controlSinkInput = getInput(controlSinkNode);
            if (merge.phis().count() == 1) {
                phi = merge.phis().first();
                /*
                 * If there are computations done between the phiNode and control sink node, we do
                 * not proceed because this would require code duplication.
                 */
                if (controlSinkInput != null && !controlSinkInput.equals(phi)) {
                    return;
                }
                phi.replaceAtUsages(null);
            }
            for (AbstractEndNode end : ends) {
                ValueNode newInput = getNewInput(controlSinkInput, merge, end, phi);
                replaceControlSink(controlSinkNode, end, newInput, g);
            }
            merge.replaceAtUsages(null);
            if (phi != null) {
                phi.safeDelete();
            }
            merge.safeDelete();
            controlSinkNode.safeDelete();
        }
    }

    /**
     * Get the input for the new control sink node. If the control sink node did not have any input
     * or returned something other than the phi, just return the input of the old control sink node.
     * Otherwise, take the appropriate value from the phi node.
     */
    private static ValueNode getNewInput(ValueNode oldControlSinkInput, MergeNode merge, AbstractEndNode end, PhiNode phi) {
        if (oldControlSinkInput == null || oldControlSinkInput != phi) {
            return oldControlSinkInput;
        } else {
            int phiIndex = merge.phiPredecessorIndex(end);
            return phi.valueAt(phiIndex);
        }
    }

    /**
     * Replace an endNode of a merge with a control sink node.
     */
    private static <T extends ControlSinkNode> void replaceControlSink(T controlSinkNode, AbstractEndNode end, ValueNode input, StructuredGraph g) {
        ControlSinkNode newControlSinkNode = getNewControlSinkNode(controlSinkNode, input, g);

        ((FixedWithNextNode) end.predecessor()).setNext(newControlSinkNode);

        // remove the end node
        end.replaceAtUsages(null);
        end.safeDelete();
    }

    /**
     * Creates a new control sink node of the given type and input and adds it to the graph.
     */
    private static <T extends ControlSinkNode> ControlSinkNode getNewControlSinkNode(T controlSinkNode, ValueNode input, StructuredGraph g) {
        ControlSinkNode newControlSinkNode;
        if (controlSinkNode instanceof ReturnNode) {
            newControlSinkNode = new ReturnNode(input);
        } else {
            newControlSinkNode = new UnwindNode(input);
        }
        return g.addWithoutUnique(newControlSinkNode);
    }

    private static <T extends ControlSinkNode> ValueNode getInput(T controlSinkNode) {
        if (controlSinkNode instanceof ReturnNode) {
            return ((ReturnNode) controlSinkNode).result();
        } else {
            return ((UnwindNode) controlSinkNode).exception();
        }
    }

}
