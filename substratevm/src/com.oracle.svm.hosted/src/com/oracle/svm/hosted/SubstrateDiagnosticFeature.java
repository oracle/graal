/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunkRegistry;
import com.oracle.svm.core.SubstrateDiagnostics.FatalErrorState;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

@AutomaticallyRegisteredFeature
class SubstrateDiagnosticFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(FatalErrorState.class, new FatalErrorState());

        // Ensure that the diagnostic thunks are initialized.
        DiagnosticThunkRegistry.singleton();
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        // Explicitly mark options as used so that it is possible to specify a value at runtime.
        BeforeAnalysisAccessImpl accessImpl = (BeforeAnalysisAccessImpl) access;
        registerOptionAsRead(accessImpl, SubstrateOptions.class, SubstrateOptions.DiagnosticDetails.getName());
        registerOptionAsRead(accessImpl, SubstrateDiagnostics.Options.class, SubstrateDiagnostics.Options.LoopOnFatalError.getName());
        registerOptionAsRead(accessImpl, SubstrateDiagnostics.Options.class, SubstrateDiagnostics.Options.ImplicitExceptionWithoutStacktraceIsFatal.getName());
    }

    private static void registerOptionAsRead(BeforeAnalysisAccessImpl accessImpl, Class<?> clazz, String fieldName) {
        try {
            Field javaField = clazz.getField(fieldName);
            AnalysisField analysisField = accessImpl.getMetaAccess().lookupJavaField(javaField);
            accessImpl.registerAsRead(analysisField, "it is a runtime option field");
        } catch (NoSuchFieldException | SecurityException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
