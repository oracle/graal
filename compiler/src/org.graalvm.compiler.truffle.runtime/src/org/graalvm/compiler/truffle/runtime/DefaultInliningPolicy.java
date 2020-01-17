/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import com.oracle.truffle.api.CompilerOptions;

public class DefaultInliningPolicy implements TruffleInliningPolicy {

    @Override
    public double calculateScore(TruffleInliningProfile profile) {
        return profile.getFrequency() / profile.getDeepNodeCount();
    }

    @Override
    public boolean isAllowed(TruffleInliningProfile profile, int currentNodeCount, CompilerOptions options) {
        if (profile.isCached()) {
            profile.setFailedReason(profile.getCached().getFailedReason());
            return false;
        }

        final OptimizedCallTarget target = profile.getCallNode().getCallTarget();
        if (profile.getRecursions() > target.getOptionValue(PolyglotCompilerOptions.InliningRecursionDepth)) {
            profile.setFailedReason(FailedReason.REASON_RECURSION);
            return false;
        }

        int inliningMaxCallerSize = target.getOptionValue(PolyglotCompilerOptions.InliningNodeBudget);

        if (options instanceof GraalCompilerOptions) {
            inliningMaxCallerSize = Math.max(inliningMaxCallerSize, ((GraalCompilerOptions) options).getMinInliningMaxCallerSize());
        }

        if (currentNodeCount + profile.getDeepNodeCount() > inliningMaxCallerSize) {
            profile.setFailedReason(FailedReason.REASON_MAXIMUM_TOTAL_NODE_COUNT);
            return false;
        }

        if (profile.isForced()) {
            return true;
        }

        int cappedCallSites = Math.min(Math.max(profile.getCallSites(), 1), 10);
        if (profile.getDeepNodeCount() * cappedCallSites > inliningMaxCallerSize) {
            profile.setFailedReason(FailedReason.REASON_MAXIMUM_NODE_COUNT);
            return false;
        }

        return true;
    }
}
