/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.host.HostInliningPhase;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Overrides the behavior of the Truffle host inlining phase taking into account that SVM does not
 * need any graph caching as all graphs are already parsed. Also enables the use of this phase for
 * methods that are runtime compiled in addition to methods annotated with
 * {@link BytecodeInterpreterSwitch}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class SubstrateHostInliningPhase extends HostInliningPhase {

    private final TruffleFeature truffleFeature = ImageSingletons.lookup(TruffleFeature.class);

    SubstrateHostInliningPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer, -1.0d); // -1.0 effectively disables frequency based inlining by
                                     // default.
    }

    @Override
    protected StructuredGraph parseGraph(HighTierContext context, StructuredGraph graph, ResolvedJavaMethod method) {
        return ((HostedMethod) method).compilationInfo.createGraph(graph.getDebug(), graph.getOptions(), CompilationIdentifier.INVALID_COMPILATION_ID, true);
    }

    /**
     * Determines whether a method should be used for host inlining. We use the set of runtime
     * compiled methods collected by the {@link TruffleFeature} for that.
     */
    @Override
    protected boolean isEnabledFor(TruffleHostEnvironment env, ResolvedJavaMethod method) {
        HostedMethod hostedMethod = ((HostedMethod) method);
        if (hostedMethod.isDeoptTarget()) {
            // do not treat deopt targets as interpreter methods
            // they generally should not perform any optimization to simplify deoptimization
            return false;
        } else if (super.isEnabledFor(env, method)) {
            return true;
        } else if (truffleFeature.runtimeCompiledMethods.contains(translateMethod(method)) &&
                        isTruffleBoundary(env, method) == null) {
            return true;
        }
        return false;
    }

    @Override
    protected String isTruffleBoundary(TruffleHostEnvironment env, ResolvedJavaMethod targetMethod) {
        ResolvedJavaMethod translatedMethod = translateMethod(targetMethod);
        String boundary = super.isTruffleBoundary(env, targetMethod);
        if (boundary != null) {
            return boundary;
        } else if (truffleFeature.isBlocklisted(translatedMethod)) {
            return "SVM block listed";
        } else {
            return null;
        }
    }

    @Override
    protected ResolvedJavaMethod translateMethod(ResolvedJavaMethod method) {
        return ((HostedMethod) method).getWrapped();
    }
}
