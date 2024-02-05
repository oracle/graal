/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import java.io.PrintStream;
import java.util.Map;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.graal.TruffleRuntimeCompilationSupport;
import com.oracle.svm.truffle.SubstrateTruffleCompilationIdentifier;
import com.oracle.svm.truffle.TruffleSupport;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DiagnosticsOutputDirectory;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.PartialEvaluator;
import jdk.graal.compiler.truffle.TruffleCompilationIdentifier;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;
import jdk.graal.compiler.truffle.TruffleTierConfiguration;
import jdk.graal.compiler.truffle.phases.InstrumentationSuite;
import jdk.graal.compiler.truffle.phases.TruffleTier;
import jdk.vm.ci.code.InstalledCode;

public class SubstrateTruffleCompilerImpl extends TruffleCompilerImpl implements SubstrateTruffleCompiler {

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleCompilerImpl(TruffleCompilerConfiguration config) {
        super(config);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected PartialEvaluator createPartialEvaluator(TruffleCompilerConfiguration configuration) {
        return TruffleSupport.singleton().createPartialEvaluator(configuration, builderConfig);
    }

    @Override
    public void initialize(TruffleCompilable compilable, boolean firstInitialization) {
        super.initialize(compilable, firstInitialization);
        for (Backend backend : getConfig().backends()) {
            SubstrateGraalUtils.updateGraalArchitectureWithHostCPUFeatures(backend);
        }
    }

    @Override
    protected TruffleTier newTruffleTier(OptionValues options) {
        return new TruffleTier(options, partialEvaluator,
                        new InstrumentationSuite(partialEvaluator.instrumentationCfg, partialEvaluator.getInstrumentation()),
                        new SubstratePostPartialEvaluationSuite(getGraalOptions(), TruffleCompilerOptions.IterativePartialEscape.getValue(options)));
    }

    @Override
    public PhaseSuite<HighTierContext> createGraphBuilderSuite(TruffleTierConfiguration tier) {
        return null;
    }

    @Override
    protected OptionValues getGraalOptions() {
        return RuntimeOptionValues.singleton();
    }

    @Override
    public void teardown() {
    }

    @Override
    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier, TruffleCompilable compilable) {
        return new SubstrateCompilationResult(compilationIdentifier, name);
    }

    @Override
    public TruffleCompilationIdentifier createCompilationIdentifier(TruffleCompilationTask task, TruffleCompilable compilable) {
        return new SubstrateTruffleCompilationIdentifier(task, compilable);
    }

    @Override
    public DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, TruffleCompilable callTarget, PrintStream logStream) {
        return TruffleRuntimeCompilationSupport.get().openDebugContext(options, compilationId, callTarget, logStream);
    }

    @Override
    protected DiagnosticsOutputDirectory getDebugOutputDirectory() {
        return TruffleRuntimeCompilationSupport.get().getDebugOutputDirectory();
    }

    @Override
    protected Map<CompilationWrapper.ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return TruffleRuntimeCompilationSupport.get().getCompilationProblemsPerAction();
    }

    @Override
    protected InstalledCode createInstalledCode(TruffleCompilable compilable) {
        return ((SubstrateCompilableTruffleAST) compilable).createPreliminaryInstalledCode();
    }

}
