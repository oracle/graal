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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

public class BeginNode extends AbstractStateSplit implements LIRLowerable, Canonicalizable {
    public BeginNode() {
        super(StampFactory.illegal());
    }

    public static BeginNode begin(FixedNode with) {
        if (with instanceof BeginNode) {
            return (BeginNode) with;
        }
        BeginNode begin =  with.graph().add(new BeginNode());
        begin.setNext(with);
        return begin;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> debugProperties = super.getDebugProperties();
        debugProperties.put("shortName", "B");
        return debugProperties;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        FixedNode prev = (FixedNode) this.predecessor();
        if (prev == null) {
            // This is the start node.
            return this;
        } else if (prev instanceof ControlSplitNode) {
            // This begin node is necessary.
            return this;
        } else {
            // This begin node can be removed and all guards moved up to the preceding begin node.
            Node prevBegin = prev;
            while (!(prevBegin instanceof BeginNode)) {
                prevBegin = prevBegin.predecessor();
            }
            this.replaceAtUsages(prevBegin);
            return next();
        }
    }

    @Override
    public boolean verify() {
        assertTrue(predecessor() != null || this == ((StructuredGraph) graph()).start() || this instanceof MergeNode, "begin nodes must be connected");
        return super.verify();
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        // nop
    }
}
