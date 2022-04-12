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

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.IterativePartialEscape;

import java.io.PrintStream;
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.CompilationWrapper;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.TruffleCompilationIdentifier;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerConfiguration;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.compiler.TruffleTierConfiguration;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentationSuite;
import org.graalvm.compiler.truffle.compiler.phases.TruffleTier;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateCompilationResult;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.SubstrateGraalUtils;
import com.oracle.svm.truffle.SubstrateTruffleCompilationIdentifier;
import com.oracle.svm.truffle.TruffleSupport;

import jdk.vm.ci.code.InstalledCode;

public class SubstrateTruffleCompilerImpl extends TruffleCompilerImpl implements SubstrateTruffleCompiler {

    private final String compilerConfigurationName;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleCompilerImpl(TruffleCompilerConfiguration config) {
        super(config);
        compilerConfigurationName = GraalConfiguration.runtimeInstance().getCompilerConfigurationName();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected PartialEvaluator createPartialEvaluator(TruffleCompilerConfiguration configuration) {
        return TruffleSupport.singleton().createPartialEvaluator(configuration, builderConfig);
    }

    @Override
    public void initialize(Map<String, Object> optionsMap, CompilableTruffleAST compilable, boolean firstInitialization) {
        super.initialize(optionsMap, compilable, firstInitialization);
        for (Backend backend : getConfig().backends()) {
            SubstrateGraalUtils.updateGraalArchitectureWithHostCPUFeatures(backend);
        }
    }

    @Override
    protected TruffleTier newTruffleTier(org.graalvm.options.OptionValues options) {
        return new TruffleTier(options, partialEvaluator,
                        new InstrumentationSuite(partialEvaluator.instrumentationCfg, config.snippetReflection(), partialEvaluator.getInstrumentation()),
                        new SubstratePostPartialEvaluationSuite(options.get(IterativePartialEscape)));
    }

    @Override
    public PhaseSuite<HighTierContext> createGraphBuilderSuite(TruffleTierConfiguration tier) {
        return null;
    }

    @Override
    public String getCompilerConfigurationName() {
        return compilerConfigurationName;
    }

    @Override
    public void teardown() {
    }

    @Override
    protected CompilationResult createCompilationResult(String name, CompilationIdentifier compilationIdentifier, CompilableTruffleAST compilable) {
        return new SubstrateCompilationResult(compilationIdentifier, name);
    }

    @Override
    public TruffleCompilationIdentifier createCompilationIdentifier(CompilableTruffleAST optimizedCallTarget) {
        return new SubstrateTruffleCompilationIdentifier((OptimizedCallTarget) optimizedCallTarget);
    }

    @Override
    public DebugContext createDebugContext(OptionValues options, CompilationIdentifier compilationId, CompilableTruffleAST callTarget, PrintStream logStream) {
        return GraalSupport.get().openDebugContext(options, compilationId, callTarget, logStream);
    }

    @Override
    protected DiagnosticsOutputDirectory getDebugOutputDirectory() {
        return GraalSupport.get().getDebugOutputDirectory();
    }

    @Override
    protected Map<CompilationWrapper.ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return GraalSupport.get().getCompilationProblemsPerAction();
    }

    @Override
    protected InstalledCode createInstalledCode(CompilableTruffleAST compilable) {
        return ((SubstrateCompilableTruffleAST) compilable).createPreliminaryInstalledCode();
    }
}
