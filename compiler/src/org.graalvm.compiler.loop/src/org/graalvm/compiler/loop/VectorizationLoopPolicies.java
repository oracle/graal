/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.loop;

import java.util.List;
import java.util.stream.StreamSupport;

import org.graalvm.compiler.core.common.VectorDescription;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.iterators.NodePredicates;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.Access;
import org.graalvm.compiler.nodes.memory.WriteNode;

import jdk.vm.ci.meta.MetaAccessProvider;

public class VectorizationLoopPolicies implements LoopPolicies {
    @Override
    public boolean shouldPeel(LoopEx loop, ControlFlowGraph cfg, MetaAccessProvider metaAccess) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for peeling");
    }

    @Override
    public boolean shouldFullUnroll(LoopEx loop) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for full unrolling");
    }

    @Override
    public boolean shouldPartiallyUnroll(LoopEx loop, VectorDescription vectorDescription) {
        final int minWidth = StreamSupport.stream(loop.whole().nodes().
                filter(NodePredicates.isA(ValueNode.class)).spliterator(), false).
                filter(x -> x instanceof Access).
                map(x -> {
                    if (x instanceof WriteNode) {
                        return ((WriteNode) x).getAccessStamp();
                    } else {
                        return ((ValueNode) x).stamp(NodeView.DEFAULT);
                    }
                }).
                filter(x -> x instanceof PrimitiveStamp).
                mapToInt(vectorDescription::maxVectorWidth).min().orElse(0);

        return loop.loopBegin().getUnrollFactor() < minWidth;
    }

    @Override
    public boolean shouldTryUnswitch(LoopEx loop) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for trying unswitching");
    }

    @Override
    public boolean shouldUnswitch(LoopEx loop, List<ControlSplitNode> controlSplits) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for unswitching");
    }
}
