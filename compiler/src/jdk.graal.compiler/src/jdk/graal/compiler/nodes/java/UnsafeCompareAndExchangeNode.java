/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

/**
 * Represents an atomic compare-and-swap operation. The result is the current value of the memory
 * location that was compared.
 */
@NodeInfo
public final class UnsafeCompareAndExchangeNode extends AbstractUnsafeCompareAndSwapNode {

    public static final NodeClass<UnsafeCompareAndExchangeNode> TYPE = NodeClass.create(UnsafeCompareAndExchangeNode.class);

    public UnsafeCompareAndExchangeNode(ValueNode object, ValueNode offset, ValueNode expected, ValueNode newValue, JavaKind valueKind, LocationIdentity locationIdentity,
                    MemoryOrderMode memoryOrder) {
        super(TYPE, meetInputs(expected.stamp(NodeView.DEFAULT), newValue.stamp(NodeView.DEFAULT)), object, offset, expected, newValue, valueKind, locationIdentity, memoryOrder);
    }

    private static Stamp meetInputs(Stamp expected, Stamp newValue) {
        assert expected.isCompatible(newValue);
        return expected.unrestricted().meet(newValue.unrestricted());
    }

    @Override
    protected void finishVirtualize(VirtualizerTool tool, LogicNode equalsNode, ValueNode currentValue) {
        tool.replaceWith(currentValue);
    }
}
