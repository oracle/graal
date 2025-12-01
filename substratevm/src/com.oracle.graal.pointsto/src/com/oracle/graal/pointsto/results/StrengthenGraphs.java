/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.results;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLowerThanNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase.CustomSimplification;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * This class applies static analysis results directly to the {@link StructuredGraph Graal IR} used
 * to build the type flow graph.
 *
 * It uses a {@link CustomSimplification} for the {@link CanonicalizerPhase}, because that provides
 * all the framework for iterative stamp propagation and adding/removing control flow nodes while
 * processing the graph.
 *
 * From the single-method view that the compiler has when later compiling the graph, static analysis
 * results appear "out of thin air": At some random point in the graph, we suddenly have a more
 * precise type (= stamp) for a value. Since many nodes are floating, and even currently fixed nodes
 * might float later, we need to be careful that all information coming from the type flow graph
 * remains properly anchored to the point where the static analysis actually proved the information.
 * So we cannot just change the stamp of, e.g., the parameter of a method invocation, to a more
 * precise stamp. We need to do that indirectly by adding a {@link PiNode} that is anchored using a
 * {@link ValueAnchorNode}.
 */
public abstract class StrengthenGraphs {

    public static class Options {
        @Option(help = "Perform constant folding in StrengthenGraphs")//
        public static final OptionKey<Boolean> StrengthenGraphWithConstants = new OptionKey<>(true);
    }

