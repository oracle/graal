/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.strictconstantanalysis;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.ImageSingletons;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

@AutomaticallyRegisteredFeature
public class StrictConstantAnalysisFeature implements InternalFeature {

    public static class Options {

        public enum Mode {
            Disable,
            Warn,
            Enforce
        }

        @Option(help = """
                        Select the mode for the strict, build-time analysis for calls requiring dynamic access.
                        Possible values are:
                         "Disable" (default): Disable the strict mode and fall back to the optimization dependent analysis for inferrable dynamic calls;
                         "Warn": Fold both the calls inferred with the strict mode analysis and the optimization dependant analysis, but print a warning for non-strict call folding;
                         "Enforce": Fold only the calls inferred by the strict analysis mode.""")//
        public static final HostedOptionKey<Mode> StrictConstantAnalysis = new HostedOptionKey<>(Mode.Disable);
    }

    private ImageClassLoader loader;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess a) {
        return Options.StrictConstantAnalysis.getValue() != Options.Mode.Disable;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        loader = access.getImageClassLoader();
        ImageSingletons.add(ConstantExpressionRegistry.class, new ConstantExpressionRegistry());
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        ConstantExpressionRegistry registry = ImageSingletons.lookup(ConstantExpressionRegistry.class);
        ConstantExpressionAnalyzer analyzer = new ConstantExpressionAnalyzer(providers, loader);
        plugins.appendMethodParsingPlugin((method, intrinsicContext) -> registry.analyzeAndStore(analyzer, method, intrinsicContext));
    }

    /**
     * Utility method which attempts to infer {@code targetMethod} according to the {@code Disable},
     * {@code Warn} and {@code Enforce} options of {@code StrictConstantAnalysis}.
     */
    public static boolean tryToInfer(Predicate<ConstantExpressionRegistry> strictModeRoutine, BooleanSupplier graphModeRoutine, ResolvedJavaMethod targetMethod,
                    Predicate<ResolvedJavaMethod> strictModeTarget) {
        Options.Mode analysisMode = Options.StrictConstantAnalysis.getValue();
        boolean isTarget = strictModeTarget.test(targetMethod);
        if (analysisMode != Options.Mode.Disable && isTarget) {
            ConstantExpressionRegistry registry = ImageSingletons.lookup(ConstantExpressionRegistry.class);
            if (strictModeRoutine.test(registry)) {
                return true;
            }
        }
        if (analysisMode != Options.Mode.Enforce || !isTarget) {
            return graphModeRoutine.getAsBoolean();
        }
        return false;
    }

    /**
     * Utility method which attempts to infer {@code targetMethod} according to the {@code Disable},
     * {@code Warn} and {@code Enforce} options of {@code StrictConstantAnalysis}.
     */
    public static boolean tryToInfer(Predicate<ConstantExpressionRegistry> strictModeRoutine, BooleanSupplier graphModeRoutine) {
        return tryToInfer(strictModeRoutine, graphModeRoutine, null, (method) -> true);
    }
}
