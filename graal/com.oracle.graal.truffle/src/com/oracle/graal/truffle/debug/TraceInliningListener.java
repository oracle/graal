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

import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;

public final class TraceInliningListener extends AbstractDebugCompilationListener {

    private TraceInliningListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleInlining.getValue()) {
            runtime.addCompilationListener(new TraceInliningListener());
        }
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
        TruffleInlining inlining = target.getInlining();
        if (inlining == null) {
            return;
        }

        log(0, "inline start", target.toString(), target.getDebugProperties());
        logInliningDecisionRecursive(inlining, 1);
        log(0, "inline done", target.toString(), target.getDebugProperties());
    }

    private static void logInliningDecisionRecursive(TruffleInlining result, int depth) {
        for (TruffleInliningDecision decision : result) {
            TruffleInliningProfile profile = decision.getProfile();
            boolean inlined = decision.isInline();
            String msg = inlined ? "inline success" : "inline failed";
            logInlinedImpl(msg, decision.getProfile().getCallNode(), profile, depth);
            if (inlined) {
                logInliningDecisionRecursive(decision, depth + 1);
            }
        }
    }

    private static void logInlinedImpl(String status, OptimizedDirectCallNode callNode, TruffleInliningProfile profile, int depth) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (profile != null) {
            properties.putAll(profile.getDebugProperties());
        }
        log((depth * 2), status, callNode.getCurrentCallTarget().toString(), properties);
    }

}
