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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code Anchor} instruction represents the end of a block with an unconditional jump to another block.
 */
public final class Anchor extends FixedNodeWithNext {

    @Input    private final NodeInputList<GuardNode> guards = new NodeInputList<GuardNode>(this);

    /**
     * Constructs a new Anchor instruction.
     * @param graph
     */
    public Anchor(Graph graph) {
        super(CiKind.Illegal, graph);
    }

    public void addGuard(GuardNode x) {
        guards.add(x);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitAnchor(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("anchor ").print(next());
    }

    public Iterable<GuardNode> happensAfterGuards() {
        final Iterator<Node> usages = this.usages().iterator();
        return new Iterable<GuardNode>() {
            public Iterator<GuardNode> iterator() {
                return new Iterator<GuardNode>() {
                    private GuardNode next;
                    @Override
                    public boolean hasNext() {
                        if (next == null) {
                            while (usages.hasNext()) {
                                Node cur = usages.next();
                                if (cur instanceof GuardNode) {
                                    next = ((GuardNode) cur);
                                    break;
                                }
                            }
                        }
                        return next != null;
                    }

                    @Override
                    public GuardNode next() {
                        GuardNode result = next;
                        next = null;
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new IllegalStateException();
                    }
                };
            }
        };
    }
}
