/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.debug.instrumentation;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.DeoptimizingFixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.java.AccessMonitorNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

/**
 * The {@code InstrumentationNode} is a place holder of the instrumentation in the original graph.
 * It is generated at the ExtractInstrumentationPhase and substituted at the
 * InlineInstrumentationPhase. It maintains an instrumentation graph which contains the
 * instrumentation nodes in the original graph (between InstrumentationBeginNode and
 * InstrumentationEndNode). Any data dependency of the instrumentation nodes will be transformed
 * into an input to the InstrumentationNode.
 */
@NodeInfo
public class InstrumentationNode extends DeoptimizingFixedWithNextNode implements Virtualizable {

    public static final NodeClass<InstrumentationNode> TYPE = NodeClass.create(InstrumentationNode.class);

    @OptionalInput(value = InputType.Association) protected ValueNode target;
    @OptionalInput protected NodeInputList<ValueNode> weakDependencies;

    protected StructuredGraph instrumentationGraph;
    protected final int offset;

    public InstrumentationNode(ValueNode target, int offset) {
        super(TYPE, StampFactory.forVoid());

        this.target = target;
        this.offset = offset;
        this.weakDependencies = new NodeInputList<>(this);
    }

    public ValueNode target() {
        return target;
    }

    public int offset() {
        return offset;
    }

    public StructuredGraph instrumentationGraph() {
        return instrumentationGraph;
    }

    public void setInstrumentationGraph(StructuredGraph graph) {
        this.instrumentationGraph = graph;
    }

    public void addWeakDependency(ValueNode input) {
        weakDependencies.add(input);
    }

    public ValueNode getWeakDependency(int index) {
        return weakDependencies.get(index);
    }

    public NodeInputList<ValueNode> getWeakDependencies() {
        return weakDependencies;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        // InstrumentationNode allows non-materialized inputs. During the inlining of the
        // InstrumentationNode, non-materialized inputs will be replaced by null.
        if (target != null) {
            if (target instanceof AccessMonitorNode) {
                AccessMonitorNode monitor = (AccessMonitorNode) target;
                ValueNode alias = tool.getAlias(monitor.object());
                if (alias instanceof VirtualObjectNode) {
                    tool.replaceFirstInput(target, graph().addWithoutUnique(new MonitorProxyNode(null, monitor.getMonitorId())));
                }
            } else if (!(target instanceof VirtualObjectNode)) {
                ValueNode alias = tool.getAlias(target);
                if (alias instanceof VirtualObjectNode) {
                    tool.replaceFirstInput(target, alias);
                }
            }
        }

        for (ValueNode input : weakDependencies) {
            if (input instanceof VirtualObjectNode) {
                continue;
            }
            ValueNode alias = tool.getAlias(input);
            if (alias instanceof VirtualObjectNode) {
                tool.replaceFirstInput(input, alias);
            }
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

}
