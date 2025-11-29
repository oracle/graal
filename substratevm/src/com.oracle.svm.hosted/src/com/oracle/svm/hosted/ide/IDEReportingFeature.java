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
package com.oracle.svm.hosted.ide;

import java.util.Collection;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.ide.IDEReport;

/**
 * A feature that provides reporting functionality for IDEs.
 * <p>
 * In particular, the feature provides information about class initialization and compiled methods.
 *
 * @see IDEReport
 */
@AutomaticallyRegisteredFeature
public class IDEReportingFeature implements InternalFeature {

    private ClassInitializationSupport classInitializationSupport;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return IDEReport.isEnabled();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        var accessImpl = (FeatureImpl.DuringSetupAccessImpl) access;
        classInitializationSupport = accessImpl.getHostVM().getClassInitializationSupport();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        var accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        saveClassInitializationModes(accessImpl);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        var accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;
        saveCompiledMethods(accessImpl.getUniverse().getMethods());
    }

    /**
     * Saves class initialization results to the IDE report.
     *
     * @param access access to the analysis results
     */
    private void saveClassInitializationModes(FeatureImpl.AfterAnalysisAccessImpl access) {
        IDEReport.runIfEnabled(ideReport -> {
            forallTypes: for (var aType : access.getUniverse().getTypes()) {
                var clazz = aType.getJavaClass();
                if (clazz == null) {
                    continue forallTypes;
                }
                var classInitKind = classInitializationSupport.computedInitKindFor(clazz);
                if (classInitKind == null) {
                    continue forallTypes;
                }
                var kind = classInitKind.toString();
                if (kind.equals("RUN_TIME")) {
                    var type = access.getMetaAccess().optionalLookupJavaType(clazz);
                    if (type.isPresent() && SimulateClassInitializerSupport.singleton().isSuccessfulSimulation(type.get())) {
                        kind = "SIMULATED";
                    }
                }
                try {
                    var type = access.getMetaAccess().lookupJavaType(clazz);
                    if (type.getSourceFileName() != null) {
                        var filePath = IDEReport.getFilePath(type);
                        ideReport.saveClassReport(filePath, type.toJavaName(), "Initialization mode of " + type.toJavaName(false) + " is " + kind);
                    }
                } catch (UnsupportedFeatureException e) {
                    // skip
                }
            }
        });
    }

    /**
     * Saves compiled methods to the IDE report.
     *
     * @param allPossiblyCompiledMethods collection of compiled methods
     */
    private static void saveCompiledMethods(Collection<HostedMethod> allPossiblyCompiledMethods) {
        for (var method : allPossiblyCompiledMethods) {
            if (method.isCompiled()) {
                IDEReport.runIfEnabled(ideReport -> {
                    var declaringClass = method.getDeclaringClass();
                    var filepath = IDEReport.getFilePath(method);
                    if (declaringClass == null || filepath == null) {
                        return;
                    }
                    ideReport.saveMethodCompiled(filepath, declaringClass.toJavaName(), method.getName(),
                                    method.getSignature().toMethodDescriptor());
                });
            }
        }
    }

}
