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
package org.graalvm.compiler.truffle.debug;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleInlining;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.graalvm.compiler.truffle.TruffleInliningDecision;
import org.graalvm.compiler.truffle.TruffleInliningProfile;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;

public final class TraceInliningListener extends AbstractDebugCompilationListener {

    private TraceInliningListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleInlining.getValue()) {
            runtime.addCompilationListener(new TraceInliningListener());
        }
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {
        if (inliningDecision == null) {
            return;
        }
        if (TruffleCompilerOptions.TruffleFunctionInlining.getValue()) {
            log(0, "inline start", target.toString(), target.getDebugProperties(null));
            logInliningDecisionRecursive(target, inliningDecision, 1);
            log(0, "inline done", target.toString(), target.getDebugProperties(inliningDecision));
        }else {
            log(0, "TruffleFunctionInlining is set to false", "", null);
            return;
        }
    }

    private void logInliningDecisionRecursive(OptimizedCallTarget target, TruffleInlining inliningDecision, int depth) {
        for (TruffleInliningDecision decision : inliningDecision) {
            TruffleInliningProfile profile = decision.getProfile();
            boolean inlined = decision.isInline();
            String msg = inlined ? "inline success" : "inline failed";
            log(depth, msg, decision.getProfile().getCallNode().getCurrentCallTarget().toString(), profile.getDebugProperties());
            if (inlined) {
                logInliningDecisionRecursive(target, decision, depth + 1);
            }
        }
    }

}
