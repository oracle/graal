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
package com.oracle.svm.hosted.classinitialization;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysis;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.fieldfolding.MarkStaticFinalFieldInitializedNode;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.phases.InlineBeforeAnalysisGraphDecoderImpl;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.FinalFieldBarrierNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.PEGraphDecoder;
import jdk.vm.ci.meta.JavaConstant;

/**
 * The main entry point for simulation of class initializer.
 *
 * A class initializer is executed exactly once before the first usage of a class. The specification
 * requires that this happens as late as possible just before the first usage. But if executing a
 * class initializer does not have any side effects, i.e., does not depend on any external state and
 * does not modify any external state, then there is no observable difference when already starting
 * out with the class pre-initialized at image run time. However, we do not want to really
 * initialize the class in the image generator VM, because the class could be used by Feature code
 * that runs at image build time, and we do not want to inherit any static state modified by a
 * Feature. Therefore, we simulate the class initializer.
 *
 * A class with a simulated class initializer has the following initialization status: 1) Its
 * initialization status in the hosting VM that runs the image generator does not matter. The class
 * may or may not be initialized there. But in any case, static field values of the hosting VM are
 * not visible at run time. 2) The class starts out as initialized at image run time, i.e., there
 * are no run-time class initialization checks necessary and the class initializer itself is not AOT
 * compiled. Static field values in the image heap reflect the status after the class initializer.
 *
 * Simulation determines whether a class initializer is side-effect free and does not depend on
 * external state, i.e., if executing the class initializer before the first usage of the class does
 * not lead to any observable differences (except timing differences).
 *
 * Examples of what can be simulated:
 * <ul>
 * <li>Allocations of instances, and allocations of arrays with a constant length. This is safe
 * because a class initializer is a single-execute method, and all loops are unrolled during
 * simulation.</li>
 * <li>Reads and writes of objects (instances and arrays) that are allocated in the same class
 * initializer.</li>
 * <li>Reads of immutable state of classes that are simulated themselves or that are initialized at
 * image build time.</li>
 * <li>Cyclic class initializer dependencies where all members of the cycle can be simulated.</li>
 * <li>Arithmetic or conditions that can be constant folded.</li>
 * </ul>
 * 
 * Examples of what prevents a class initializer from being simulated:
 * <ul>
 * <li>Reads or writes of mutable state of another class, i.e., accesses of non-final static fields
 * or instance fields, or accesses of arrays that were read from final fields.</li>
 * <li>Dependencies on another class that are not simulated themselves or that are not initialized
 * at image build time.</li>
 * <li>Invokes that cannot be de-virtualized</li>
 * <li>Invokes of native methods.</li>
 * <li>Loops that cannot be fully unrolled.</li>
 * <li>Arithmetic or conditions that cannot be constant folded.</li>
 * </ul>
 *
 * Simulation is implemented using partial evaluation of Graal IR, leveraging
 * {@link PEGraphDecoder}. The class initializer itself and any method that partial evaluation
 * reaches is parsed using {@link AnalysisParsedGraph}. The parsed graphs are shared with the static
 * analysis, i.e., there is no special bytecode parsing (like special graph builder plugins). The
 * simulation is similar to {@link InlineBeforeAnalysis}, but with significantly more
 * canonicalizations done in {@link SimulateClassInitializerGraphDecoder#doCanonicalizeFixedNode}.
 *
 * To support cyclic class initializer dependencies, all not-yet-simulated classes referenced by a
 * class initializer are handled as part of the same {@link SimulateClassInitializerCluster}. Each
 * {@link SimulateClassInitializerClusterMember} goes through states defined in
 * {@link SimulateClassInitializerStatus}: Only when all members of a cycle have been analyzed, the
 * results for the whole cycle are published, i.e., either all or none of the members are marked as
 * simulated.
 *
 * The graph decoder implementation {@link SimulateClassInitializerGraphDecoder} extends the
 * {@link InlineBeforeAnalysisGraphDecoder}, which allows to abort inlining of a method. This "abort
 * a particular inlining" is not necessary in "production mode" where such an abort immediately
 * marks the class initializer as "simulation not possible". But for diagnostic purposes, a
 * {@link #collectAllReasons} mode can be enabled. In this mode, as many reasons as possible are
 * collected why simulation is not possible. When users try to make more class initializer
 * simulateable, having as many reasons as possible is helpful.
 *
 * The results of the simulation are available before the static analysis, i.e., if a class
 * initializer is simulated it is not seen as a root method by the static analysis. This is
 * important because otherwise static final fields in the class would still be seen as written by
 * the static analysis. However, it is not allowed to use simulation results already during bytecode
 * parsing: since simulation relies on parsing arbitrary methods that might be reachable from a
 * class initializer, using simulation results during parsing would lead to cyclic dependencies. In
 * the best case that would be bytecode parsing deadlocks, in the worst case it would be
 * non-deterministic usage of simulation information depending on the order in which classes are
 * analyzed. Therefore, the simulation results are not used by
 * {@link AnalysisConstantReflectionProvider} (only by {@link HostedConstantReflectionProvider}) and
 * they are not used by {@link AnalysisType#isInitialized} ( only by
 * {@link HostedType#isInitialized}). In order to use simulation results before the static analysis,
 * the simulation results are used by {@link InlineBeforeAnalysis} (see the implementation of
 * {@link InlineBeforeAnalysisGraphDecoderImpl}).
 */
