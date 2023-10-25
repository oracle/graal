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
package com.oracle.svm.graal.hosted;

import static com.oracle.svm.common.meta.MultiMethod.DEOPT_TARGET_METHOD;
import static com.oracle.svm.common.meta.MultiMethod.ORIGINAL_METHOD;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.SVMParsingSupport;
import com.oracle.svm.hosted.code.DeoptimizationUtils;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisPolicyUtils;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * For Deoptimization Testing we create two versions of candidate methods:
 * <ol>
 * <li>The "original" method which can deoptimize</li>
 * <li>A deoptimization target method to deoptimize to</li>
 * </ol>
 * 
 * This is different than our runtime compilation support, as in that environment also a runtime
 * compilation version for methods are created. In addition, in that environment "original" methods
 * are not allowed to deoptimize.
 */
public class ParseOnceDeoptTestFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(DeoptimizationFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SVMParsingSupport.class, new DeoptTestingParsingSupport());
        ImageSingletons.add(HostVM.MultiMethodAnalysisPolicy.class, new DeoptTestingAnalysisPolicy());
    }

    private class DeoptTestingParsingSupport implements SVMParsingSupport {

        @Override
        public Object parseGraph(BigBang bb, DebugContext debug, AnalysisMethod method) {
            /* Regular parsing is always used. */
            return HostVM.PARSING_UNHANDLED;
        }

        @Override
        public GraphBuilderConfiguration updateGraphBuilderConfiguration(GraphBuilderConfiguration config, AnalysisMethod method) {
            if (method.isDeoptTarget()) {
                /*
                 * Local variables are never retained to help ensure the state of the deoptimization
                 * source will always be a superset of the deoptimization target.
                 */
                return config.withRetainLocalVariables(false);
            }
            return config;
        }

        @Override
        public boolean validateGraph(PointsToAnalysis bb, StructuredGraph graph) {
            PointsToAnalysisMethod aMethod = (PointsToAnalysisMethod) graph.method();
            Supplier<Boolean> graphInvalidator = DeoptimizationUtils.createGraphInvalidator(graph);
            if (aMethod.isDeoptTarget()) {
                return !graphInvalidator.get();
            } else {
                boolean canDeoptForTesting = aMethod.isOriginalMethod() &&
                                DeoptimizationUtils.canDeoptForTesting(aMethod, DeoptTester.enabled(), graphInvalidator);
                if (canDeoptForTesting) {
                    DeoptimizationUtils.registerDeoptEntriesForDeoptTesting(bb, graph, aMethod);
                }
            }

            return true;
        }

        @Override
        public boolean allowAssumptions(AnalysisMethod method) {
            /* Assumptions are not allowed it AOT compiled methods */
            return false;
        }

        @Override
        public boolean recordInlinedMethods(AnalysisMethod method) {
            return false;
        }

        @Override
        public HostedProviders getHostedProviders(MultiMethod.MultiMethodKey key) {
            /* The buildtime providers are always used. */
            return null;
        }

        @Override
        public void initializeInlineBeforeAnalysisPolicy(SVMHost svmHost, InlineBeforeAnalysisPolicyUtils inliningUtils) {
            /* We do not use a custom analysis policy for deopt testing. */
        }

        /**
         * Currently we do not support inlining before analysis during deopt testing. More work is
         * needed to support this.
         */
        @Override
        public InlineBeforeAnalysisPolicy inlineBeforeAnalysisPolicy(MultiMethod.MultiMethodKey multiMethodKey, InlineBeforeAnalysisPolicy defaultPolicy) {
            if (multiMethodKey == ORIGINAL_METHOD) {
                /*
                 * Since we can deopt from original methods, we do not inline here. Doing so would
                 * require us to track the flows into these inlined methods.
                 */
                return InlineBeforeAnalysisPolicy.NO_INLINING;
            } else if (multiMethodKey == DEOPT_TARGET_METHOD) {
                return InlineBeforeAnalysisPolicy.NO_INLINING;
            } else {
                throw VMError.shouldNotReachHere("Unexpected method key: %s", multiMethodKey);
            }
        }

        @Override
        public Function<AnalysisType, ResolvedJavaType> getStrengthenGraphsToTargetFunction(MultiMethod.MultiMethodKey key) {
            /* No customization is needed to deopt testing. */
            return null;
        }
    }

    private static class DeoptTestingAnalysisPolicy implements HostVM.MultiMethodAnalysisPolicy {

        @Override
        public <T extends AnalysisMethod> Collection<T> determineCallees(BigBang bb, T implementation, T target, MultiMethod.MultiMethodKey callerMultiMethodKey, InvokeTypeFlow invokeFlow) {
            if (callerMultiMethodKey == ORIGINAL_METHOD) {
                if (DeoptimizationUtils.canDeoptForTesting(implementation, DeoptTester.enabled(), () -> false)) {
                    /*
                     * If the target is registered for deoptimization, then we must also make a
                     * deoptimized version.
                     */
                    return List.of(implementation, getDeoptVersion(implementation));
                } else {
                    return List.of(implementation);
                }
            } else {
                assert callerMultiMethodKey == DEOPT_TARGET_METHOD;
                /*
                 * A deoptimization target will always call the original method. However, the return
                 * can also be from a deoptimized version when a deoptimization is triggered in an
                 * inlined callee.
                 */
                return List.of(implementation, getDeoptVersion(implementation));
            }
        }

        @SuppressWarnings("unchecked")
        protected <T extends AnalysisMethod> T getDeoptVersion(T implementation) {
            /*
             * Flows for deopt versions are only created once a frame state for the method is seen
             * within a runtime compiled method.
             */
            return (T) implementation.getOrCreateMultiMethod(DEOPT_TARGET_METHOD, (newMethod) -> ((PointsToAnalysisMethod) newMethod).getTypeFlow().setAsStubFlow());
        }

        @Override
        public boolean performParameterLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey) {
            if (callerMultiMethodKey == DEOPT_TARGET_METHOD) {
                /* A deopt method can call the original version only. */
                return calleeMultiMethodKey == ORIGINAL_METHOD;
            } else {
                assert callerMultiMethodKey == ORIGINAL_METHOD;
                /* An original method can call the deopt target as well. */
                return true;
            }
        }

        @Override
        public boolean performReturnLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey) {
            if (callerMultiMethodKey == DEOPT_TARGET_METHOD) {
                /* A deopt method can be returned to from the deopt target or an original method. */
                return true;
            } else {
                assert callerMultiMethodKey == ORIGINAL_METHOD;
                /*
                 * An original method can be returned to from the deopt target or an original
                 * method.
                 */
                return true;
            }
        }

        @Override
        public boolean canComputeReturnedParameterIndex(MultiMethod.MultiMethodKey multiMethodKey) {
            /*
             * Since Deopt Target Methods may have their flow created multiple times, this
             * optimization is not allowed.
             */
            return multiMethodKey != DEOPT_TARGET_METHOD;
        }

        @Override
        public boolean insertPlaceholderParamAndReturnFlows(MultiMethod.MultiMethodKey multiMethodKey) {
            /*
             * Since Deopt Target Methods may have their flow created multiple times, placeholder
             * flows are needed.
             */
            return multiMethodKey == DEOPT_TARGET_METHOD;
        }
    }
}
