/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.UninterruptibleAnnotationUtils;
import com.oracle.svm.core.graal.nodes.InlinedInvokeArgumentsNode;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldSnippets;
import com.oracle.svm.core.graal.snippets.TypeSnippets;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.phases.AnalyzeJavaHomeAccessPhase;
import com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.TriState;

public class SubstrateStrengthenGraphs extends StrengthenGraphs {
    private final boolean trackDynamicAccess;
    private final boolean trackJavaHomeAccess;
    private final boolean trackJavaHomeAccessDetailed;
    private final boolean allowArtificialInstanceOfProfiles;
    private final boolean usesClosedTypeWorldHubLayout;

    public SubstrateStrengthenGraphs(Inflation bb, Universe converter) {
        this(bb, converter, true);
    }

    /**
     * Creates graph strengthening for Native Image.
     *
     * @param allowArtificialInstanceOfProfiles whether static-analysis results may be used to
     *            create artificial profiles for {@link InstanceOfNode}s
     */
    public SubstrateStrengthenGraphs(Inflation bb, Universe converter, boolean allowArtificialInstanceOfProfiles) {
        super(bb, converter);
        this.trackDynamicAccess = DynamicAccessDetectionSupport.isDynamicAccessTrackingEnabled();
        this.trackJavaHomeAccess = SubstrateOptions.TrackJavaHomeAccess.getValue();
        this.trackJavaHomeAccessDetailed = SubstrateOptions.TrackJavaHomeAccessDetailed.getValue();
        this.allowArtificialInstanceOfProfiles = allowArtificialInstanceOfProfiles;
        this.usesClosedTypeWorldHubLayout = SubstrateOptions.useClosedTypeWorldHubLayout();
    }

    @Override
    protected void preStrengthenGraphs(StructuredGraph graph, AnalysisMethod method) {
        if (trackDynamicAccess) {
            new DynamicAccessDetectionPhase().apply(graph, bb.getProviders(method));
        }
    }

    @Override
    protected void postStrengthenGraphs(StructuredGraph graph, AnalysisMethod method) {
        if (trackJavaHomeAccess) {
            new AnalyzeJavaHomeAccessPhase(trackJavaHomeAccessDetailed, bb.getMetaAccess()).apply(graph, bb.getProviders(method));
        }
    }

    @Override
    protected void persistStrengthenGraph(AnalysisMethod method) {
        if (HostedImageLayerBuildingSupport.buildingSharedLayer() && method.isTrackedAcrossLayers()) {
            HostedImageLayerBuildingSupport.singleton().getWriter().persistMethodStrengthenedGraph(method);
        }
    }

    @Override
    protected AnalysisType getSingleImplementorType(AnalysisType originalType) {
        HostedType singleImplementorType = ((HostedType) converter.lookup(originalType)).getSingleImplementor();
        return singleImplementorType == null ? null : singleImplementorType.getWrapped();
    }

    @Override
    protected AnalysisType getStrengthenStampType(AnalysisType originalType) {
        HostedType strengthenStampType = ((HostedType) converter.lookup(originalType)).getStrengthenStampType();
        return strengthenStampType == null ? null : strengthenStampType.getWrapped();
    }

