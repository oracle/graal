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
package com.oracle.max.graal.compiler.loop;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;

public class Loop {
    private final LoopBeginNode loopBegin;
    private Loop parent;
    private final List<Loop> children;
    private final NodeBitMap exits;
    private final NodeBitMap directCFGNodes;
    private NodeBitMap loopVariant;
    private boolean finished;

    public Loop(LoopBeginNode loopBegin) {
        this.loopBegin = loopBegin;
        this.children = new ArrayList<Loop>(1);
        this.exits = loopBegin.graph().createNodeBitMap();
        this.directCFGNodes = loopBegin.graph().createNodeBitMap();
    }

    public LoopBeginNode loopBegin() {
        return loopBegin;
    }

    public Loop parent() {
        return parent;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished() {
        finished = true;
    }

    public List<Loop> children() {
        return children;
    }

    public NodeBitMap exits() {
        return exits;
    }

    public boolean isLoopExit(FixedNode node) {
        return exits.isMarked(node);
    }

    public boolean isChildOf(Loop l) {
        return parent == l || (parent != null && parent.isChildOf(l));
    }

    public boolean localContainsFixed(FixedNode n) {
        return directCFGNodes.isMarked(n);
    }

    public boolean containsFixed(FixedNode n) {
        if (localContainsFixed(n)) {
            return true;
        }
        for (Loop child : children()) {
            if (child.containsFixed(n)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLoopInvariant(ValueNode value) {
        if (loopVariant().isMarked(value)) {
            return false;
        }
        for (Loop child : children()) {
            if (!child.isLoopInvariant(value)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public Iterable<FixedNode> fixedNodes() {
        return (Iterable) directCFGNodes;
    }

    public void addChildren(Loop loop) {
        this.children.add(loop);
        loop.parent = this;
    }

    NodeBitMap directCFGNode() {
        return directCFGNodes;
    }

    @Override
    public String toString() {
        return "Loop " + loopBegin();
    }

    NodeBitMap loopVariant() {
        if (loopVariant == null) {
            loopVariant = loopBegin().graph().createNodeBitMap();
            NodeFlood work = loopBegin().graph().createNodeFlood();
            work.addAll(directCFGNodes);
            for (Node n : work) {
                loopVariant.mark(n);
                work.addAll(n.usages());
            }
        }
        return loopVariant;
    }
}
