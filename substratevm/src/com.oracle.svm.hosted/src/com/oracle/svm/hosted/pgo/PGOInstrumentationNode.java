/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

/**
 * A profile counter that the {@code --pgo-instrument} phases insert ({@link PGOInstrumentationPhases}).
 * <p>
 * The counters must not change how the rest of the code is compiled, so that the instrumented
 * image takes the decisions of an image built without them:
 * <ul>
 * <li>The compiler's cost models ignore them ({@link jdk.graal.compiler.nodeinfo.NodeCycles#CYCLES_IGNORED},
 * {@link jdk.graal.compiler.nodeinfo.NodeSize#SIZE_IGNORED}), for example when they decide whether to
 * unroll or duplicate code. The priority inliner, which counts nodes instead, leaves them out
 * explicitly ({@link PGOInstrumentationFeature#instrumentationNodeCount}).</li>
 * <li>They are not memory kills: the snippet each one lowers to touches only counter memory, which
 * it declares private, so no load or store of the method is ordered against a counter.</li>
 * <li>They lower only once frame states are attached to deoptimization points, after the high-tier
 * transformations, and keep their place in the control flow until then.</li>
 * </ul>
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public abstract class PGOInstrumentationNode extends FixedWithNextNode implements Lowerable, IterableNodeType {

    public static final NodeClass<PGOInstrumentationNode> TYPE = NodeClass.create(PGOInstrumentationNode.class);

    protected PGOInstrumentationNode(NodeClass<? extends PGOInstrumentationNode> c) {
        super(c, StampFactory.forVoid());
    }

    @Override
    public final void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            lowerToSnippet(tool);
        }
    }

    /** Replaces this node with the snippet that updates its counter. */
    protected abstract void lowerToSnippet(LoweringTool tool);
}
