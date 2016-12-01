/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes.profiling;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.DeoptimizingFixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

@NodeInfo
public class ProfileNode extends DeoptimizingFixedWithNextNode implements Lowerable {
    public static class Options {
        @Option(help = "Control probabilistic profiling on AMD64", type = OptionType.Expert)//
        public static final OptionValue<Boolean> ProbabilisticProfiling = new OptionValue<>(true);
    }

    public static final NodeClass<ProfileNode> TYPE = NodeClass.create(ProfileNode.class);

    protected ResolvedJavaMethod method;

    // Only used if ProbabilisticProfiling == true and may be ignored by lowerer.
    @OptionalInput protected ValueNode random;

    // logarithm base 2 of the profile probability
    protected int probabilityLog;

    protected ProfileNode(NodeClass<? extends DeoptimizingFixedWithNextNode> c, ResolvedJavaMethod method, int probabilityLog) {
        super(c, StampFactory.forVoid());
        this.method = method;
        this.probabilityLog = probabilityLog;
    }

    public ProfileNode(ResolvedJavaMethod method, int probabilityLog) {
        super(TYPE, StampFactory.forVoid());
        this.method = method;
        this.probabilityLog = probabilityLog;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public ResolvedJavaMethod getProfiledMethod() {
        return method;
    }

    public ValueNode getRandom() {
        return random;
    }

    public void setRandom(ValueNode r) {
        updateUsages(random, r);
        this.random = r;
    }

    /**
     * Get the logarithm base 2 of the profile probability.
     */
    public int getProbabilityLog() {
        return probabilityLog;
    }

    /**
     * Gathers all the {@link ProfileNode}s that are inputs to the
     * {@linkplain StructuredGraph#getNodes() live nodes} in a given graph.
     */
    public static NodeIterable<ProfileNode> getProfileNodes(StructuredGraph graph) {
        return graph.getNodes().filter(ProfileNode.class);
    }
}
