/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;

public class ReadEliminationPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (FloatingReadNode n : graph.getNodes(FloatingReadNode.class)) {
            if (isReadEliminable(n)) {
                NodeMap<ValueNode> nodeMap = n.graph().createNodeMap();
                ValueNode value = getValue(n, n.getLastLocationAccess(), nodeMap);
                Debug.log("Eliminated memory read %1.1s and replaced with node %s", n, value);
                graph.replaceFloating(n, value);
            }
        }
    }

    private static boolean isReadEliminable(FloatingReadNode n) {
        return isWrites(n, n.getLastLocationAccess(), n.graph().createNodeBitMap());
    }

    private static boolean isWrites(FloatingReadNode n, MemoryNode lastLocationAccess, NodeBitMap visited) {
        if (lastLocationAccess == null) {
            return false;
        }
        if (visited.isMarked(ValueNodeUtil.asNode(lastLocationAccess))) {
            return true; // dataflow loops must come from Phis assume them ok until proven wrong
        }
        if (lastLocationAccess instanceof ProxyNode) {
            return isWrites(n, (MemoryNode) ((ProxyNode) lastLocationAccess).value(), visited);
        }
        if (lastLocationAccess instanceof WriteNode) {
            WriteNode other = (WriteNode) lastLocationAccess;
            return other.object() == n.object() && other.location() == n.location();
        }
        if (lastLocationAccess instanceof PhiNode) {
            visited.mark(ValueNodeUtil.asNode(lastLocationAccess));
            for (ValueNode value : ((PhiNode) lastLocationAccess).values()) {
                if (!isWrites(n, (MemoryNode) value, visited)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static ValueNode getValue(FloatingReadNode n, MemoryNode lastLocationAccess, NodeMap<ValueNode> nodeMap) {
        ValueNode exisiting = nodeMap.get(ValueNodeUtil.asNode(lastLocationAccess));
        if (exisiting != null) {
            return exisiting;
        }
        if (lastLocationAccess instanceof MemoryProxyNode) {
            MemoryProxyNode proxy = (MemoryProxyNode) lastLocationAccess;
            ValueNode value = getValue(n, proxy.getOriginalMemoryNode(), nodeMap);
            return ProxyNode.forValue(value, proxy.proxyPoint(), proxy.graph());
        }
        if (lastLocationAccess instanceof WriteNode) {
            return ((WriteNode) lastLocationAccess).value();
        }
        if (lastLocationAccess instanceof PhiNode) {
            PhiNode phi = (PhiNode) lastLocationAccess;
            PhiNode newPhi = phi.graph().addWithoutUnique(new PhiNode(n.kind(), phi.merge()));
            nodeMap.set(phi, newPhi);
            for (ValueNode value : phi.values()) {
                newPhi.addInput(getValue(n, (MemoryNode) value, nodeMap));
            }
            return newPhi;
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
