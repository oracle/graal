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

import org.graalvm.nativeimage.Feature;

import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.MethodPointer;

import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Feature to allow deoptimization in a generated native image.
 */
public final class DeoptimizationFeature implements Feature {

    private static final Method deoptStubMethod;
    private static final Method deoptimizeMethod;

    static {
        try {
            deoptStubMethod = Deoptimizer.class.getMethod("deoptStub", DeoptimizedFrame.class);
            deoptimizeMethod = SnippetRuntime.class.getDeclaredMethod("deoptimize", long.class, SpeculationReason.class);
        } catch (NoSuchMethodException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl config = (BeforeAnalysisAccessImpl) a;

        /*
         * The deoptimization stub is never called directly. It is patched in as the new return
         * address during deoptimization.
         */
        config.registerAsCompiled(deoptStubMethod);

        /*
         * The deoptimize run time call is not used for method in the native image, but only for
         * runtime compiled methods. Make sure it gets compiled.
         */
        config.registerAsCompiled(deoptimizeMethod);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;

        HostedMetaAccess metaAccess = config.getMetaAccess();
        DeoptimizationSupport.setDeoptStubPointer(MethodPointer.factory(metaAccess.lookupJavaMethod(deoptStubMethod)));
    }
}
