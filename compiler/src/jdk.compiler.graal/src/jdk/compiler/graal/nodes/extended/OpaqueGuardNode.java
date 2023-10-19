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
package jdk.compiler.graal.nodes.extended;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.graph.spi.NodeWithIdentity;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ValueNode;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_0;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class OpaqueGuardNode extends OpaqueNode implements NodeWithIdentity, GuardingNode {
    public static final NodeClass<OpaqueGuardNode> TYPE = NodeClass.create(OpaqueGuardNode.class);

    @Input(InputType.Guard) private ValueNode value;

    public OpaqueGuardNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public void setValue(ValueNode value) {
        this.updateUsages(this.value, value);
        this.value = value;
    }

}
