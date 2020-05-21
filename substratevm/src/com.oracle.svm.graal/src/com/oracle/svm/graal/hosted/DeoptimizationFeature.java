/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.deopt.DeoptimizationCounters;
import com.oracle.svm.core.deopt.DeoptimizationRuntime;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.DeoptTestSnippets;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.util.CounterFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.MethodPointer;

/**
 * Feature to allow deoptimization in a generated native image.
 */
public final class DeoptimizationFeature implements GraalFeature {

    private static final Method deoptStubMethod;

    static {
        try {
            deoptStubMethod = Deoptimizer.class.getMethod("deoptStub", DeoptimizedFrame.class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(CounterFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(DeoptimizationSupport.class, new DeoptimizationSupport());
        /* Counters for deoptimization. */
        ImageSingletons.add(DeoptimizationCounters.class, new DeoptimizationCounters());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        /*
         * The deoptimization stub is never called directly. It is patched in as the new return
         * address during deoptimization.
         */
        access.registerAsCompiled(deoptStubMethod);

        /*
         * The deoptimize run time call is not used for method in the native image, but only for
         * runtime compiled methods. Make sure it gets compiled.
         */
        access.registerAsCompiled((AnalysisMethod) DeoptimizationRuntime.DEOPTIMIZE.findMethod(access.getMetaAccess()));

        if (DeoptTester.enabled()) {
            access.getBigBang().addRootMethod((AnalysisMethod) DeoptTester.DEOPTTEST.findMethod(access.getMetaAccess()));
        }
    }

    @Override
    public void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection, SubstrateForeignCallsProvider foreignCalls, boolean hosted) {
        foreignCalls.register(providers, DeoptimizationRuntime.DEOPTIMIZE);
        if (DeoptTester.enabled()) {
            foreignCalls.register(providers, DeoptTester.DEOPTTEST);
        }
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {

        if (DeoptTester.enabled()) {
            DeoptTestSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;
        config.registerAsImmutable(ImageSingletons.lookup(DeoptimizationSupport.class));
        HostedMetaAccess metaAccess = config.getMetaAccess();
        DeoptimizationSupport.setDeoptStubPointer(MethodPointer.factory(metaAccess.lookupJavaMethod(deoptStubMethod)));
    }
}