public class SimulateClassInitializerSupport {

    protected final ClassInitializationSupport classInitializationSupport = ClassInitializationSupport.singleton();
    protected final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();
    protected final SimulateClassInitializerPolicy simulateClassInitializerPolicy;
    protected final SimulateClassInitializerConstantFieldProvider simulatedFieldValueConstantFieldProvider;

    /** The main data structure that stores all published results of the simulation. */
    protected final ConcurrentMap<AnalysisType, SimulateClassInitializerResult> analyzedClasses = new ConcurrentHashMap<>();

    protected final boolean enabled = ClassInitializationOptions.SimulateClassInitializer.getValue();
    /* Cached value of options to avoid frequent lookup of option values. */
    protected final boolean collectAllReasons = ClassInitializationOptions.SimulateClassInitializerCollectAllReasons.getValue();
    protected final int maxInlineDepth = ClassInitializationOptions.SimulateClassInitializerMaxInlineDepth.getValue();
    protected final int maxLoopIterations = ClassInitializationOptions.SimulateClassInitializerMaxLoopIterations.getValue();
    protected final int maxAllocatedBytes = ClassInitializationOptions.SimulateClassInitializerMaxAllocatedBytes.getValue();

    public static SimulateClassInitializerSupport singleton() {
        return ImageSingletons.lookup(SimulateClassInitializerSupport.class);
    }

