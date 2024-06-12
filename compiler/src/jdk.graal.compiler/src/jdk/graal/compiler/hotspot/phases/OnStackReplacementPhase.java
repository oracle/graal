/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.phases;

import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import java.util.BitSet;
import java.util.Optional;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.hotspot.HotSpotGraalServices;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EntryMarkerNode;
import jdk.graal.compiler.nodes.EntryProxyNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.OSRLocalNode;
import jdk.graal.compiler.nodes.extended.OSRLockNode;
import jdk.graal.compiler.nodes.extended.OSRMonitorEnterNode;
import jdk.graal.compiler.nodes.extended.OSRStartNode;
import jdk.graal.compiler.nodes.java.AccessMonitorNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import jdk.vm.ci.runtime.JVMCICompiler;

public class OnStackReplacementPhase extends BasePhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "Deoptimize OSR compiled code when the OSR entry loop is finished " +
                       "if there is no mature profile available for the rest of the method.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DeoptAfterOSR = new OptionKey<>(true);
        @Option(help = "Support OSR compilations with locks. If DeoptAfterOSR is true we can per definition not have " +
                       "unbalanced enter/exits mappings. If DeoptAfterOSR is false insert artificial monitor enters after " +
                       "the OSRStart to have balanced enter/exits in the graph.", type = OptionType.Debug)
        public static final OptionKey<Boolean> SupportOSRWithLocks = new OptionKey<>(true);
        // @formatter:on
    }

    private static final CounterKey OsrWithLocksCount = DebugContext.counter("OSRWithLocks");

    private static boolean supportOSRWithLocks(OptionValues options) {
        return Options.SupportOSRWithLocks.getValue(options);
    }

    private static final SpeculationReasonGroup OSR_LOCAL_SPECULATIONS = new SpeculationReasonGroup("OSRLocal", int.class, Stamp.class, int.class);

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders providers) {
        DebugContext debug = graph.getDebug();
        if (graph.getEntryBCI() == JVMCICompiler.INVOCATION_ENTRY_BCI) {
            // This happens during inlining in a OSR method, because the same phase plan will be
            // used.
            assert graph.getNodes(EntryMarkerNode.TYPE).isEmpty();
            return;
        }
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "OnStackReplacement initial at bci %d", graph.getEntryBCI());

        EntryMarkerNode osr;
        int maxIterations = -1;
        int iterations = 0;

        final EntryMarkerNode originalOSRNode = getEntryMarker(graph);
        final LoopBeginNode originalOSRLoop = osrLoop(originalOSRNode, providers);
        final boolean currentOSRWithLocks = osrWithLocks(originalOSRNode);

        if (originalOSRLoop == null) {
            /*
             * OSR with Locks: We do not have an OSR loop for the original OSR bci. Therefore we
             * cannot decide where to deopt and which framestate will be used. In the worst case the
             * framestate of the OSR entry would be used.
             */
            throw new PermanentBailoutException("OSR compilation without OSR entry loop.");
        }

        if (!supportOSRWithLocks(graph.getOptions()) && currentOSRWithLocks) {
            throw new PermanentBailoutException("OSR with locks disabled.");
        }

        do {
            osr = getEntryMarker(graph);
            LoopsData loops = providers.getLoopsDataProvider().getLoopsData(graph);
            // Find the loop that contains the EntryMarker
            CFGLoop<HIRBlock> l = loops.getCFG().getNodeToBlock().get(osr).getLoop();
            if (l == null) {
                break;
            }

            iterations++;
            if (maxIterations == -1) {
                maxIterations = l.getDepth();
            } else if (iterations > maxIterations) {
                throw GraalError.shouldNotReachHere(iterations + " " + maxIterations); // ExcludeFromJacocoGeneratedReport
            }

            l = l.getOutmostLoop();

            Loop loop = loops.loop(l);
            loop.loopBegin().markOsrLoop();
            LoopTransformations.peel(loop);

            osr.prepareDelete();
            GraphUtil.removeFixedWithUnusedInputs(osr);
            debug.dump(DebugContext.DETAILED_LEVEL, graph, "OnStackReplacement loop peeling result");
        } while (true); // TERMINATION ARGUMENT: bounded by max iterations

        StartNode start = graph.start();
        FrameState osrState = osr.stateAfter();
        OSRStartNode osrStart;
        try (DebugCloseable context = osr.withNodeSourcePosition()) {
            osr.setStateAfter(null);
            osrStart = graph.add(new OSRStartNode());
            FixedNode next = osr.next();
            osr.setNext(null);
            osrStart.setNext(next);
            graph.setStart(osrStart);
            osrStart.setStateAfter(osrState);

            debug.dump(DebugContext.DETAILED_LEVEL, graph, "OnStackReplacement after setting OSR start");
            final int localsSize = osrState.localsSize();
            final int locksSize = osrState.locksSize();

            ResolvedJavaMethod osrStateMethod = osrState.getMethod();
            GraalError.guarantee(localsSize == osrStateMethod.getMaxLocals(), "%s@%d: locals size %d != %d", osrStateMethod, osrState.bci, localsSize, osrStateMethod.getMaxLocals());
            BitSet oopMap = getOopMapAt(osrStateMethod, osrState.bci);

            for (int i = 0; i < localsSize + locksSize; i++) {
                ValueNode value;
                if (i >= localsSize) {
                    value = osrState.lockAt(i - localsSize);
                } else {
                    value = osrState.localAt(i);
                }
                if (value instanceof EntryProxyNode) {
                    EntryProxyNode proxy = (EntryProxyNode) value;
                    /*
                     * We need to drop the stamp since the types we see during OSR may be too
                     * precise (if a branch was not parsed for example). In cases when this is
                     * possible, we insert a guard and narrow the OSRLocal stamp at its usages.
                     * Right after graph building the stamps of phis can be too imprecise, so make
                     * sure to infer a more precise one if possible.
                     */
                    proxy.value().inferStamp();
                    Stamp narrowedStamp = proxy.value().stamp(NodeView.DEFAULT);
                    Stamp unrestrictedStamp = proxy.stamp(NodeView.DEFAULT).unrestricted();
                    ValueNode osrLocal;
                    if (i >= localsSize) {
                        osrLocal = graph.addOrUnique(new OSRLockNode(i - localsSize, unrestrictedStamp));
                    } else {
                        osrLocal = initLocal(graph, unrestrictedStamp, oopMap, i);
                    }

                    // Speculate on the OSRLocal stamps that could be more precise.
                    SpeculationReason reason = OSR_LOCAL_SPECULATIONS.createSpeculationReason(osrState.bci, narrowedStamp, i);
                    if (graph.getSpeculationLog().maySpeculate(reason) && osrLocal instanceof OSRLocalNode && value.getStackKind().equals(JavaKind.Object) &&
                                    !narrowedStamp.isUnrestricted()) {
                        osrLocal = narrowOsrLocal(graph, narrowedStamp, osrLocal, reason, osrStart, proxy, osrState);
                    }
                    proxy.replaceAndDelete(osrLocal);

                } else {
                    assert value == null || value instanceof OSRLocalNode : Assertions.errorMessage(value);
                }
            }

            osr.replaceAtUsages(osrStart, InputType.Guard);
            osr.replaceAtUsages(osrStart, InputType.Anchor);
        }
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "OnStackReplacement after replacing entry proxies");
        GraphUtil.killCFG(start);
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "OnStackReplacement result");
        new DeadCodeEliminationPhase(Required).apply(graph);

        if (currentOSRWithLocks) {
            OsrWithLocksCount.increment(debug);
            try (DebugCloseable context = osrStart.withNodeSourcePosition()) {
                for (int i = osrState.monitorIdCount() - 1; i >= 0; --i) {
                    MonitorIdNode id = osrState.monitorIdAt(i);
                    ValueNode lockedObject = osrState.lockAt(i);
                    OSRMonitorEnterNode osrMonitorEnter = graph.add(new OSRMonitorEnterNode(lockedObject, id));
                    osrMonitorEnter.setStateAfter(osrStart.stateAfter());
                    for (Node usage : id.usages()) {
                        if (usage instanceof AccessMonitorNode) {
                            AccessMonitorNode access = (AccessMonitorNode) usage;
                            access.setObject(lockedObject);
                        }
                    }
                    FixedNode oldNext = osrStart.next();
                    oldNext.replaceAtPredecessor(null);
                    osrMonitorEnter.setNext(oldNext);
                    osrStart.setNext(osrMonitorEnter);
                }
            }

            debug.dump(DebugContext.DETAILED_LEVEL, graph, "After inserting OSR monitor enters");
            /*
             * Ensure balanced monitorenter - monitorexit
             *
             * Ensure that there is no monitor exit without a monitor enter in the graph. If there
             * is one this can only be done by bytecode as we have the monitor enter before the OSR
             * loop but the exit in a path of the loop that must be under a condition, else it will
             * throw an IllegalStateException anyway in the 2.iteration
             */
            for (MonitorExitNode exit : graph.getNodes(MonitorExitNode.TYPE)) {
                MonitorIdNode id = exit.getMonitorId();
                if (id.usages().filter(MonitorEnterNode.class).count() != 1) {
                    throw new PermanentBailoutException("Unbalanced monitor enter-exit in OSR compilation with locks. Object is locked before the loop but released inside the loop.");
                }
            }
        }
        debug.dump(DebugContext.DETAILED_LEVEL, graph, "OnStackReplacement result");
        new DeadCodeEliminationPhase(Required).apply(graph);
        /*
         * There must not be any parameter nodes left after OSR compilation.
         */
        assert graph.getNodes(ParameterNode.TYPE).count() == 0 : "OSR Compilation contains references to parameters.";
    }

    /**
     * Generates a speculative type check on {@code osrLocal} for {@code narrowedStamp}.
     *
     * @return a {@link PiNode} that narrows the type of {@code osrLocal} to {@code narrowedStamp}
     */
    private static ValueNode narrowOsrLocal(StructuredGraph graph, Stamp narrowedStamp, ValueNode osrLocal, SpeculationReason reason,
                    OSRStartNode osrStart, EntryProxyNode proxy, FrameState osrState) {
        // Add guard.
        LogicNode check = graph.addOrUniqueWithInputs(InstanceOfNode.createHelper((ObjectStamp) narrowedStamp, osrLocal, null, null));
        SpeculationLog.Speculation constant = graph.getSpeculationLog().speculate(reason);
        FixedGuardNode guard = graph.add(new FixedGuardNode(check, DeoptimizationReason.OptimizedTypeCheckViolated, DeoptimizationAction.InvalidateRecompile, constant, false));
        graph.addAfterFixed(osrStart, guard);

        // Replace with a more specific type at usages.
        // We know that we are at the root,
        // so we need to replace the proxy in the state.
        proxy.replaceAtMatchingUsages(osrLocal, n -> n == osrState);
        return graph.addOrUnique(new PiNode(osrLocal, narrowedStamp, guard));
    }

    /**
     * Initializes local variable {@code i} at an OSR entry point.
     *
     * @param oopMap oop map for the method at the OSR entry point (see
     *            {@link #getOopMapAt(ResolvedJavaMethod, int)}
     * @param i index of a local variable
     * @return value representing initial value of the local variable
     */
    private static ValueNode initLocal(StructuredGraph graph, Stamp unrestrictedStamp, BitSet oopMap, int i) {
        if (unrestrictedStamp.isObjectStamp() && (oopMap != null && !oopMap.get(i))) {
            // @formatter:off
            // The OSR entry FrameState says that this value is "available" here.
            // That is, all *parsed* control flow paths to the frame state had a
            // definition of the value. However, the interpreter oop map shows
            // the value is not available here based on *all* control flow paths.
            // See GraalOSRTest.testOopMap() for an example where Graal
            // does not parse a non-taken exception handler path.
            // We need to use the interpreter's view in this case since it's
            // guaranteed to be complete, and so we treat the value as null.
            //
            // The interpreter view also helps preserve object values for the
            // lifetime expected by a debugger. For example:
            //
            // 1: int foo(int i1, Object o2) {
            // 2:     int h = o2.hashCode();
            // 3:     h *= i1;
            // 4:     bar(h);
            // 5:     return h;
            // 6: }
            //
            // The availability of o2 according to the interpreter is from line 1
            // to line 5 (i.e. o2's source file scope). Without the oop map, we would use
            // compiler liveness for o2's availability which would only be
            // from line 1 to line 2 since it's not read after line 2.
            // @formatter:on
            return ConstantNode.defaultForKind(JavaKind.Object, graph);
        }
        return graph.addOrUnique(new OSRLocalNode(i, unrestrictedStamp));
    }

    /**
     * Gets the oop map covering the local variables of {@code method} at {@code bci}.
     *
     * @return a bit set with a bit set for each local variable that contains a live oop at
     *         {@code bci}
     */
    private static BitSet getOopMapAt(ResolvedJavaMethod method, int bci) {
        if (!HotSpotGraalServices.hasGetOopMapAt()) {
            return null;
        } else {
            return HotSpotGraalServices.getOopMapAt(method, bci);
        }
    }

    private static EntryMarkerNode getEntryMarker(StructuredGraph graph) {
        NodeIterable<EntryMarkerNode> osrNodes = graph.getNodes(EntryMarkerNode.TYPE);
        EntryMarkerNode osr = osrNodes.first();
        if (osr == null) {
            throw new GraalError("No OnStackReplacementNode generated");
        }
        if (osrNodes.count() > 1) {
            throw new GraalError("Multiple OnStackReplacementNodes generated");
        }
        if (osr.stateAfter().stackSize() != 0) {
            throw new PermanentBailoutException("OSR with stack entries not supported: %s", osr.stateAfter().toString(Verbosity.Debugger));
        }
        return osr;
    }

    private static LoopBeginNode osrLoop(EntryMarkerNode osr, CoreProviders providers) {
        // Check that there is an OSR loop for the OSR begin
        LoopsData loops = providers.getLoopsDataProvider().getLoopsData(osr.graph());
        CFGLoop<HIRBlock> l = loops.getCFG().getNodeToBlock().get(osr).getLoop();
        if (l == null) {
            return null;
        }
        return (LoopBeginNode) l.getHeader().getBeginNode();
    }

    private static boolean osrWithLocks(EntryMarkerNode osr) {
        return osr.stateAfter().locksSize() != 0;
    }

    @Override
    public float codeSizeIncrease() {
        return 5.0f;
    }
}