    @Override
    protected FixedNode createInvokeWithNullReceiverReplacement(StructuredGraph graph) {
        /*
         * Since this only should happen in a runtime compiled method, we can directly insert a
         * deoptimize node.
         */
        VMError.guarantee(SubstrateCompilationDirectives.isRuntimeCompiledMethod(graph.method()), "Creating null check deoptimize in non-runtime compiled method: %s", graph.method());
        DeoptimizeNode deopt = graph.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.NullCheckException));
        return deopt;
    }

    @Override
    protected FixedNode createUnreachable(StructuredGraph graph, CoreProviders providers, Supplier<String> message) {
        FixedNode unreachableNode = graph.add(new LoweredDeadEndNode());

        /*
         * To aid debugging of static analysis problems, we can print details about why the place is
         * unreachable before failing fatally. But since these strings are long and not useful for
         * non-VM developers, we only do it when assertions are enabled for the image builder. And
         * Uninterruptible methods might not be able to access the heap yet for the error message
         * constant, so we skip it for such methods too.
         *
         * We also do not print out this message for runtime compiled methods and methods which can
         * deopt for testing because it would require us to preserve additional graph state.
         */
        boolean insertMessage = SubstrateUtil.assertionsEnabled() &&
                        !UninterruptibleAnnotationUtils.isUninterruptible(graph.method()) &&
                        !SubstrateCompilationDirectives.isRuntimeCompiledMethod(graph.method()) &&
                        !SubstrateCompilationDirectives.singleton().isRegisteredForDeoptTesting(graph.method());
        if (insertMessage) {
            ConstantNode messageNode = ConstantNode.forConstant(providers.getConstantReflection().forString(message.get()), providers.getMetaAccess(), graph);
            ForeignCallNode foreignCallNode = graph.add(new ForeignCallNode(SnippetRuntime.UNSUPPORTED_FEATURE, messageNode));
            foreignCallNode.setNext(unreachableNode);
            unreachableNode = foreignCallNode;
        }

        return unreachableNode;
    }

    @Override
    protected void setInvokeProfiles(Invoke invoke, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile) {
        if (needsProfiles(invoke.asNode().graph())) {
            ((SubstrateMethodCallTargetNode) invoke.callTarget()).setProfiles(typeProfile, typeProfile, methodProfile, methodProfile);
        }
    }

    protected void setInvokeProfiles(Invoke invoke, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile, JavaTypeProfile staticTypeProfile, JavaMethodProfile staticMethodProfile) {
        if (needsProfiles(invoke.asNode().graph())) {
            SubstrateMethodCallTargetNode substrateCallTarget = (SubstrateMethodCallTargetNode) invoke.callTarget();
            substrateCallTarget.setProfiles(typeProfile, staticTypeProfile, methodProfile, staticMethodProfile);
        }
    }

    protected static boolean needsProfiles(StructuredGraph graph) {
        /* We do not need any profiles in methods for JIT compilation at image run time. */
        return !SubstrateCompilationDirectives.isRuntimeCompiledMethod(graph.method());
    }

    /**
     * Tries to infer an artificial profile from analysis results. If successful, the profile is
     * assigned to the {@code instanceof} node.
     * <p>
     * The artificial profile of a type check {@code inputType instanceof checkedType} is based on
     * the potential types the static {@code inputType} can have. Artificial profiles are only added
     * if the number of possible types is very small. The maximum number of injected types depends
     * on the hub layout and whether the type check is an interface check.
     */
    @Override
    protected void maybeAssignInstanceOfProfiles(InstanceOfNode iof) {
        if (iof.graph() == null || !needsProfiles(iof.graph()) || !allowArtificialInstanceOfProfiles) {
            return;
        }

        ObjectStamp checkedStamp = iof.getCheckedStamp();
        if (checkedStamp.isExactType()) {
            return;
        }

        ObjectStamp inputStamp = (ObjectStamp) iof.getValue().stamp(NodeView.DEFAULT);
        HostedType inputType = (HostedType) converter.lookup(inputStamp.type());
        HostedType checkedType = (HostedType) converter.lookup(checkedStamp.type());

        int maxTypes = getMaxTypes(checkedType, iof.getOptions());

        /*
         * 1) Identify what should be collected.
         *
         * Depending on the branch probability, fast paths might be only useful for types that
         * succeed or fail the instanceof check.
         *
         * Corner case: If the static type of the inputType is Object, we decide to collect all
         * subtypes of the checkedType which are assignable to the inputType. This way only fast
         * paths for succeeding instanceof checks can be created. This is not done if the branch
         * profile suggests that the true branch probability is unlikely. In that case, no profile
         * is injected.
         */
        boolean collectAssignable = true;
        boolean collectNotAssignable = true;
        if (inputType != null) {
            CollectableTypes toCollect = identifyTypesToCollect(iof);
            collectAssignable = toCollect.collectPassing;
            collectNotAssignable = toCollect.collectFailing;
        } else {
            /* The static type of inputType is Object. */
            for (IfNode ifNode : iof.usages().filter(IfNode.class)) {
                if (ifNode.getTrueSuccessorProbability() <= 0.5) {
                    return;
                }
            }
            /*
             * Swap inputType and checkedType to collect just types which would succeed the
             * instanceof check.
             */
            inputType = checkedType;
            checkedType = null;
        }

        /*
         * 2) Collect types.
         *
         * Try to collect assignable and not assignable types up to maxTypes. If this fails or the
         * maximum number of types is exceeded, return without injecting a profile.
         */
        ArrayList<HostedType> assignableTypes = new ArrayList<>();
        ArrayList<HostedType> notAssignableTypes = new ArrayList<>();
        if (!collectTypesForInstanceofProfile(inputType, checkedType, maxTypes, collectAssignable, assignableTypes, collectNotAssignable, notAssignableTypes)) {
            return;
        }

        /*
         * 3) Create and inject the profile.
         *
         * The not-recorded probability is only zero if all possible types have been added under a
         * closed type world.
         */
        int numberOfTypes = assignableTypes.size() + notAssignableTypes.size();
        if (numberOfTypes > 0 && numberOfTypes <= maxTypes) {
            double notRecordedProbability = isClosedTypeWorld && collectAssignable && collectNotAssignable ? 0.0d : BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
            double probability = (1.0 - notRecordedProbability) / numberOfTypes;
            JavaTypeProfile.ProfiledType[] profiledTypes = createProfiledTypes(assignableTypes, notAssignableTypes, probability);

            JavaTypeProfile profile = new JavaTypeProfile(inputStamp.nonNull() ? TriState.FALSE : TriState.TRUE, notRecordedProbability, profiledTypes);
            iof.setProfile(profile, iof.getAnchor());
        }
    }

    /**
     * Uses the branch-profile information from the usages of {@code iof} to identify whether the
     * type check is likely to pass or fail. When the trusted profile indicates a tendency, only
     * exact types for the corresponding fast paths are selected. If no trusted profile establishes
     * a tendency, both passing and failing types are selected.
     *
     * @return the passing and failing type categories to collect
     */
    private static CollectableTypes identifyTypesToCollect(InstanceOfNode iof) {
        boolean collectPassing = false;
        boolean collectFailing = false;

        for (IfNode ifNode : iof.usages().filter(IfNode.class)) {
            if (ProfileData.ProfileSource.isTrusted(ifNode.profileSource())) {
                if (ifNode.getTrueSuccessorProbability() >= BranchProbabilityNode.LIKELY_PROBABILITY) {
                    collectPassing = true;
                } else if (ifNode.getTrueSuccessorProbability() <= BranchProbabilityNode.NOT_LIKELY_PROBABILITY) {
                    collectFailing = true;
                }
            } else {
                /* Untrusted profile data cannot justify excluding either category. */
                collectPassing = true;
                collectFailing = true;
                break;
            }
        }
        if (!collectPassing && !collectFailing) {
            /* Trusted profiles with balanced probabilities do not establish a tendency. */
            collectPassing = true;
            collectFailing = true;
        }

        return new CollectableTypes(collectPassing, collectFailing);
    }

    /** Passing and failing concrete-type categories selected for artificial profile synthesis. */
    private record CollectableTypes(boolean collectPassing, boolean collectFailing) {
    }

    /**
     * Creates profiled types from {@code assignableTypes} and {@code notAssignableTypes}. Each
     * profiled type has the same {@code probability}.
     */
    private static JavaTypeProfile.ProfiledType[] createProfiledTypes(ArrayList<HostedType> assignableTypes, ArrayList<HostedType> notAssignableTypes, double probability) {
        JavaTypeProfile.ProfiledType[] profiledTypes = new JavaTypeProfile.ProfiledType[assignableTypes.size() + notAssignableTypes.size()];
        int index = 0;
        for (HostedType type : assignableTypes) {
            profiledTypes[index++] = new JavaTypeProfile.ProfiledType(type, probability);
        }
        for (HostedType type : notAssignableTypes) {
            profiledTypes[index++] = new JavaTypeProfile.ProfiledType(type, probability);
        }
        return profiledTypes;
    }

    /** Returns the exact-type hint budget used by the selected type-check lowering. */
    private int getMaxTypes(HostedType checkedType, OptionValues options) {
        if (usesClosedTypeWorldHubLayout) {
            return TypeSnippets.getTypeCheckMaxHints(options);
        }
        return OpenTypeWorldSnippets.getTypeCheckMaxHints(checkedType.isInterface(), options);
    }

    /**
     * Collects reachable concrete subtypes of {@code inputType}. Depending on
     * {@code collectAssignable} and {@code collectNotAssignable}, types are classified relative to
     * {@code checkedType} and stored in {@code assignableTypes} or {@code notAssignableTypes}.
     *
     * @return whether all types were collected without encountering the object-array state
     *         explosion or exceeding {@code maxTypes}; when the limit is exceeded, the lists may
     *         contain the first over-budget type
     */
    private static boolean collectTypesForInstanceofProfile(HostedType inputType, HostedType checkedType, int maxTypes, boolean collectAssignable, ArrayList<HostedType> assignableTypes,
                    boolean collectNotAssignable, ArrayList<HostedType> notAssignableTypes) {
        ArrayDeque<HostedType> worklist = new ArrayDeque<>();

        checkConcreteAssignable(inputType, checkedType, assignableTypes, notAssignableTypes, collectAssignable, collectNotAssignable);
        worklist.add(inputType);
        while (!worklist.isEmpty()) {
            var elementalType = worklist.peek().getElementalType();
            if (elementalType.isJavaLangObject()) {
                /*
                 * Object arrays of arbitrary dimension lead to state explosion as AnyArray[][] is
                 * a subtype of Object[], etc.
                 */
                return false;
            }
            HostedType[] types = worklist.removeFirst().getSubTypes();
            for (HostedType type : types) {
                if (!type.getWrapped().isReachable()) {
                    continue;
                }
                if (checkConcreteAssignable(type, checkedType, assignableTypes, notAssignableTypes, collectAssignable, collectNotAssignable) &&
                                assignableTypes.size() + notAssignableTypes.size() > maxTypes) {
                    return false;
                }
                worklist.add(type);
            }
        }
        return true;
    }

    /**
     * If {@code inputType} is concrete, adds it to the selected list according to whether it is
     * assignable to {@code checkedType}.
     *
     * @return whether {@code inputType} was added to one of the lists
     */
    private static boolean checkConcreteAssignable(HostedType inputType, HostedType checkedType, ArrayList<HostedType> assignableTypes, ArrayList<HostedType> notAssignableTypes,
                    boolean collectAssignable, boolean collectNotAssignable) {
        if (!inputType.isInterface() && inputType.isConcrete()) {
            if (checkedType == null || checkedType.isAssignableFrom(inputType)) {
                if (collectAssignable) {
                    assignableTypes.add(inputType);
                    return true;
                }
            } else if (collectNotAssignable) {
                notAssignableTypes.add(inputType);
                return true;
            }
        }
        return false;
    }

    @Override
    protected String getTypeName(AnalysisType type) {
        return HostedStringDeduplication.singleton().deduplicate(type.toJavaName(true), false);
    }

    @Override
    protected boolean simplifyDelegate(Node n, SimplifierTool tool, Predicate<Node> isUnreachable) {
        if (n instanceof InlinedInvokeArgumentsNode inlinedInvokeArgumentsNode) {
            if (isUnreachable.test(inlinedInvokeArgumentsNode)) {
                /* If node is unreachable, then the entire branch should be removed. */
                StructuredGraph graph = (StructuredGraph) n.graph();
                Supplier<String> message = () -> String.format("Method %s, Unreachable InlinedInvokeArgumentsNode: %s", StrengthenGraphs.getQualifiedName(graph), inlinedInvokeArgumentsNode);
                FixedNode unreachableNode = createUnreachable(graph, tool, message);
                ((FixedWithNextNode) inlinedInvokeArgumentsNode.predecessor()).setNext(unreachableNode);
                GraphUtil.killCFG(inlinedInvokeArgumentsNode);
            } else {
                /*
                 * Otherwise, InlinedInvokeArgumentsNode is only necessary for analysis and can be
                 * removed once StrengthenGraphs is reached.
                 */
                inlinedInvokeArgumentsNode.graph().removeFixed(inlinedInvokeArgumentsNode);
            }
            return true;
        }
        return false;
    }
}
