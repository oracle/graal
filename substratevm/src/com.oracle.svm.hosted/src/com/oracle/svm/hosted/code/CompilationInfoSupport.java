/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

public class CompilationInfoSupport {

    protected boolean sealed;

    private final Set<AnalysisMethod> forcedCompilations = new HashSet<>();
    private final Set<AnalysisMethod> frameInformationRequired = new HashSet<>();
    private final Map<AnalysisMethod, Set<Long>> deoptEntries = new HashMap<>();
    private final Set<AnalysisMethod> deoptInliningExcludes = new HashSet<>();

    public static CompilationInfoSupport singleton() {
        return ImageSingletons.lookup(CompilationInfoSupport.class);
    }

    public void registerForcedCompilation(ResolvedJavaMethod method) {
        assert !sealed;
        forcedCompilations.add(toAnalysisMethod(method));
    }

    public boolean isForcedCompilation(ResolvedJavaMethod method) {
        assert seal();
        return forcedCompilations.contains(toAnalysisMethod(method));
    }

    public void registerFrameInformationRequired(AnalysisMethod method) {
        assert !sealed;
        frameInformationRequired.add(method);
        /*
         * Frame information is matched using the deoptimization entry point of a method. So in
         * addition to requiring frame information, we also need to mark the method as a
         * deoptimization target. No bci needs to be registered, it is enough to have a non-null
         * value in the map.
         */
        deoptEntries.computeIfAbsent(method, m -> new HashSet<>());
    }

    public boolean isFrameInformationRequired(ResolvedJavaMethod method) {
        assert seal();
        return frameInformationRequired.contains(toAnalysisMethod(method));
    }

    public void registerDeoptEntry(ResolvedJavaMethod method, int bci, boolean duringCall, boolean rethrowException) {
        assert !sealed;
        assert bci >= 0;
        long encodedBci = FrameInfoEncoder.encodeBci(bci, duringCall, rethrowException);
        deoptEntries.computeIfAbsent(toAnalysisMethod(method), m -> new HashSet<>()).add(encodedBci);
    }

    public boolean isDeoptTarget(ResolvedJavaMethod method) {
        assert seal();
        return deoptEntries.containsKey(toAnalysisMethod(method));
    }

    protected boolean isDeoptEntry(ResolvedJavaMethod method, int bci, boolean duringCall, boolean rethrowException) {
        assert seal();
        Set<Long> bciSet = deoptEntries.get(toAnalysisMethod(method));
        assert bciSet != null : "can only query for deopt entries for methods registered as deopt targets";

        long encodedBci = FrameInfoEncoder.encodeBci(bci, duringCall, rethrowException);
        return bciSet.contains(encodedBci);
    }

    public void registerAsDeoptInlininingExclude(ResolvedJavaMethod method) {
        assert !sealed;
        deoptInliningExcludes.add(toAnalysisMethod(method));
    }

    public boolean isDeoptInliningExclude(ResolvedJavaMethod method) {
        assert seal();
        return deoptInliningExcludes.contains(toAnalysisMethod(method));
    }

    public Map<AnalysisMethod, Set<Long>> getDeoptEntries() {
        assert seal();
        return deoptEntries;
    }

    private static AnalysisMethod toAnalysisMethod(ResolvedJavaMethod method) {
        if (method instanceof AnalysisMethod) {
            return (AnalysisMethod) method;
        } else if (method instanceof HostedMethod) {
            return ((HostedMethod) method).wrapped;
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    private boolean seal() {
        sealed = true;
        return true;
    }
}

@AutomaticFeature
class CompilationInfoFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CompilationInfoSupport.class, new CompilationInfoSupport());
    }
}
