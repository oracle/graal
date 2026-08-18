/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.replacements.nodes.BasicObjectCloneNode;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase removes {@linkplain BasicObjectCloneNode} that have no usages except a single
 * {@linkplain FrameState} which is the {@linkplain BasicObjectCloneNode#stateAfter()}.
 *
 * Note that for a type to support Object.clone() it either needs to be an array or a type
 * implementing {@linkplain Cloneable}.
 */
public class ObjectCloneRemovalPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.when(!graphState.getGuardsStage().allowsFloatingGuards(), "Floating guards must be allowed");
    }

    @Override
    protected void run(StructuredGraph graph) {
        List<BasicObjectCloneNode> clones = graph.getNodes(BasicObjectCloneNode.TYPE).snapshot();
        if (clones.isEmpty()) {
            return;
        }
        /*
         * Run in "poor mans" post order to get chains of clones if possible, but do not spend too
         * much time computing a CFG and doing a proper iteration.
         */
        Collections.reverse(clones);
        clonesLoop: for (BasicObjectCloneNode clone : clones) {
            ValueNode object = clone.getObject();
            Stamp s = object.stamp(NodeView.DEFAULT);
            if (!(s instanceof ObjectStamp)) {
                // not an object stamp, be conservative.
                continue;
            }
            ObjectStamp os = (ObjectStamp) s;
            if (!os.nonNull()) {
                // we still need to preserve the null check semantic (dominating null
                // check with a pi should exist)
                continue;
            }
            ResolvedJavaType type = StampTool.typeOrNull(object);
            if (type == null) {
                // we need to ensure clone() is supported, for this we need a type
                continue;
            }
            if (!(type.isCloneableWithAllocation())) {
                assert !type.isArray() : "All array types must support clone with allocation";
                // no array, and we don't know if clone is supported, abort for this node
                continue;
            }
            for (Node usage : clone.usages()) {
                if (!(usage instanceof FrameState)) {
                    /*
                     * We have a usage of the clone operation that is not a state, i.e., the value
                     * is needed, thus we cannot remove it.
                     */
                    continue clonesLoop;
                }
                FrameState fsUsage = (FrameState) usage;
                if (fsUsage == clone.stateAfter()) {
                    /*
                     * Usage that will be deleted together with the clone operation if possible.
                     */
                    if (clone.stateAfter().hasMoreThanOneUsage()) {
                        continue clonesLoop;
                    }
                    assert clone.stateAfter().usages().first() == clone : "Clone must be the only usage";
                } else {
                    /*
                     * The cloned value is used in a framestate this means any deopt introduced
                     * later may consume this value thus we need to preserve the identity of the
                     * clone.
                     */
                    continue clonesLoop;
                }
            }
            if (clone.stateAfter() != null) {
                /*
                 * The only remaining usage of the clone is its own framestate, thus we can safely
                 * remove it since no other program location (also no deopt) will ever see the
                 * cloned value (and cannot require its identity).
                 */
                FrameState stateAfter = clone.stateAfter();
                clone.setStateAfter(null);
                stateAfter.safeDelete();
            }
            GraphUtil.removeFixedWithUnusedInputs(clone);
        }
    }

}
