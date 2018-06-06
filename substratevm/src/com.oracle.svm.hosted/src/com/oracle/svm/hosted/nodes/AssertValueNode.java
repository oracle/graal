/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.nodes;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

/**
 * Run-time check that the actual {@link #input} passes some run-time checks, which are defined in a
 * subclass.
 */
@NodeInfo
public abstract class AssertValueNode extends FixedWithNextNode implements Canonicalizable, Lowerable {
    public static final NodeClass<AssertValueNode> TYPE = NodeClass.create(AssertValueNode.class);

    @Input protected ValueNode input;

    protected static void insert(ValueNode input, AssertValueNode assertionNode) {
        StructuredGraph graph = input.graph();

        /* Find the insertion point where we want to add the assertion node. */
        FixedWithNextNode insertionPoint;
        if (input instanceof ParameterNode) {
            insertionPoint = graph.start();
        } else if (input instanceof InvokeWithExceptionNode) {
            insertionPoint = ((InvokeWithExceptionNode) input).next();
        } else if (input instanceof FixedWithNextNode) {
            insertionPoint = (FixedWithNextNode) input;
        } else {
            throw shouldNotReachHere("Node is not fixed: " + input);
        }

        /*
         * When inserting after an invoke that is also a loop exit, a proxy node is inserted between
         * the invoke and every usage. We need to be after this proxy node to avoid unschedulable
         * graphs.
         */
        ProxyNode proxyUsage = null;
        boolean otherUsages = false;
        for (Node usage : input.usages()) {
            if (usage instanceof ProxyNode && ((ProxyNode) usage).proxyPoint() == insertionPoint) {
                assert proxyUsage == null : "can have only one proxy";
                proxyUsage = (ProxyNode) usage;
            } else if (!(usage instanceof FrameState)) {
                otherUsages = true;
            }
        }
        assert proxyUsage == null || otherUsages == false : "cannot have other usages when having a proxy usage";
        ValueNode assertInput = proxyUsage != null ? proxyUsage : input;

        /*
         * Replace the object at usages. We do not process usages at the frame state because it
         * could be the stateAfter() of the insertion point. Since frame states are not doing
         * anything in code, this is not a loss of assertion precision.
         */
        for (Node usage : assertInput.usages().snapshot()) {
            if (!(usage instanceof FrameState)) {
                usage.replaceFirstInput(assertInput, assertionNode);
            }
        }

        /*
         * Set the input object of the assertion node, now that all other usages have been replaced.
         */
        assertionNode.updateUsages(assertionNode.input, assertInput);
        assertionNode.input = assertInput;
        /* Insert assertion node in graph. */
        graph.addAfterFixed(insertionPoint, assertionNode);
    }

    protected AssertValueNode(NodeClass<? extends AssertValueNode> c, Stamp stamp) {
        super(c, stamp);
    }

    public ValueNode getInput() {
        return input;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp(NodeView.DEFAULT).join(input.stamp(NodeView.DEFAULT)));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool) {
        /*
         * During canonicalization the graph might contain dead code, i.e., assertion nodes that are
         * known to fail but that will be removed later on by dead code elimination. Therefore, we
         * do not report compile time errors here, but only during lowering.
         */
        if (alwaysHolds(false)) {
            return getInput();
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (!alwaysHolds(true)) {
            tool.getLowerer().lower(this, tool);
        }
    }

    protected abstract boolean alwaysHolds(boolean reportError);
}
