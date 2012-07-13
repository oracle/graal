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
package com.oracle.graal.compiler.phases;

import java.util.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.extended.*;

public class ReadEliminationPhase extends Phase {
    private Queue<PhiNode> newPhis;

    @Override
    protected void run(StructuredGraph graph) {
        newPhis = new LinkedList<>();
        for (FloatingReadNode n : graph.getNodes(FloatingReadNode.class)) {
            if (isReadEliminable(n)) {
                NodeMap<ValueNode> nodeMap = n.graph().createNodeMap();
                ValueNode value = getValue(n.lastLocationAccess(), nodeMap);
                Debug.log("Eliminated memory read %1.1s and replaced with node %s", n, value);
                graph.replaceFloating(n, value);
            }
        }
        // get a proper stamp for the new phis
        while (!newPhis.isEmpty()) {
            PhiNode phi = newPhis.poll();
            if (phi.inferStamp()) {
                for (PhiNode usagePhi : phi.usages().filter(PhiNode.class)) {
                    newPhis.add(usagePhi);
                }
            }
        }
    }

    private boolean isReadEliminable(FloatingReadNode n) {
        return isWrites(n, n.lastLocationAccess(), n.graph().createNodeBitMap());
    }

    private boolean isWrites(FloatingReadNode n, Node lastLocationAccess, NodeBitMap visited) {
        if (lastLocationAccess == null) {
            return false;
        }
        if (visited.isMarked(lastLocationAccess)) {
            return true; // dataflow loops must come from Phis assume them ok until proven wrong
        }
        if (lastLocationAccess instanceof WriteNode) {
            WriteNode other = (WriteNode) lastLocationAccess;
            return other.object() == n.object() && other.location() == n.location();
        }
        if (lastLocationAccess instanceof PhiNode) {
            visited.mark(lastLocationAccess);
            for (ValueNode value : ((PhiNode) lastLocationAccess).values()) {
                if (!isWrites(n, value, visited)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private ValueNode getValue(Node lastLocationAccess, NodeMap<ValueNode> nodeMap) {
        ValueNode exisiting = nodeMap.get(lastLocationAccess);
        if (exisiting != null) {
            return exisiting;
        }
        if (lastLocationAccess instanceof WriteNode) {
            return ((WriteNode) lastLocationAccess).value();
        }
        if (lastLocationAccess instanceof PhiNode) {
            PhiNode phi = (PhiNode) lastLocationAccess;
            PhiNode newPhi = phi.graph().add(new PhiNode(PhiType.Value, phi.merge()));
            nodeMap.set(lastLocationAccess, newPhi);
            for (ValueNode value : phi.values()) {
                newPhi.addInput(getValue(value, nodeMap));
            }
            newPhis.add(newPhi);
            return newPhi;
        }
        throw GraalInternalError.shouldNotReachHere();
    }
}
