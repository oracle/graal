/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodeinfo.NodeInfo;

import jdk.vm.ci.meta.JavaKind;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a GuardedValueNode
 * is used to keep the nodes depending on guards inside a loop during speculative guard movement.
 *
 * A GuardedValueNode will only go away if its guard is null or {@link StructuredGraph#start()}.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class GuardedValueNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, Canonicalizable, ValueProxy {

    public static final NodeClass<GuardedValueNode> TYPE = NodeClass.create(GuardedValueNode.class);
    @Input ValueNode object;

    public GuardedValueNode(ValueNode object, GuardingNode guard) {
        super(TYPE, object.stamp(NodeView.DEFAULT), guard);
        this.object = object;
    }

    public ValueNode object() {
        return object;
    }

    public static ValueNode create(ValueNode object, GuardingNode guard) {
        ValueNode canonical = canonicalize(guard, object);
        if (canonical != null) {
            return canonical;
        }
        return new GuardedValueNode(object, guard);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (object.getStackKind() != JavaKind.Void && object.getStackKind() != JavaKind.Illegal) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(object().stamp(NodeView.DEFAULT));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            tool.replaceWithVirtual((VirtualObjectNode) alias);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node canonical = canonicalize(guard, object);
        if (canonical != null && canonical != this) {
            return canonical;
        }
        return this;
    }

    static ValueNode canonicalize(GuardingNode guard, ValueNode object) {
        if (guard == null) {
            return object;
        }
        if (object instanceof ConstantNode) {
            return object;
        }
        return null;
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }
}
