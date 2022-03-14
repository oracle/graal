/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.nodes.TruffleAssumption;
import org.graalvm.options.OptionValues;

import java.util.Objects;

public final class TruffleTierContext extends HighTierContext {
    public final OptionValues options;
    public final DebugContext debug;
    public final CompilableTruffleAST compilable;
    public final CompilationIdentifier compilationId;
    public final SpeculationLog log;
    public final TruffleCompilerImpl.CancellableTruffleCompilationTask task;
    public final StructuredGraph graph;
    public final PerformanceInformationHandler handler;

    public TruffleTierContext(PartialEvaluator partialEvaluator, OptionValues options, DebugContext debug, CompilableTruffleAST compilable, ResolvedJavaMethod method,
                    CompilationIdentifier compilationId, SpeculationLog log, TruffleCompilerImpl.CancellableTruffleCompilationTask task, PerformanceInformationHandler handler) {
        super(partialEvaluator.getProviders(), new PhaseSuite<>(), OptimisticOptimizations.NONE);
        Objects.requireNonNull(options);
        Objects.requireNonNull(debug);
        Objects.requireNonNull(compilable);
        Objects.requireNonNull(compilationId);
        Objects.requireNonNull(task);
        this.options = options;
        this.debug = debug;
        this.compilable = compilable;
        this.compilationId = compilationId;
        this.log = log;
        this.task = task;
        // @formatter:off
        StructuredGraph.Builder builder = new StructuredGraph.Builder(this.debug.getOptions(), this.debug, StructuredGraph.AllowAssumptions.YES).
                name(this.compilable.getName()).
                method(method).
                speculationLog(this.log).
                compilationId(this.compilationId).
                trackNodeSourcePosition(partialEvaluator.configForParsing.trackNodeSourcePosition()).
                cancellable(this.task);
        // @formatter:on
        builder = partialEvaluator.customizeStructuredGraphBuilder(builder);
        this.graph = builder.build();
        this.graph.getAssumptions().record(new TruffleAssumption(compilable.getValidRootAssumptionConstant()));
        this.graph.getAssumptions().record(new TruffleAssumption(compilable.getNodeRewritingAssumptionConstant()));
        this.handler = handler;
    }

    public TruffleTierContext(PartialEvaluator partialEvaluator, TruffleCompilerImpl.TruffleCompilationWrapper wrapper, DebugContext debug, PerformanceInformationHandler handler) {
        this(partialEvaluator, wrapper.options, debug, wrapper.compilable, partialEvaluator.rootForCallTarget(wrapper.compilable), wrapper.compilationId, getSpeculationLog(wrapper), wrapper.task,
                        handler);
    }

    public boolean isFirstTier() {
        return task.isFirstTier();
    }

    private static SpeculationLog getSpeculationLog(TruffleCompilerImpl.TruffleCompilationWrapper wrapper) {
        SpeculationLog speculationLog = wrapper.compilable.getCompilationSpeculationLog();
        if (speculationLog != null) {
            speculationLog.collectFailedSpeculations();
        }
        return speculationLog;
    }
}
