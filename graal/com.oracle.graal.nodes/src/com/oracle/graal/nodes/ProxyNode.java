/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;

/**
 * A proxy is inserted at loop exits for any value that is created inside the loop (i.e. was not
 * live on entry to the loop) and is (potentially) used after the loop.
 */
@NodeInfo
public abstract class ProxyNode extends FloatingNode implements IterableNodeType, ValueNumberable {

    @Input(InputType.Association) private BeginNode proxyPoint;

    public ProxyNode(Stamp stamp, BeginNode proxyPoint) {
        super(stamp);
        assert proxyPoint != null;
        this.proxyPoint = proxyPoint;
    }

    public abstract ValueNode value();

    public BeginNode proxyPoint() {
        return proxyPoint;
    }

    @Override
    public boolean verify() {
        assert value() != null;
        assert proxyPoint != null;
        assert !(value() instanceof ProxyNode) || ((ProxyNode) value()).proxyPoint != proxyPoint;
        return super.verify();
    }

    public static ValueProxyNode forValue(ValueNode value, BeginNode exit, StructuredGraph graph) {
        return graph.unique(new ValueProxyNode(value, exit));
    }

    public static GuardProxyNode forGuard(GuardingNode value, BeginNode exit, StructuredGraph graph) {
        return graph.unique(new GuardProxyNode(value, exit));
    }
}
