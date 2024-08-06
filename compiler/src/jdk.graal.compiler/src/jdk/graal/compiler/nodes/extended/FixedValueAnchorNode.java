/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0, allowedUsageTypes = {InputType.Anchor})
public class FixedValueAnchorNode extends FixedWithNextNode implements LIRLowerable, ValueProxy, GuardingNode, Canonicalizable, AnchoringNode {
    public static final NodeClass<FixedValueAnchorNode> TYPE = NodeClass.create(FixedValueAnchorNode.class);

    @OptionalInput ValueNode object;

    public ValueNode object() {
        return object;
    }

    protected FixedValueAnchorNode(NodeClass<? extends FixedValueAnchorNode> c, ValueNode object) {
        super(c, object == null ? StampFactory.forVoid() : object.stamp(NodeView.DEFAULT));
        this.object = object;
    }

    public FixedValueAnchorNode(ValueNode object) {
        this(TYPE, object);
    }

    @Override
    public boolean inferStamp() {
        if (object != null) {
            return updateStamp(stamp.join(object.stamp(NodeView.DEFAULT)));
        } else {
            return false;
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        if (object instanceof FixedValueAnchorNode && ((FixedValueAnchorNode) object).next == this) {
            return object;
        }
        return this;
    }

    @NodeIntrinsic
    public static native Object getObject(Object object);

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generator.operand(object));
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }

    @Override
    public GuardingNode getGuard() {
        return this;
    }

}
