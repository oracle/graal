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
import org.graalvm.options.OptionKey;

public interface TruffleInliningPolicy {

    enum FailedReason {
        REASON_RECURSION("number of recursions > ", PolyglotCompilerOptions.InliningRecursionDepth),
        REASON_MAXIMUM_NODE_COUNT("deepNodeCount * callSites > ", PolyglotCompilerOptions.InliningNodeBudget),
        REASON_MAXIMUM_TOTAL_NODE_COUNT("totalNodeCount > ", PolyglotCompilerOptions.InliningNodeBudget);

        private final String start;
        private final OptionKey<Integer> option;

        FailedReason(String start, OptionKey<Integer> option) {
            this.start = start;
            this.option = option;
        }

        String format(OptimizedCallTarget target) {
            return start + target.getOptionValue(option);
        }
    }

    boolean isAllowed(TruffleInliningProfile profile, int currentNodeCount, CompilerOptions options);

    double calculateScore(TruffleInliningProfile profile);

    @SuppressWarnings("unused")
    static TruffleInliningPolicy getInliningPolicy() {
        return new DefaultInliningPolicy();
    }

    @SuppressWarnings("unused")
    static TruffleInliningPolicy getNoInliningPolicy() {
        return new NoInliningPolicy();
    }
}
