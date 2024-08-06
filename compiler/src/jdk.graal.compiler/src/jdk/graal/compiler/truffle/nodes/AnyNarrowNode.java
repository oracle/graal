/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * The {@code AnyNarrowNode} narrows an i64 integer to an i32 integer. This node itself cannot
 * produce code, it needs to be converted to a {@link NarrowNode} explicitly.
 */
@NodeInfo(cycles = CYCLES_1, size = NodeSize.SIZE_1)
public final class AnyNarrowNode extends FloatingNode implements IterableNodeType {

    public static final NodeClass<AnyNarrowNode> TYPE = NodeClass.create(AnyNarrowNode.class);

    public static final int INPUT_BITS = 64;
    public static final int OUTPUT_BITS = 32;

    @Input protected ValueNode value;

    public AnyNarrowNode(ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        Stamp stamp2 = value.stamp(NodeView.DEFAULT);
        assert stamp2 instanceof IntegerStamp : stamp2;
        this.value = value;
    }

    public ValueNode getValue() {
        return value;
    }
}
