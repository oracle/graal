/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.calc.FloatingNode;

/**
 * Proxy node that is used in OSR. This node drops the stamp information from the value, since the
 * types we see during OSR may be too precise (if a branch was not parsed for example).
 */
@NodeInfo(nameTemplate = "EntryProxy({i#value})", cycles = CYCLES_0, size = SIZE_0)
public final class EntryProxyNode extends FloatingNode implements ValueProxy {

    public static final NodeClass<EntryProxyNode> TYPE = NodeClass.create(EntryProxyNode.class);
    @Input(Association) EntryMarkerNode proxyPoint;
    @Input ValueNode value;

    public EntryProxyNode(ValueNode value, EntryMarkerNode proxyPoint) {
        super(TYPE, value.stamp(NodeView.DEFAULT).unrestricted());
        this.value = value;
        this.proxyPoint = proxyPoint;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public ValueNode getOriginalNode() {
        return value();
    }

    @Override
    public GuardingNode getGuard() {
        return proxyPoint;
    }
}
