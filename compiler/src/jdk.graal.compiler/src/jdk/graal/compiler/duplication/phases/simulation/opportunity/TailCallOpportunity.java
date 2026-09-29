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

import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.PhiNode;

/** Captures the benefit of giving merged paths distinct indirect tail-call sites. */
public final class TailCallOpportunity extends DuplicationOpportunity {

    private static final CounterKey tailCallOpportunities = DebugContext.counter("Duplication_TailCallOpportunities");

    private static final TailCallOpportunity NO_OPPORTUNITY = new TailCallOpportunity();

    public static boolean isTailCallMerge(MergeNode merge) {
        for (PhiNode phi : merge.phis()) {
            for (Node usage : phi.usages()) {
                if (usage instanceof MultiReturnNode multiReturn && multiReturn.shouldEncourageTailDuplication()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static TailCallOpportunity get(MergeNode merge, FixedNode regionEnd, int benefit) {
        if (benefit > 0 && isTailCallMerge(merge)) {
            TailCallOpportunity opportunity = new TailCallOpportunity();
            opportunity.cyclesSaved = benefit;
            opportunity.lastOptimizableNode = regionEnd;
            tailCallOpportunities.increment(regionEnd.getDebug());
            return opportunity;
        }
        return NO_OPPORTUNITY;
    }
}
