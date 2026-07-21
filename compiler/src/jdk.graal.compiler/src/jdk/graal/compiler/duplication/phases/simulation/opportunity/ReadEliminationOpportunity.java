/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.phases.simulation.opportunity;

import java.util.ArrayList;

import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.util.GraphUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ResolvedJavaField;

public class ReadEliminationOpportunity extends DuplicationOpportunity {

    private static final CounterKey eliminatedUnsafeOtherLocation = DebugContext.counter("ReadEliminationOpportunity_EliminatedUnsafeOtherLocation");

    /**
     * Maximum number of fixed nodes to consider for read elimination opportunities.
     */
    private static final int MAX_READ_ELIM_WALKS = 10;

    public static ReadEliminationOpportunity getReadEliminationOpportunity(AbstractMergeNode merge, EndNode end) {
        ArrayList<Node> locations = null;
        FixedNode current = merge;
        ReadEliminationOpportunity opportunity = null;
        int nodesWalked = 0;
        while (current instanceof FixedWithNextNode) {
            if (current instanceof AccessFieldNode ||
                            (current instanceof UnsafeAccessNode && ((UnsafeAccessNode) current).getLocationIdentity().isSingle())) {
                if (locations == null) {
                    locations = new ArrayList<>();
                }
                locations.add(current);
                if (opportunity == null) {
                    opportunity = new ReadEliminationOpportunity();
                }
                opportunity.lastOptimizableNode = current;
            }
            if (MemoryKill.isMemoryKill(current) || nodesWalked++ > MAX_READ_ELIM_WALKS) {
                break;
            }
            current = ((FixedWithNextNode) current).next();
        }
        if (locations != null) {
            nodesWalked = 0;
            current = end;
            current = (FixedNode) current.predecessor();
            while (current != null) {
                if (current instanceof AccessFieldNode || current instanceof UnsafeAccessNode) {
                    for (Node candidate : locations) {
                        if (opportunity == null) {
                            opportunity = new ReadEliminationOpportunity();
                        }
                        opportunity.processMaybeRedundantReads(current, candidate);
                    }
                } else if (MemoryKill.isMemoryKill(current) || nodesWalked++ > MAX_READ_ELIM_WALKS) {
                    break;
                }
                current = (FixedNode) current.predecessor();
            }
            return opportunity != null ? opportunity : DEFAULT_RE_OPPORTUNITY;
        }
        return DEFAULT_RE_OPPORTUNITY;
    }

    private void processMaybeRedundantReads(Node a, Node b) {
        assert a instanceof AccessFieldNode || a instanceof UnsafeAccessNode : a;
        assert b instanceof AccessFieldNode || b instanceof UnsafeAccessNode : b;
        if (a instanceof AccessFieldNode) {
            // a is safe
            if (b instanceof AccessFieldNode) {
                // a is safe && b is safe
                processMaybeRedundantSafeAccess((AccessFieldNode) a, (AccessFieldNode) b);
            } else {
                /*
                 * a is safe && b is unsafe: There is no need in trying to remove the unsafe object
                 * based on resolving the concrete field, if there is one, this will be done by the
                 * canonicalizer.
                 */
            }
        } else {
            // a is unsafe
            if (b instanceof AccessFieldNode) {
                /*
                 * a is unsafe && b is safe: There is no need in trying to remove the unsafe object
                 * based on resolving the concrete field, if there is one, this will be done by the
                 * canonicalizer.
                 */
            } else {
                // a is unsafe && b is unsafe
                processMaybeRedundantUnsafeAccess((UnsafeAccessNode) a, (UnsafeAccessNode) b);
            }
        }
    }

    private void processMaybeRedundantSafeAccess(AccessFieldNode access1, AccessFieldNode access2) {
        ValueNode object1 = GraphUtil.unproxify(access1.object());
        ValueNode object2 = GraphUtil.unproxify(access2.object());
        if (object1 == object2) {
            ResolvedJavaField r1 = access1.field();
            ResolvedJavaField r2 = access2.field();
            if (r1.equals(r2)) {
                cyclesSaved += access2.estimatedNodeCycles().value;
            }
        }
    }

    private void processMaybeRedundantUnsafeAccess(UnsafeAccessNode unsafeAccess1, UnsafeAccessNode unsafeAccess2) {
        /*
         * Try to eliminate them based on their identity, object and location.
         */
        LocationIdentity l1 = unsafeAccess1.getLocationIdentity();
        LocationIdentity l2 = unsafeAccess2.getLocationIdentity();
        if (l1.isSingle() && l1.equals(l2)) {
            ValueNode object1 = GraphUtil.unproxify(unsafeAccess1.object());
            ValueNode object2 = GraphUtil.unproxify(unsafeAccess2.object());
            if (object1 == object2) {
                ValueNode offset1 = GraphUtil.unproxify(unsafeAccess1.offset());
                ValueNode offset2 = GraphUtil.unproxify(unsafeAccess2.offset());
                if (offset1 == offset2) {
                    if (l1.equals(l2)) {
                        cyclesSaved += unsafeAccess2.estimatedNodeCycles().value;
                        eliminatedUnsafeOtherLocation.increment(unsafeAccess1.getDebug());
                    }
                }
            }
        }
    }

    public static final ReadEliminationOpportunity DEFAULT_RE_OPPORTUNITY = new ReadEliminationOpportunity();

}