    /**
     * The hashCode implementation of {@link JavaMethodProfile} is bad because it does not take the
     * actual methods into account, only the number of methods in the profile. This wrapper class
     * provides a proper hashCode.
     */
    record CachedJavaMethodProfile(JavaMethodProfile profile, int hash) {
        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CachedJavaMethodProfile other) {
                return profile.equals(other.profile);
            }
            return false;
        }
    }

    protected final BigBang bb;
    /**
     * The universe used to convert analysis metadata to hosted metadata, or {@code null} if no
     * conversion should be performed.
     */
    protected final Universe converter;

    private final Map<JavaTypeProfile, JavaTypeProfile> cachedTypeProfiles = new ConcurrentHashMap<>();
    private final Map<CachedJavaMethodProfile, CachedJavaMethodProfile> cachedMethodProfiles = new ConcurrentHashMap<>();

    /* Cached option values to avoid repeated option lookup. */
    private final int analysisSizeCutoff;
    protected final boolean strengthenGraphWithConstants;

    private final StrengthenGraphsCounters beforeCounters;
    private final StrengthenGraphsCounters afterCounters;

    /** Used to avoid aggressive optimizations for open type world analysis. */
    protected final boolean isClosedTypeWorld;

    protected final boolean buildingSharedLayer;

    public StrengthenGraphs(BigBang bb, Universe converter) {
        this.bb = bb;
        this.converter = converter;

        analysisSizeCutoff = PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions());
        strengthenGraphWithConstants = Options.StrengthenGraphWithConstants.getValue(bb.getOptions());

        if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(bb.getOptions())) {
            beforeCounters = new StrengthenGraphsCounters(ImageBuildStatistics.CheckCountLocation.BEFORE_STRENGTHEN_GRAPHS);
            afterCounters = new StrengthenGraphsCounters(ImageBuildStatistics.CheckCountLocation.AFTER_STRENGTHEN_GRAPHS);
            reportNeverNullInstanceFields(bb);
        } else {
            beforeCounters = null;
            afterCounters = null;
        }
        HostVM hostVM = converter.hostVM();
        this.isClosedTypeWorld = hostVM.isClosedTypeWorld();
        this.buildingSharedLayer = hostVM.buildingSharedLayer();
    }

    private static void reportNeverNullInstanceFields(BigBang bb) {
        int neverNull = 0;
        int canBeNull = 0;
        for (var field : bb.getUniverse().getFields()) {
            if (!field.isStatic() && field.isReachable() && field.getType().getStorageKind() == JavaKind.Object && field instanceof PointsToAnalysisField ptaField) {
                /* If the field flow is saturated we must assume it can be null. */
                if (ptaField.getSinkFlow().isSaturated() || ptaField.getSinkFlow().getState().canBeNull()) {
                    canBeNull++;
                } else {
                    neverNull++;
                }
            }
        }
        ImageBuildStatistics imageBuildStats = ImageBuildStatistics.counters();
        imageBuildStats.createCounter("instancefield_neverNull").addAndGet(neverNull);
        imageBuildStats.createCounter("instancefield_canBeNull").addAndGet(canBeNull);
    }

    @SuppressWarnings("try")
    public final void applyResults(AnalysisMethod method) {

        if (method.analyzedInPriorLayer()) {
            /*
             * The method was already strengthened in a prior layer. If the graph was persisted, it
             * will be loaded on demand during compilation, so there is no need to strengthen it in
             * this layer.
             *
             * GR-59646: The graphs from the base layer could be strengthened again in the
             * application layer using closed world assumptions.
             */
            return;
        }

        if (method.getAnalyzedGraph() == null) {
            /* Method was not analyzed, so there is nothing to strengthen. */
            return;
        }

        var nodeReferences = method.getEncodedNodeReferences();
        var debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).build();
        var graph = method.decodeAnalyzedGraph(debug, nodeReferences);

        preStrengthenGraphs(graph, method);

        graph.resetDebug(debug);
        if (beforeCounters != null) {
            beforeCounters.collect(graph);
        }
        try (var s = debug.scope("StrengthenGraphs", graph); var a = debug.activate()) {
            new AnalysisStrengthenGraphsPhase(method, graph).apply(graph, bb.getProviders(method));
        } catch (Throwable ex) {
            debug.handle(ex);
        }
        if (afterCounters != null) {
            afterCounters.collect(graph);
        }

        postStrengthenGraphs(graph, method);

        /* Preserve the strengthened graph in an encoded format. */
        method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE));

        persistStrengthenGraph(method);

        if (nodeReferences != null) {
            /* Ensure the temporarily decoded graph is not kept alive via the node references. */
            for (var nodeReference : nodeReferences) {
                nodeReference.clear();
            }
        }
    }

    protected abstract void preStrengthenGraphs(StructuredGraph graph, AnalysisMethod method);

    protected abstract void postStrengthenGraphs(StructuredGraph graph, AnalysisMethod method);

    protected abstract void persistStrengthenGraph(AnalysisMethod method);

    /**
     * Returns a type that can replace the original type in stamps as an exact type. When the
     * returned type is the original type itself, the original type has no subtype and can be used
     * as an exact type.
     * <p>
     * Returns {@code null} if there is no optimization potential, i.e., if the original type
     * doesn't have a unique implementor type, or we cannot prove that it has a unique implementor
     * type due to open-world analysis.
     */
    protected abstract AnalysisType getSingleImplementorType(AnalysisType originalType);

    /**
     * Returns a type that can replace the original type in stamps.
     * <p>
     * Returns {@code null} if the original type has no assignable type that is instantiated, i.e.,
     * the code using the type is unreachable.
     * <p>
     * Returns the original type itself if there is no optimization potential, i.e., if the original
     * type itself is instantiated or has more than one instantiated direct subtype, or we cannot
     * prove that it doesn't have any instantiated subtype due to open-world analysis.
     */
    protected abstract AnalysisType getStrengthenStampType(AnalysisType originalType);

    protected abstract FixedNode createUnreachable(StructuredGraph graph, CoreProviders providers, Supplier<String> message);

    protected abstract FixedNode createInvokeWithNullReceiverReplacement(StructuredGraph graph);

    protected abstract void setInvokeProfiles(Invoke invoke, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile);

    protected abstract String getTypeName(AnalysisType type);

    protected abstract boolean simplifyDelegate(Node n, SimplifierTool tool);

    /* Wrapper to clearly identify phase in IGV graph dumps. */
    public class AnalysisStrengthenGraphsPhase extends BasePhase<CoreProviders> {
        final CanonicalizerPhase phase;

        AnalysisStrengthenGraphsPhase(AnalysisMethod method, StructuredGraph graph) {
            ReachabilitySimplifier simplifier;
            if (bb.isPointsToAnalysis()) {
                simplifier = new TypeFlowSimplifier(StrengthenGraphs.this, method, graph);
            } else {
                simplifier = new ReachabilitySimplifier(StrengthenGraphs.this, method, graph);
            }
            phase = CanonicalizerPhase.create().copyWithCustomSimplification(simplifier);
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return phase.notApplicableTo(graphState);
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            phase.apply(graph, context);
        }

        @Override
        public CharSequence getName() {
            return "AnalysisStrengthenGraphs";
        }
    }

    @SuppressWarnings("unused")
    protected void maybeAssignInstanceOfProfiles(InstanceOfNode iof) {
        // placeholder
    }

    static String getQualifiedName(StructuredGraph graph) {
        return ((AnalysisMethod) graph.method()).getQualifiedName();
    }

    protected JavaTypeProfile makeTypeProfile(TypeState typeState, boolean injectNotRecordedProbability) {
        if (typeState == null || analysisSizeCutoff != -1 && typeState.typesCount() > analysisSizeCutoff) {
            return null;
        }
        var created = createTypeProfile(typeState, injectNotRecordedProbability);
        var existing = cachedTypeProfiles.putIfAbsent(created, created);
        return existing != null ? existing : created;
    }

    private JavaTypeProfile createTypeProfile(TypeState typeState, boolean injectNotRecordedProbability) {
        final double notRecordedProb = injectNotRecordedProbability ? BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY : 0.0d;
        final double probability = (1.0 - notRecordedProb) / typeState.typesCount();

        Stream<? extends ResolvedJavaType> stream = typeState.typesStream(bb);
        if (converter != null) {
            stream = stream.map(converter::lookup).sorted(converter.hostVM().getTypeComparator());
        }
        JavaTypeProfile.ProfiledType[] pitems = stream
                        .map(type -> new JavaTypeProfile.ProfiledType(type, probability))
                        .toArray(JavaTypeProfile.ProfiledType[]::new);

        return new JavaTypeProfile(TriState.get(typeState.canBeNull()), notRecordedProb, pitems);
    }

    protected JavaMethodProfile makeMethodProfile(Collection<AnalysisMethod> callees, boolean injectNotRecordedProbability) {
        if (analysisSizeCutoff != -1 && callees.size() > analysisSizeCutoff) {
            return null;
        }
        var created = createMethodProfile(callees, injectNotRecordedProbability);
        var existing = cachedMethodProfiles.putIfAbsent(created, created);
        return existing != null ? existing.profile : created.profile;
    }

    private CachedJavaMethodProfile createMethodProfile(Collection<AnalysisMethod> callees, boolean injectNotRecordedProbability) {
        JavaMethodProfile.ProfiledMethod[] pitems = new JavaMethodProfile.ProfiledMethod[callees.size()];
        int hashCode = 0;
        final double notRecordedProb = injectNotRecordedProbability ? BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY : 0.0d;
        final double probability = (1.0 - notRecordedProb) / pitems.length;

        int idx = 0;
        for (AnalysisMethod aMethod : callees) {
            ResolvedJavaMethod convertedMethod = converter == null ? aMethod : converter.lookup(aMethod);
            pitems[idx++] = new JavaMethodProfile.ProfiledMethod(convertedMethod, probability);
            hashCode = hashCode * 31 + convertedMethod.hashCode();
        }
        return new CachedJavaMethodProfile(new JavaMethodProfile(notRecordedProb, pitems), hashCode);
    }
}

