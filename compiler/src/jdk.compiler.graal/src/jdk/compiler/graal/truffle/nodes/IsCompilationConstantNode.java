/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.truffle.nodes;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.extended.BoxNode;
import jdk.compiler.graal.nodes.spi.Lowerable;
import jdk.compiler.graal.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
public final class IsCompilationConstantNode extends FloatingNode implements Lowerable, Canonicalizable {
    public static final NodeClass<IsCompilationConstantNode> TYPE = NodeClass.create(IsCompilationConstantNode.class);

    @Input ValueNode value;

    public IsCompilationConstantNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.value = value;
    }

    @Override
    public void lower(LoweringTool tool) {
        ValueNode result;
        ValueNode synonym = findSynonym(value);
        if (synonym != null && synonym.isConstant()) {
            result = synonym;
        } else {
            result = ConstantNode.forBoolean(false, graph());
        }
        assert result != null;
        replaceAtUsagesAndDelete(result);
    }

    public static ValueNode create(ValueNode value) {
        ValueNode synonym = findSynonym(value);
        if (synonym != null) {
            return synonym;
        }
        return new IsCompilationConstantNode(value);
    }

    public static ValueNode findSynonym(ValueNode value) {
        if (value instanceof BoxNode) {
            return create(((BoxNode) value).getValue());
        }
        if (value.isConstant()) {
            return ConstantNode.forBoolean(true);
        }
        return null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node synonym = findSynonym(value);
        if (synonym != null) {
            return synonym;
        }
        return this;
    }

    @NodeIntrinsic
    public static native boolean check(Object value);
}