    @SuppressWarnings("this-escape")
    public SimulateClassInitializerSupport(AnalysisMetaAccess aMetaAccess, SVMHost hostVM) {
        simulateClassInitializerPolicy = new SimulateClassInitializerPolicy(hostVM, this);
        simulatedFieldValueConstantFieldProvider = new SimulateClassInitializerConstantFieldProvider(aMetaAccess, hostVM, this);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Initiate the simulation of the class initializer, unless there is already a published result
     * available.
     *
     * The method returns true if the provided type is either initialized at build time or the
     * simulation succeeded, i.e., if the type starts out as "initialized" at image run time.
     */
    @SuppressWarnings("try")
    public boolean trySimulateClassInitializer(BigBang bb, AnalysisType type) {
        var existingResult = lookupPublishedSimulateClassInitializerResult(type);
        if (existingResult != null) {
            return existingResult.simulatedInitialized;
        }

        var debug = new DebugContext.Builder(bb.getOptions()).build();
        try (var scope = debug.scope("SimulateClassInitializer", type)) {
            /* Entry point to the analysis: start a new cluster of class initializers. */
            var cluster = new SimulateClassInitializerCluster(this, bb);
            boolean result = trySimulateClassInitializer(debug, type, cluster, null);

            for (var clusterMember : cluster.clusterMembers.getValues()) {
                VMError.guarantee(clusterMember.status.published, "All members of the cluster must be published at the end of the analysis");
            }
            return result;

        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    boolean trySimulateClassInitializer(DebugContext debug, AnalysisType type, SimulateClassInitializerClusterMember dependant) {
        var existingResult = lookupPublishedSimulateClassInitializerResult(type);
        if (existingResult != null) {
            return existingResult.simulatedInitialized;
        }
        return trySimulateClassInitializer(debug, type, dependant.cluster, dependant);
    }

    /**
     * Tries to constant-fold the field access based on results from the class initializer
     * simulation.
     */
    public ConstantNode tryCanonicalize(BigBang bb, LoadFieldNode node) {
        var field = (AnalysisField) node.field();
        var simulatedFieldValue = getSimulatedFieldValue(field);
        if (simulatedFieldValue == null) {
            return null;
        }

        /*
         * We need to go via a ConstantFieldProvider to produce the ConstantNode so that stable
         * array dimensions are properly computed. But we cannot use the ConstantFieldProvider from
         * the providers, because our regular implementation only folds static fields for types
         * where isInitialized() returns true. For our simulated types, AnalysisType.isInitialized()
         * is still false because that method needs to return a stable value that does not change
         * while the analysis is running.
         */
        return simulatedFieldValueConstantFieldProvider.readConstantField(field, new ConstantFieldProvider.ConstantFieldTool<>() {
            @Override
            public JavaConstant readValue() {
                return simulatedFieldValue;
            }

            @Override
            public JavaConstant getReceiver() {
                /* We are only reading static fields. */
                return null;
            }

            @Override
            public Object getReason() {
                return node.getNodeSourcePosition();
            }

            @Override
            public ConstantNode foldConstant(JavaConstant constant) {
                return constant == null ? null : ConstantNode.forConstant(constant, bb.getMetaAccess());
            }

            @Override
            public ConstantNode foldStableArray(JavaConstant constant, int stableDimensions, boolean isDefaultStable) {
                return constant == null ? null : ConstantNode.forConstant(constant, stableDimensions, isDefaultStable, bb.getMetaAccess());
            }

            @Override
            public OptionValues getOptions() {
                return bb.getOptions();
            }
        });
    }

    /**
     * Returns the simulated value of a static field, or null if no such value is available.
     */
    public JavaConstant getSimulatedFieldValue(AnalysisField field) {
        if (!enabled || !field.isStatic()) {
            return null;
        }

        var existingResult = analyzedClasses.get(field.getDeclaringClass());
        if (existingResult == null || !existingResult.simulatedInitialized) {
            return null;
        }

        var simulatedFieldValue = existingResult.staticFieldValues.get(field);
        if (simulatedFieldValue != null) {
            return simulatedFieldValue;
        } else {
            /*
             * The JVM specification requires that all static final fields are explicitly
             * initialized, even when they have the default value. So we should never hit this path
             * for a static final field. But languages like Scala violate the specification and
             * write static final fields outside the class initializer. Therefore, we must return
             * null here to indicate that we do not know the value yet.
             */
            return null;
        }
    }

    public boolean isClassInitializerSimulated(AnalysisType type) {
        var existingResult = lookupPublishedSimulateClassInitializerResult(type);
        if (existingResult != null) {
            return existingResult.simulatedInitialized;
        }
        return false;
    }

    private SimulateClassInitializerResult lookupPublishedSimulateClassInitializerResult(AnalysisType type) {
        if (!type.isLinked()) {
            return SimulateClassInitializerResult.NOT_SIMULATED_INITIALIZED;
        } else if (type.isInitialized()) {
            /*
             * Type is registered as "initialize at build time", so class initializer is already
             * executed by the host VM that runs the image generator.
             */
            return SimulateClassInitializerResult.INITIALIZED_HOSTED;
        }
        VMError.guarantee(!type.isArray() && !type.isPrimitive(), "array and primitive types are always initialized at build time");

        if (!enabled) {
            return SimulateClassInitializerResult.NOT_SIMULATED_INITIALIZED;
        }
        return analyzedClasses.get(type);
    }

    boolean trySimulateClassInitializer(DebugContext debug, AnalysisType type, SimulateClassInitializerCluster cluster, SimulateClassInitializerClusterMember dependant) {
        var existingClusterMember = cluster.clusterMembers.get(type);
        if (existingClusterMember != null) {
            assert !existingClusterMember.status.published : type + ": " + existingClusterMember.status;
            /* Cycle in class initializer dependencies. */
            dependant.dependencies.add(existingClusterMember);
            return false;
        }

        var clusterMember = new SimulateClassInitializerClusterMember(cluster, type);
        if (dependant != null) {
            dependant.dependencies.add(clusterMember);
        }

        checkStrictlyInitializeAtRunTime(clusterMember);
        if (clusterMember.notInitializedReasons.size() == 0 || collectAllReasons) {
            addSuperDependencies(debug, clusterMember);
        }
        if (clusterMember.notInitializedReasons.size() == 0 || collectAllReasons) {
            addClassInitializerDependencies(clusterMember);
        }

        clusterMember.status = SimulateClassInitializerStatus.INIT_CANDIDATE;

        EconomicSet<SimulateClassInitializerClusterMember> transitiveDependencies = EconomicSet.create();
        boolean dependenciesMissing = collectTransitiveDependencies(clusterMember, transitiveDependencies);
        if (dependenciesMissing) {
            /* Cycle is not fully processed yet, delay publishing results. */
            return false;
        }

        boolean allDependenciesSimulated = true;
        for (var dependency : transitiveDependencies) {
            assert dependency.status != SimulateClassInitializerStatus.COLLECTING_DEPENDENCIES : dependency.type + ": " + dependency.status;
            if (dependency.status == SimulateClassInitializerStatus.PUBLISHED_AS_NOT_INITIALIZED || dependency.notInitializedReasons.size() > 0) {
                allDependenciesSimulated = false;
                break;
            }
        }

        publishResults(debug, allDependenciesSimulated, transitiveDependencies);
        return allDependenciesSimulated;
    }

    private void checkStrictlyInitializeAtRunTime(SimulateClassInitializerClusterMember clusterMember) {
        var clazz = clusterMember.type.getJavaClass();
        if (classInitializationSupport.specifiedInitKindFor(clazz) == InitKind.RUN_TIME && classInitializationSupport.isStrictlyDefined(clazz)) {
            /*
             * The class itself (not just the whole package) is registered as
             * "initialize at run time", so we honor that registration. There was hopefully a good
             * reason for the explicit registration.
             */
            clusterMember.notInitializedReasons.add("Class is strictly defined as initialize at run time");
        }
    }

    private void addSuperDependencies(DebugContext debug, SimulateClassInitializerClusterMember clusterMember) {
        if (clusterMember.type.isInterface()) {
            /*
             * Initialization of an interface does not trigger initialization of any
             * super-interface, even in the case of default methods.
             */
            return;
        }
        var supertype = clusterMember.type.getSuperclass();
        if (supertype != null) {
            addDependency(debug, clusterMember, supertype, supertype);
        }
        addInterfaceDependencies(debug, clusterMember.type, clusterMember);
    }

    private void addInterfaceDependencies(DebugContext debug, AnalysisType type, SimulateClassInitializerClusterMember clusterMember) {
        for (var iface : type.getInterfaces()) {
            if (iface.declaresDefaultMethods()) {
                /*
                 * An interface that declares default methods is initialized when a class
                 * implementing it is initialized.
                 */
                addDependency(debug, clusterMember, iface, iface);
            } else {
                /*
                 * An interface that does not declare default methods is independent from a class
                 * that implements it, i.e., the interface can still be uninitialized even when the
                 * class is initialized.
                 */
                addInterfaceDependencies(debug, iface, clusterMember);
            }
        }
    }

    private void addDependency(DebugContext debug, SimulateClassInitializerClusterMember clusterMember, AnalysisType newDependency, Object reason) {
        var dependencyResult = lookupPublishedSimulateClassInitializerResult(newDependency);
        if (dependencyResult != null) {
            if (!dependencyResult.simulatedInitialized) {
                clusterMember.notInitializedReasons.add(reason);
            }
            return;
        }
        trySimulateClassInitializer(debug, newDependency, clusterMember.cluster, clusterMember);
    }

    private void addClassInitializerDependencies(SimulateClassInitializerClusterMember clusterMember) {
        var classInitializer = clusterMember.type.getClassInitializer();
        if (classInitializer == null) {
            return;
        }

        StructuredGraph graph;
        try {
            graph = decodeGraph(clusterMember, classInitializer);
        } catch (SimulateClassInitializerAbortException ignored) {
            VMError.guarantee(!clusterMember.notInitializedReasons.isEmpty(), "Reason must be added before throwing the abort-exception");
            return;
        }

        for (Node node : graph.getNodes()) {
            processEffectsOfNode(clusterMember, node);
        }
    }

    @SuppressWarnings("try")
    private StructuredGraph decodeGraph(SimulateClassInitializerClusterMember clusterMember, AnalysisMethod classInitializer) {
        var bb = clusterMember.cluster.bb;
        var analysisParsedGraph = classInitializer.ensureGraphParsed(bb);
        var description = new DebugContext.Description(classInitializer, ClassUtil.getUnqualifiedName(classInitializer.getClass()) + ":" + classInitializer.getId());
        var debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getProviders(classInitializer).getSnippetReflection())).description(description).build();

        var result = new StructuredGraph.Builder(bb.getOptions(), debug)
                        .method(classInitializer)
                        .recordInlinedMethods(false)
                        .trackNodeSourcePosition(analysisParsedGraph.getEncodedGraph().trackNodeSourcePosition())
                        .build();

        try (var scope = debug.scope("SimulateClassInitializerGraphDecoder", result)) {

            var decoder = createGraphDecoder(clusterMember, bb, result);
            decoder.decode(classInitializer);
            debug.dump(DebugContext.BASIC_LEVEL, result, "SimulateClassInitializer after decode");

            CanonicalizerPhase.create().apply(result, clusterMember.cluster.providers);
            return result;

        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    protected SimulateClassInitializerGraphDecoder createGraphDecoder(SimulateClassInitializerClusterMember clusterMember, BigBang bb, StructuredGraph result) {
        return new SimulateClassInitializerGraphDecoder(bb, simulateClassInitializerPolicy, clusterMember, result);
    }

    private static void processEffectsOfNode(SimulateClassInitializerClusterMember clusterMember, Node node) {
        if (node instanceof StartNode || node instanceof BeginNode || node instanceof UnreachableBeginNode || node instanceof UnreachableControlSinkNode || node instanceof FullInfopointNode) {
            /* Boilerplate nodes in a graph that do not lead to any machine code. */
            return;
        } else if (node instanceof ReturnNode returnNode) {
            assert returnNode.result() == null : "Class initializer always has return type void";
            return;
        } else if (node instanceof VirtualState) {
            /* All kinds of FrameState can be ignored. */
            return;
        } else if (node instanceof VirtualObjectNode) {
            /*
             * Can still be referenced by a FrameState even when all allocations were partially
             * evaluated into image heap constants.
             */
            return;
        } else if (node instanceof ConstantNode) {
            return;
        } else if (node instanceof FinalFieldBarrierNode) {
            return;
        } else if (node instanceof MarkStaticFinalFieldInitializedNode) {
            return;

        } else if (node instanceof StoreFieldNode storeFieldNode) {
            var field = (AnalysisField) storeFieldNode.field();
            if (field.isStatic() && field.getDeclaringClass().equals(clusterMember.type)) {
                var constantValue = storeFieldNode.value().asJavaConstant();
                if (constantValue != null) {
                    clusterMember.staticFieldValues.put(field, constantValue);
                    return;
                }
            }
        }

        clusterMember.notInitializedReasons.add(node);
    }

    private void publishResults(DebugContext debug, boolean simulatedInitialized, EconomicSet<SimulateClassInitializerClusterMember> transitiveDependencies) {
        for (var clusterMember : transitiveDependencies) {
            if (clusterMember.status.published) {
                continue;
            }
            assert clusterMember.status == SimulateClassInitializerStatus.INIT_CANDIDATE : clusterMember.type + ": " + clusterMember.status;

            SimulateClassInitializerResult existingResult;
            if (simulatedInitialized) {
                assert clusterMember.notInitializedReasons.isEmpty() : clusterMember.type + ": " + clusterMember.notInitializedReasons;
                if (debug.isLogEnabled(DebugContext.BASIC_LEVEL)) {
                    debug.log("simulated: %s", clusterMember.type.toJavaName(true));
                }
                existingResult = analyzedClasses.putIfAbsent(clusterMember.type, SimulateClassInitializerResult.forInitialized(clusterMember.staticFieldValues));
                clusterMember.status = SimulateClassInitializerStatus.PUBLISHED_AS_INITIALIZED;

            } else {
                if (debug.isLogEnabled(DebugContext.BASIC_LEVEL)) {
                    debug.log("not simulated: %s:%n    %s", clusterMember.type.toJavaName(true),
                                    clusterMember.notInitializedReasons.stream()
                                                    .map(reason -> reasonToString(clusterMember.cluster.providers, reason))
                                                    .filter(s -> s != null && !s.isEmpty())
                                                    .collect(Collectors.joining(System.lineSeparator() + "    ")));
                }
                existingResult = analyzedClasses.putIfAbsent(clusterMember.type, SimulateClassInitializerResult.NOT_SIMULATED_INITIALIZED);
                clusterMember.status = SimulateClassInitializerStatus.PUBLISHED_AS_NOT_INITIALIZED;
            }
            if (existingResult != null && simulatedInitialized != existingResult.simulatedInitialized) {
                StringBuilder msg = new StringBuilder("mismatch with existing registration: ").append(clusterMember.type.toJavaName(true))
                                .append(", existingResult: ").append(existingResult.simulatedInitialized)
                                .append(", new: ").append(simulatedInitialized)
                                .append(System.lineSeparator()).append("Cluster members:");
                for (var m : clusterMember.cluster.clusterMembers.getValues()) {
                    msg.append(System.lineSeparator()).append("  ").append(m.type.toJavaName(true)).append(": ").append(m.status)
                                    .append(", ").append(m.staticFieldValues.size())
                                    .append(", ").append(m.notInitializedReasons.isEmpty() ? "(no reasons)" : reasonToString(clusterMember.cluster.providers, m.notInitializedReasons.get(0)));
                }
                throw VMError.shouldNotReachHere(msg.toString());
            }
        }
    }

    private boolean collectTransitiveDependencies(SimulateClassInitializerClusterMember clusterMember, EconomicSet<SimulateClassInitializerClusterMember> transitiveDependencies) {
        if (clusterMember.status == SimulateClassInitializerStatus.COLLECTING_DEPENDENCIES) {
            return true;
        } else if (clusterMember.status.published) {
            transitiveDependencies.add(clusterMember);
            /*
             * No need to follow any transitive dependencies, that was done when the cluster member
             * was published.
             */
        } else {
            assert clusterMember.status == SimulateClassInitializerStatus.INIT_CANDIDATE : clusterMember.type + ": " + clusterMember.status;
            if (transitiveDependencies.add(clusterMember)) {
                for (var dependency : clusterMember.dependencies) {
                    if (collectTransitiveDependencies(dependency, transitiveDependencies)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String reasonToString(HostedProviders providers, Object reason) {
        if (reason instanceof AnalysisType type) {
            return "superclass/interface: " + type.toJavaName(true);
        } else if (reason instanceof EnsureClassInitializedNode node && node.constantTypeOrNull(providers.getConstantReflection()) != null) {
            return "class initializer dependency: " +
                            ((EnsureClassInitializedNode) reason).constantTypeOrNull(providers.getConstantReflection()).toJavaName(true) +
                            " " + node.getNodeSourcePosition();
        } else if (reason instanceof Node node) {
            if (node instanceof BeginNode || node instanceof ExceptionObjectNode || node instanceof MergeNode || node instanceof EndNode) {
                /* Filter out uninteresting nodes to keep the reason printing short. */
                return null;
            } else {
                return "node " + node + " " + node.getNodeSourcePosition();
            }
        } else {
            return String.valueOf(reason);
        }
    }
}
