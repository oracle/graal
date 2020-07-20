/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Inlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInlining;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInliningDecision;
import org.graalvm.compiler.truffle.runtime.TruffleInliningProfile;

public final class TraceInliningListener extends AbstractGraalTruffleRuntimeListener {

    private TraceInliningListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new TraceInliningListener(runtime));
    }

    @Override
    public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
        if (target.getOptionValue(PolyglotCompilerOptions.LanguageAgnosticInlining) || !target.getOptionValue(TraceInlining) || inliningDecision == null) {
            return;
        }
        if (target.getOptionValue(Inlining)) {
            runtime.logEvent(target, 0, "inline start", target.getDebugProperties(null));
            logInliningDecisionRecursive(target, inliningDecision, 1);
            runtime.logEvent(target, 0, "inline done", target.getDebugProperties(inliningDecision));
        } else {
            runtime.logEvent(target, 0, "TruffleFunctionInlining is set to false", "", null, null);
            return;
        }
    }

    private void logInliningDecisionRecursive(OptimizedCallTarget target, TruffleInlining inliningDecision, int depth) {
        for (TruffleInliningDecision decision : inliningDecision) {
            TruffleInliningProfile profile = decision.getProfile();
            boolean inlined = decision.shouldInline();
            String msg = inlined ? "inline success" : "inline failed";
            runtime.logEvent(target, depth, msg, decision.getProfile().getCallNode().getCurrentCallTarget().toString(), profile.getDebugProperties(), null);
            if (inlined) {
                logInliningDecisionRecursive(target, decision, depth + 1);
            }
        }
    }

}
