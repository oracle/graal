/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase.SubstrateBytecodeParser;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HostedGraphBuilderPhase extends SubstrateGraphBuilderPhase {

    public HostedGraphBuilderPhase(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext,
                    WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new HostedBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
    }
}

class HostedBytecodeParser extends SubstrateBytecodeParser {

    private static final DeoptEntryNode STICKY_DEOPT_ENTRY = new DeoptEntryNode();

    private int currentDeoptIndex;
    private Map<Long, DeoptProxyAnchorNode> deoptEntries = new HashMap<>();

    HostedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, true);
    }

    @Override
    public HostedMethod getMethod() {
        return (HostedMethod) super.getMethod();
    }

    @Override
    protected boolean forceLoopPhis() {
        return getMethod().compilationInfo.isDeoptTarget() || super.forceLoopPhis();
    }

    @Override
    protected boolean stampFromValueForForcedPhis() {
        return true;
    }

    @Override
    protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
        super.build(startInstruction, startFrameState);

        /* We never have floating guards in AOT compiled code. */
        getGraph().setGuardsStage(GuardsStage.FIXED_DEOPTS);

        assert !getMethod().isEntryPoint() : "Cannot directly use as entry point, create a call stub";

        if (getMethod().compilationInfo.isDeoptTarget()) {
            /*
             * Remove dangling DeoptProxyNodes which remained after deletion of the corresponding
             * DeoptEntryNodes.
             */
            for (DeoptProxyNode deoptProxy : graph.getNodes(DeoptProxyNode.TYPE)) {
                if (!deoptProxy.hasProxyPoint()) {
                    ValueNode originalValue = deoptProxy.getOriginalNode();
                    deoptProxy.replaceAtUsagesAndDelete(originalValue);
                }
            }
        }
    }

    @Override
    public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
        return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, getMethod().getProfilingInfo(), bci());
    }

    private void insertProxies(FixedNode deoptTarget, FrameStateBuilder state) {

        /*
         * At a deoptimization point we wrap non-constant locals (and java stack elements) with
         * proxy nodes. This is to avoid global value numbering on locals (or derived expressions).
         * The effect is that when a local is accessed after a deoptimization point it is really
         * loaded from its location. This is similar to what happens in the GraphBuilderPhase if
         * entryBCI is set for OSR.
         */
        state.insertProxies(value -> createProxyNode(value, deoptTarget));
        currentDeoptIndex++;
    }

    private ValueNode createProxyNode(ValueNode value, FixedNode deoptTarget) {
        ValueNode v = DeoptProxyNode.create(value, deoptTarget, currentDeoptIndex);
        if (v.graph() != null) {
            return v;
        }
        return graph.addOrUniqueWithInputs(v);
    }

    /**
     * Insert a deopt entry for the graph's start node.
     */
    @Override
    protected void finishPrepare(FixedWithNextNode startInstr, int bci, FrameStateBuilder state) {
        super.finishPrepare(startInstr, bci, state);

        if (getMethod().compilationInfo.isDeoptEntry(bci, false, false)) {
            DeoptEntryNode deoptEntry = append(new DeoptEntryNode());
            deoptEntry.setStateAfter(frameState.create(bci, deoptEntry));
            deoptEntries.put(Long.valueOf(bci), deoptEntry);
            insertProxies(deoptEntry, state);
        }
    }

    @Override
    protected void parseAndInlineCallee(ResolvedJavaMethod targetMethod, ValueNode[] args, IntrinsicContext calleeIntrinsicContext) {
        assert calleeIntrinsicContext != null : "only inlining replacements";
        if (getMethod().compilationInfo.isDeoptEntry(bci(), false, false)) {
            /*
             * Replacements use the frame state before the invoke for all nodes that need a state,
             * i.e., we want to re-execute the whole replacement in case of deoptimization.
             * Therefore, we need to register a DeoptEntryNode before inlining the replacement. The
             * frame state used for the DeoptEntryNode needs to be the same state that will be used
             * later on in the intrinsic.
             */
            FrameState stateAfter = frameState.create(bci(), getNonIntrinsicAncestor(), false, targetMethod.getSignature().toParameterKinds(!targetMethod.isStatic()), args);
            long encodedBci = FrameInfoEncoder.encodeBci(stateAfter.bci, stateAfter.duringCall(), stateAfter.rethrowException());
            if (!deoptEntries.containsKey(encodedBci)) {
                DeoptEntryNode deoptEntry = graph.add(new DeoptEntryNode());
                deoptEntry.setStateAfter(stateAfter);
                insertProxies(deoptEntry, frameState);

                lastInstr.setNext(deoptEntry);
                lastInstr = deoptEntry;

                for (int i = 0; i < args.length; i++) {
                    args[i] = createProxyNode(args[i], deoptEntry);
                }
            }
            /*
             * Ensure that no one registers a later state (after the replacement) with the same
             * frame state.
             */
            deoptEntries.put(encodedBci, STICKY_DEOPT_ENTRY);
        }

        super.parseAndInlineCallee(targetMethod, args, calleeIntrinsicContext);
    }

    /**
     * Insert deopt entries after all state splits.
     */
    @Override
    protected FixedWithNextNode finishInstruction(FixedWithNextNode instr, FrameStateBuilder stateBuilder) {
        if (getMethod().compilationInfo.isDeoptTarget() && !parsingIntrinsic()) {
            FrameState stateAfter = null;
            if (instr instanceof StateSplit && !(instr instanceof DeoptEntryNode)) {
                /*
                 * The regular case: the instruction is a state split and we insert a DeoptEntryNode
                 * right after it.
                 */
                StateSplit stateSplit = (StateSplit) instr;
                stateAfter = stateSplit.stateAfter();
            } else if (instr instanceof AbstractBeginNode) {
                /*
                 * We are at a block begin. If the block predecessor is a LoopExitNode or an
                 * InvokeWithException (both are state splits), we didn't inserted a deopt entry
                 * yet. So we do it at the begin of a block.
                 *
                 * Note that this only happens if the LoopExitNode/InvokeWithException is the
                 * _single_ predcessor of this block. In case of multiple predecessors, the block
                 * starts with a MergeNode and this is handled like a regular case.
                 */
                Node predecessor = instr.predecessor();
                if (predecessor instanceof KillingBeginNode) {
                    /*
                     * This is between an InvokeWithException and the BlockPlaceholderNode.
                     */
                    predecessor = predecessor.predecessor();
                }
                if (predecessor instanceof StateSplit && !(predecessor instanceof DeoptEntryNode)) {
                    stateAfter = ((StateSplit) predecessor).stateAfter();
                }
            }

            boolean needsDeoptEntry = false;
            boolean needsProxies = false;
            if (stateAfter != null) {
                if (getMethod().compilationInfo.isDeoptEntry(stateAfter.bci, stateAfter.duringCall(), stateAfter.rethrowException())) {
                    needsDeoptEntry = true;
                    needsProxies = true;
                } else if (instr.predecessor() instanceof Invoke && getMethod().compilationInfo.isDeoptEntry(((Invoke) instr.predecessor()).bci(), true, false)) {
                    /*
                     * Invoke nodes can be implicit deoptimization entry points. But we cannot
                     * anchor proxy nodes on invocations: The invoke has two successors (normal and
                     * exception handler), and we need to proxy values at the beginning of both.
                     */
                    needsProxies = true;
                } else if (instr instanceof ExceptionObjectNode && getMethod().compilationInfo.isDeoptEntry(((ExceptionObjectNode) instr).stateAfter().bci, true, false)) {
                    /*
                     * The predecessor of the ExceptionObjectNode will be an Invoke, but the Invoke
                     * has not been created yet. So the check above for the predecessor does not
                     * trigger.
                     */
                    needsProxies = true;
                }
            }

            if (needsProxies) {
                long encodedBci = FrameInfoEncoder.encodeBci(stateAfter.bci, stateAfter.duringCall(), stateAfter.rethrowException());
                DeoptProxyAnchorNode existingDeoptEntry = deoptEntries.get(encodedBci);
                if (existingDeoptEntry == null || (existingDeoptEntry != STICKY_DEOPT_ENTRY && instr instanceof AbstractMergeNode)) {
                    if (existingDeoptEntry != null) {
                        /*
                         * Some state splits (i.e. MergeNode and LoopExitNode) do not have a
                         * correspondent byte code. Therefore there can be a previously added deopt
                         * entry with the same BCI. For MergeNodes we replace the previous entry
                         * because the new frame state has less live locals.
                         */
                        existingDeoptEntry.replaceAtUsages(null);
                        graph.removeFixed(existingDeoptEntry);
                        deoptEntries.remove(encodedBci);

                        if (existingDeoptEntry instanceof DeoptEntryNode) {
                            /*
                             * We already had a DeoptEntryNode registered earlier for some reason,
                             * so be conservative and create one again (and not just a
                             * DeoptProxyAnchorNode).
                             */
                            needsDeoptEntry = true;
                        }
                    }
                    assert !deoptEntries.containsKey(encodedBci) : "duplicate deopt entry for encoded BCI " + encodedBci;
                    DeoptProxyAnchorNode deoptEntry = createDeoptEntry(stateBuilder, stateAfter, !needsDeoptEntry);

                    if (instr instanceof LoopBeginNode) {
                        /*
                         * Loop headers to not have their own bci. Never move a deopt entry for the
                         * loop header down, e.g., into a loop end (that might then end up to be
                         * dead code).
                         */
                        deoptEntries.put(encodedBci, STICKY_DEOPT_ENTRY);
                    } else {
                        deoptEntries.put(encodedBci, deoptEntry);
                    }

                    assert instr.next() == null : "cannot append instruction to instruction which isn't end (" + instr + "->" + instr.next() + ")";
                    instr.setNext(deoptEntry);
                    return deoptEntry;
                }
            }
        }
        return super.finishInstruction(instr, stateBuilder);
    }

    private DeoptProxyAnchorNode createDeoptEntry(FrameStateBuilder stateBuilder, FrameState stateAfter, boolean anchorOnly) {
        DeoptProxyAnchorNode deoptEntry = graph.add(anchorOnly ? new DeoptProxyAnchorNode() : new DeoptEntryNode());
        deoptEntry.setStateAfter(stateAfter);

        insertProxies(deoptEntry, stateBuilder);
        return deoptEntry;
    }
}
