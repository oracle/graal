/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jni.access.JNIAccessibleClass;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.ProgressReporter.DirectPrinter;
import com.oracle.svm.hosted.jdk.JNIRegistrationSupport;
import com.oracle.svm.hosted.util.CPUTypeAArch64;
import com.oracle.svm.hosted.util.CPUTypeAMD64;
import com.oracle.svm.hosted.util.CPUTypeRISCV64;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class ProgressReporterFeature implements InternalFeature {
    protected final ProgressReporter reporter = ProgressReporter.singleton();

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (SubstrateOptions.BuildOutputBreakdowns.getValue()) {
            ImageSingletons.add(HeapBreakdownProvider.class, new HeapBreakdownProvider());
        }
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        reporter.reportStageProgress();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        var vectorSpeciesClass = ReflectionUtil.lookupClass(true, "jdk.incubator.vector.VectorSpecies");
        if (vectorSpeciesClass != null && access.isReachable(vectorSpeciesClass)) {
            LogUtils.warning(
                            "This application uses a preview of the Vector API, which is functional but slow on Native Image because it is not yet optimized by the Graal compiler. Please keep this in mind when evaluating performance.");
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (SubstrateOptions.BuildOutputBreakdowns.getValue()) {
            ImageSingletons.add(CodeBreakdownProvider.class, new CodeBreakdownProvider(((AfterCompilationAccessImpl) access).getCompilationTasks()));
        }
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        if (SubstrateOptions.BuildOutputBreakdowns.getValue()) {
            HeapBreakdownProvider.singleton().calculate(((BeforeImageWriteAccessImpl) access));
        }
    }

    protected void appendGraalSuffix(@SuppressWarnings("unused") DirectPrinter graalLine) {
    }

    public void afterBreakdowns() {
        String userWarning = ImageSingletons.lookup(Log4ShellFeature.class).getUserWarning();
        if (userWarning != null) {
            LogUtils.warning(userWarning);
        }
    }

    public void createAdditionalArtifactsOnSuccess(@SuppressWarnings("unused") BuildArtifacts artifacts) {
    }

    protected List<UserRecommendation> getRecommendations() {
        return List.of(// in order of appearance:
                        new UserRecommendation("AWT", "Use the tracing agent to collect metadata for AWT.", ProgressReporterFeature::recommendTraceAgentForAWT),
                        new UserRecommendation("HEAP", "Set max heap for improved and more predictable memory usage.", () -> SubstrateGCOptions.MaxHeapSize.getValue() == 0),
                        new UserRecommendation("CPU", "Enable more CPU features with '-march=native' for improved performance.", ProgressReporterFeature::recommendMArchNative));
    }

    private static boolean recommendMArchNative() {
        if (NativeImageOptions.MicroArchitecture.getValue() != null) {
            return false; // explicitly set by user
        }
        return switch (SubstrateUtil.getArchitectureName()) {
            case "aarch64" -> CPUTypeAArch64.nativeSupportsMoreFeaturesThanSelected();
            case "amd64" -> CPUTypeAMD64.nativeSupportsMoreFeaturesThanSelected();
            case "riscv64" -> CPUTypeRISCV64.nativeSupportsMoreFeaturesThanSelected();
            default -> false;
        };
    }

    private static boolean recommendTraceAgentForAWT() {
        if (!ImageSingletons.contains(JNIRegistrationSupport.class) || !ImageSingletons.contains(JNIReflectionDictionary.class)) {
            return false;
        }
        if (!JNIRegistrationSupport.singleton().isRegisteredLibrary("awt")) {
            return false; // AWT not used
        }
        // check if any class located in java.awt or sun.awt is registered for JNI access
        for (JNIAccessibleClass clazz : JNIReflectionDictionary.singleton().getClasses()) {
            String className = clazz.getClassObject().getName();
            if (className.startsWith("java.awt") || className.startsWith("sun.awt")) {
                return false;
            }
        }
        return true;
    }

    public record UserRecommendation(String id, String description, Supplier<Boolean> isApplicable) {
        public UserRecommendation {
            assert id.toUpperCase(Locale.ROOT).equals(id) && id.length() < 5 : "id must be uppercase and have fewer than 5 chars";
            int maxLength = 74;
            assert description.length() < maxLength : "description must have fewer than " + maxLength + " chars to fit in terminal. Length: " + description.length();
        }
    }
}
