/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.memory;

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.MemoryProxyNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.ProxyNode;
import org.graalvm.word.LocationIdentity;

/**
 * Memory {@code PhiNode}s merge memory dependencies at control flow merges.
 */
@NodeInfo(nameTemplate = "Phi({i#values}) {p#locationIdentity/s}", allowedUsageTypes = {InputType.Memory}, size = SIZE_0)
public final class MemoryPhiNode extends PhiNode implements SingleMemoryKill {

    public static final NodeClass<MemoryPhiNode> TYPE = NodeClass.create(MemoryPhiNode.class);
    @Input(InputType.Memory) NodeInputList<ValueNode> values;
    protected final LocationIdentity locationIdentity;

    public MemoryPhiNode(AbstractMergeNode merge, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forVoid(), merge);
        this.locationIdentity = locationIdentity;
        this.values = new NodeInputList<>(this);
    }

    public MemoryPhiNode(AbstractMergeNode merge, LocationIdentity locationIdentity, ValueNode[] values) {
        super(TYPE, StampFactory.forVoid(), merge);
        this.locationIdentity = locationIdentity;
        this.values = new NodeInputList<>(this, values);
    }

    @Override
    public InputType valueInputType() {
        return InputType.Memory;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public NodeInputList<ValueNode> values() {
        return values;
    }

    @Override
    protected String valueDescription() {
        return locationIdentity.toString();
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public PhiNode duplicateOn(AbstractMergeNode newMerge) {
        return graph().addWithoutUnique(new MemoryPhiNode(newMerge, getLocationIdentity()));
    }

    @Override
    public MemoryPhiNode duplicateWithValues(AbstractMergeNode newMerge, ValueNode... newValues) {
        return new MemoryPhiNode(newMerge, getLocationIdentity(), newValues);
    }

    @Override
    public ProxyNode createProxyFor(LoopExitNode lex) {
        return graph().addWithoutUnique(new MemoryProxyNode(this, lex, getKilledLocationIdentity()));
    }
}
