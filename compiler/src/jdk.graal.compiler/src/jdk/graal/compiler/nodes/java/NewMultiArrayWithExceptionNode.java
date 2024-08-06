/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_8, cyclesRationale = "tlab alloc + header init", size = SIZE_8)
public class NewMultiArrayWithExceptionNode extends AllocateWithExceptionNode {

    public static final NodeClass<NewMultiArrayWithExceptionNode> TYPE = NodeClass.create(NewMultiArrayWithExceptionNode.class);
    @Input protected NodeInputList<ValueNode> dimensions;
    protected final ResolvedJavaType type;

    @SuppressWarnings("this-escape")
    public NewMultiArrayWithExceptionNode(ResolvedJavaType type, NodeList<ValueNode> dimensions) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(type)));
        this.type = type;
        this.dimensions = new NodeInputList<>(this, dimensions);
        assert NumUtil.assertPositiveInt(dimensions.count());
        assert type.isArray();

    }

    @SuppressWarnings("this-escape")
    public NewMultiArrayWithExceptionNode(ResolvedJavaType type, ValueNode[] dimensions) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(type)));
        this.type = type;
        this.dimensions = new NodeInputList<>(this, dimensions);
        assert NumUtil.assertPositiveInt(dimensions.length);
        assert type.isArray();
    }

    public ValueNode dimension(int index) {
        return dimensions.get(index);
    }

    public int dimensionCount() {
        return dimensions.size();
    }

    public NodeList<ValueNode> dimensions() {
        return dimensions;
    }

    public ResolvedJavaType type() {
        return type;
    }

}
