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
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.deopt.DeoptimizationCanaryFeature;
import com.oracle.svm.core.deopt.DeoptimizationCounters;
import com.oracle.svm.core.deopt.DeoptimizationRuntime;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.DeoptTestSnippets;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.CounterFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

/**
 * Feature to allow deoptimization in a generated native image.
 */
@Platforms(InternalPlatform.NATIVE_ONLY.class)
public final class DeoptimizationFeature implements InternalFeature {

    private static final Method deoptStubMethod;

    static {
        try {
            deoptStubMethod = Deoptimizer.class.getMethod("deoptStub", Pointer.class, UnsignedWord.class, UnsignedWord.class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(DeoptimizationCanaryFeature.class, CounterFeature.class);
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
        access.registerAsRoot(deoptStubMethod, true, "Deoptimization stub, registered in " + DeoptimizationFeature.class);

        /*
         * The deoptimize run time call is not used for method in the native image, but only for
         * runtime compiled methods. Make sure it gets compiled.
         */
        access.registerAsRoot((AnalysisMethod) DeoptimizationRuntime.DEOPTIMIZE.findMethod(access.getMetaAccess()), true, "Deoptimization, registered in " + DeoptimizationFeature.class);

        if (DeoptTester.enabled()) {
            access.getBigBang().addRootMethod((AnalysisMethod) DeoptTester.DEOPTTEST.findMethod(access.getMetaAccess()), true, "Deoptimization test, registered in " + DeoptimizationFeature.class);
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(DeoptimizationRuntime.DEOPTIMIZE);
        if (DeoptTester.enabled()) {
            foreignCalls.register(DeoptTester.DEOPTTEST);
        }
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {

        if (DeoptTester.enabled()) {
            DeoptTestSnippets.registerLowerings(options, providers, lowerings);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;
        config.registerAsImmutable(ImageSingletons.lookup(DeoptimizationSupport.class));
        HostedMetaAccess metaAccess = config.getMetaAccess();
        DeoptimizationSupport.setDeoptStubPointer(new MethodPointer(metaAccess.lookupJavaMethod(deoptStubMethod)));
    }
}
