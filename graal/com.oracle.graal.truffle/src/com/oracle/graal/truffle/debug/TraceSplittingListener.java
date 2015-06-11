/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

public final class TraceSplittingListener extends AbstractDebugCompilationListener {

    private TraceSplittingListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleSplitting.getValue()) {
            runtime.addCompilationListener(new TraceSplittingListener());
        }
    }

    private int splitCount;

    @Override
    public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        String label = String.format("split %3s-%-4s-%-4s ", splitCount++, callNode.getCurrentCallTarget().getCloneIndex(), callNode.getCallCount());
        log(callTarget, 0, label, callTarget.toString(), callTarget.getDebugProperties());

        if (TruffleSplittingNew.getValue()) {
            Map<TruffleStamp, OptimizedCallTarget> splitTargets = callTarget.getSplitVersions();
            logProfile(callTarget.getArgumentStamp(), callTarget);
            for (TruffleStamp profile : splitTargets.keySet()) {
                logProfile(profile, splitTargets.get(profile));
            }
        }
    }

    private static void logProfile(TruffleStamp stamp, OptimizedCallTarget target) {
        String id = String.format("@%8h %s", target.hashCode(), target.getSourceCallTarget() == null ? "orig." : "split");
        target.log(String.format("%16s%-20sCallers: %3d, Nodes:%10s %s", "", id, target.getKnownCallSiteCount(), //
                        String.format("%d (%d/%d)", count(target, NodeCost.MONOMORPHIC), count(target, NodeCost.POLYMORPHIC), count(target, NodeCost.MEGAMORPHIC)), stamp));
    }

    private static int count(OptimizedCallTarget target, final NodeCost otherCost) {
        return NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == otherCost;
            }
        });
    }

}
