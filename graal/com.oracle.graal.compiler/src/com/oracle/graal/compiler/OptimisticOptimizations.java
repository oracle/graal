/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler;

import com.oracle.max.cri.ri.*;



public final class OptimisticOptimizations {
    public static OptimisticOptimizations ALL = new OptimisticOptimizations(true, true, true, true);
    public static OptimisticOptimizations NONE = new OptimisticOptimizations(false, false, false, false);

    private final boolean removeNeverExecutedCode;
    private final boolean useTypeCheckedInlining;
    private final boolean useTypeCheckHints;
    private final boolean useExceptionProbability;

    public OptimisticOptimizations(RiResolvedMethod method) {
        RiProfilingInfo profilingInfo = method.profilingInfo();
        removeNeverExecutedCode = profilingInfo.getDeoptimizationCount(RiDeoptReason.UnreachedCode) < GraalOptions.MaximumDeoptsBeforeDisablingOptimisticOptimization;
        useTypeCheckedInlining = profilingInfo.getDeoptimizationCount(RiDeoptReason.TypeCheckedInliningViolated) < GraalOptions.MaximumDeoptsBeforeDisablingOptimisticOptimization;
        useTypeCheckHints = profilingInfo.getDeoptimizationCount(RiDeoptReason.OptimizedTypeCheckViolated) < GraalOptions.MaximumDeoptsBeforeDisablingOptimisticOptimization;
        useExceptionProbability = profilingInfo.getDeoptimizationCount(RiDeoptReason.NotCompiledExceptionHandler) < GraalOptions.MaximumDeoptsBeforeDisablingOptimisticOptimization;
    }

    public OptimisticOptimizations(boolean removeNeverExecutedCode, boolean useTypeCheckedInlining, boolean useTypeCheckHints, boolean useExceptionProbability) {
        this.removeNeverExecutedCode = removeNeverExecutedCode;
        this.useTypeCheckedInlining = useTypeCheckedInlining;
        this.useTypeCheckHints = useTypeCheckHints;
        this.useExceptionProbability = useExceptionProbability;
    }

    public boolean removeNeverExecutedCode() {
        return GraalOptions.RemoveNeverExecutedCode && removeNeverExecutedCode;
    }

    public boolean useUseTypeCheckHints() {
        return GraalOptions.UseTypeCheckHints && useTypeCheckHints;
    }

    public boolean inlineMonomorphicCalls() {
        return GraalOptions.InlineMonomorphicCalls && useTypeCheckedInlining;
    }

    public boolean inlinePolymorphicCalls() {
        return GraalOptions.InlinePolymorphicCalls && useTypeCheckedInlining;
    }

    public boolean inlineMegamorphicCalls() {
        return GraalOptions.InlineMegamorphicCalls && useTypeCheckedInlining;
    }

    public boolean useExceptionProbability() {
        return useExceptionProbability;
    }
}
