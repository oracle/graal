/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.java;

import java.util.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;

/**
 * The {@code AbstractNewObjectNode} is the base class for the new instance and new array nodes.
 */
@NodeInfo
public class AbstractNewObjectNode extends DeoptimizingFixedWithNextNode implements Simplifiable, Lowerable {

    protected final boolean fillContents;

    /**
     * Constructs a new AbstractNewObjectNode.
     *
     * @param stamp the stamp of the newly created object
     * @param fillContents determines if the object's contents should be initialized to zero/null.
     */
    public static AbstractNewObjectNode create(Stamp stamp, boolean fillContents) {
        return USE_GENERATED_NODES ? new AbstractNewObjectNodeGen(stamp, fillContents) : new AbstractNewObjectNode(stamp, fillContents);
    }

    protected AbstractNewObjectNode(Stamp stamp, boolean fillContents) {
        super(stamp);
        this.fillContents = fillContents;
    }

    /**
     * @return <code>true</code> if the object's contents should be initialized to zero/null.
     */
    public boolean fillContents() {
        return fillContents;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        // poor man's escape analysis: check if the object can be trivially removed
        for (Node usage : usages()) {
            if (usage instanceof FixedValueAnchorNode) {
                if (((FixedValueAnchorNode) usage).usages().isNotEmpty()) {
                    return;
                }
            } else if (usage instanceof WriteNode) {
                if (((WriteNode) usage).object() != this || usage.usages().isNotEmpty()) {
                    // we would need to fix up the memory graph if the write has usages
                    return;
                }
            } else {
                return;
            }
        }
        for (Node usage : usages().distinct().snapshot()) {
            List<Node> snapshot = usage.inputs().snapshot();
            graph().removeFixed((FixedWithNextNode) usage);
            for (Node input : snapshot) {
                tool.removeIfUnused(input);
            }
        }
        List<Node> snapshot = inputs().snapshot();
        graph().removeFixed(this);
        for (Node input : snapshot) {
            tool.removeIfUnused(input);
        }
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
