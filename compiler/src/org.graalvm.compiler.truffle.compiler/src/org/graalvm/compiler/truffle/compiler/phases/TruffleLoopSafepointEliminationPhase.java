/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.phases.LoopSafepointEliminationPhase;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extends the default Graal loop safepoint elimination phase and adds logic to disable guest
 * safepoints when the loop contains truffle calls or when there is a plain counted loop without
 * allocations.
 *
 * @see LoopEndNode#canGuestSafepoint
 */
public final class TruffleLoopSafepointEliminationPhase extends LoopSafepointEliminationPhase {

    private final ResolvedJavaMethod callBoundary;

    public TruffleLoopSafepointEliminationPhase(Providers providers) {
        TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
        ResolvedJavaType callTarget = rt.resolveType(providers.getMetaAccess(), "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        this.callBoundary = TruffleSafepointInsertionPhase.findMethod(callTarget, "callBoundary");
    }

    @Override
    protected void onSafepointDisabledLoopBegin(LoopEx loop) {
        for (Node node : loop.whole().nodes()) {
            if (node instanceof CommitAllocationNode || node instanceof AbstractNewObjectNode) {
                // we can disable truffle safepoints if there are no allocations
                // allocations are no implicit safepoint for truffle
                return;
            }
        }
        loop.loopBegin().disableGuestSafepoint();
    }

    @Override
    protected boolean onCallInLoop(LoopEndNode loopEnd, FixedNode currentCallNode) {
        if (currentCallNode instanceof Invoke && isTruffleCall((Invoke) currentCallNode)) {
            // only truffle calls imply a truffle safepoint at method exits
            loopEnd.disableGuestSafepoint();
            return true;
        }
        return false;
    }

    @Override
    protected boolean allowGuestSafepoints() {
        return true;
    }

    private boolean isTruffleCall(Invoke call) {
        CallTargetNode target = call.callTarget();
        if (target == null) {
            return false;
        }
        ResolvedJavaMethod method = target.targetMethod();
        if (method == null) {
            return false;
        }
        return method.equals(callBoundary);
    }

}
