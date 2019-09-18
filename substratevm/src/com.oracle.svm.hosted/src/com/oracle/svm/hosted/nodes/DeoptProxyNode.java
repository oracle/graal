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

import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ValueNumberable;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.LimitedValueProxy;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import com.oracle.svm.core.deopt.Deoptimizer;

import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Wraps locals and bytecode stack elements at deoptimization points. DeoptProxyNodes are inserted
 * in deoptimization target methods to avoid global value numbering and rescheduling of local
 * variable (and stack) accesses across deoptimization points.
 * <p>
 * This is needed to ensure that the values, which are set by the {@link Deoptimizer} at the
 * deoptimization point, are really read from their locations (and not hold in a temporary register,
 * etc.)
 */
@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
public final class DeoptProxyNode extends FloatingNode implements LimitedValueProxy, ValueNumberable, LIRLowerable, Canonicalizable, IterableNodeType, ArrayLengthProvider {
    public static final NodeClass<DeoptProxyNode> TYPE = NodeClass.create(DeoptProxyNode.class);

    /**
     * The link to the deoptimization point to prevent rescheduling of this node.
     */
    @Input(InputType.Anchor) protected ValueNode proxyPoint;

    /**
     * The original value, e.g. a {@link ParameterNode}
     */
    @Input protected ValueNode value;

    /**
     * A unique index for the deoptimization point. It prevents global value numbering across
     * deoptimization points, but enabled global value numbering between two deoptimization points.
     */
    protected final int deoptIndex;

    protected DeoptProxyNode(ValueNode value, ValueNode proxyPoint, int deoptIndex) {
        super(TYPE, value.stamp(NodeView.DEFAULT));
        this.value = value;
        this.proxyPoint = proxyPoint;
        this.deoptIndex = deoptIndex;
    }

    public static ValueNode create(ValueNode value, ValueNode proxyPoint, int deoptIndex) {
        ValueNode synonym = findSynonym(value);
        if (synonym != null) {
            return synonym;
        }
        return new DeoptProxyNode(value, proxyPoint, deoptIndex);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(value.stamp(NodeView.DEFAULT));
    }

    @Override
    public ValueNode getOriginalNode() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.operand(value));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ValueNode synonym = findSynonym(value);
        return synonym != null ? synonym : this;
    }

    /**
     * No need to proxy constants.
     */
    private static ValueNode findSynonym(ValueNode value) {
        return value.isConstant() ? value : null;
    }

    public boolean hasProxyPoint() {
        return proxyPoint != null;
    }

    @Override
    public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        ValueNode length = GraphUtil.arrayLength(value, mode, constantReflection);
        return length != null && length.isConstant() ? length : null;
    }
}