/**
 * Infrastructure for collecting detailed counters that capture the benefits of strengthening
 * graphs. The counter dumping is handled by {@link ImageBuildStatistics}.
 */
final class StrengthenGraphsCounters {

    enum Counter {
        METHOD,
        BLOCK,
        IS_NULL,
        INSTANCE_OF,
        PRIM_CMP,
        INVOKE_STATIC,
        INVOKE_DIRECT,
        INVOKE_INDIRECT,
        LOAD_FIELD,
        CONSTANT,
    }

    private final AtomicLong[] values;

    StrengthenGraphsCounters(ImageBuildStatistics.CheckCountLocation location) {
        values = new AtomicLong[Counter.values().length];

        ImageBuildStatistics imageBuildStats = ImageBuildStatistics.counters();
        for (Counter counter : Counter.values()) {
            values[counter.ordinal()] = imageBuildStats.createCounter(counter.name(), location);
        }
    }

    void collect(StructuredGraph graph) {
        int[] localValues = new int[Counter.values().length];

        inc(localValues, Counter.METHOD);
        for (Node n : graph.getNodes()) {
            if (n instanceof AbstractBeginNode) {
                inc(localValues, Counter.BLOCK);
            } else if (n instanceof ConstantNode) {
                inc(localValues, Counter.CONSTANT);
            } else if (n instanceof LoadFieldNode) {
                inc(localValues, Counter.LOAD_FIELD);
            } else if (n instanceof IfNode node) {
                collect(localValues, node.condition());
            } else if (n instanceof ConditionalNode node) {
                collect(localValues, node.condition());
            } else if (n instanceof MethodCallTargetNode node) {
                collect(localValues, node.invokeKind());
            }
        }

        for (int i = 0; i < localValues.length; i++) {
            values[i].addAndGet(localValues[i]);
        }
    }

    private static void collect(int[] localValues, LogicNode condition) {
        if (condition instanceof IsNullNode) {
            inc(localValues, Counter.IS_NULL);
        } else if (condition instanceof InstanceOfNode) {
            inc(localValues, Counter.INSTANCE_OF);
        } else if (condition instanceof IntegerEqualsNode || condition instanceof IntegerLowerThanNode) {
            inc(localValues, Counter.PRIM_CMP);
        }
    }

    private static void collect(int[] localValues, CallTargetNode.InvokeKind invokeKind) {
        switch (invokeKind) {
            case Static:
                inc(localValues, Counter.INVOKE_STATIC);
                break;
            case Virtual:
            case Interface:
                inc(localValues, Counter.INVOKE_INDIRECT);
                break;
            case Special:
                inc(localValues, Counter.INVOKE_DIRECT);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(invokeKind);
        }
    }

    private static void inc(int[] localValues, Counter counter) {
        localValues[counter.ordinal()]++;
    }
}
