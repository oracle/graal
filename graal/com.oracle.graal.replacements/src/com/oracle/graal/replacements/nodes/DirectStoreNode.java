/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StateSplit;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.UnsafeStoreNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and takes a computed address instead of an object.
 */
@NodeInfo
public final class DirectStoreNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<DirectStoreNode> TYPE = NodeClass.create(DirectStoreNode.class);
    @Input protected ValueNode address;
    @Input protected ValueNode value;
    protected final JavaKind kind;

    public DirectStoreNode(ValueNode address, ValueNode value, JavaKind kind) {
        super(TYPE, StampFactory.forVoid());
        this.address = address;
        this.value = value;
        this.kind = kind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value v = gen.operand(value);
        LIRKind lirKind = gen.getLIRGeneratorTool().target().getLIRKind(kind);
        gen.getLIRGeneratorTool().emitStore(lirKind, gen.operand(address), v, null);
    }

    public ValueNode getAddress() {
        return address;
    }

    public ValueNode getValue() {
        return value;
    }

    @NodeIntrinsic
    public static native void storeBoolean(long address, boolean value, @ConstantNodeParameter JavaKind kind);
}
