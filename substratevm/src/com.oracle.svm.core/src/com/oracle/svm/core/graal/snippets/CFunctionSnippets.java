/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.KillMemoryNode;
import com.oracle.svm.core.graal.nodes.VerificationMarkerNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueDataNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.nodes.CPrologueData;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

/**
 * Snippets for calling from Java to C. This is the inverse of {@link CEntryPointSnippets}.
 *
 * The {@link JavaFrameAnchor} has to be set up because the top of the stack will no longer be a
 * Java frame. In addition, the thread state needs to transition from being in
 * {@link StatusSupport#STATUS_IN_JAVA Java} state to being in {@link StatusSupport#STATUS_IN_NATIVE
 * Native} state on the way in, and to transition the thread state from Native state to Java state
 * on the way out.
 *
 * Among the complications is that the C function may try to return while a safepoint is in
 * progress, i.e., the thread state is not Native but {@link StatusSupport#STATUS_IN_SAFEPOINT
 * Safepoint}. It must not be allowed back into Java code until the safepoint is finished.
 *
 * Only parts of these semantics can be implemented via snippets: The low-level code to initialize
 * the {@link JavaFrameAnchor} and to transition the thread from Java state to Native state must
 * only be done immediately before the call, because an accurate pointer map is necessary for the
 * last instruction pointer stored in the {@link JavaFrameAnchor}. Therefore, the
 * {@link JavaFrameAnchor} is filled at the lowest possible level: during code generation as part of
 * the same LIR operation that emits the call to the C function. Using the same LIR instruction is
 * the only way to ensure that neither the instruction scheduler nor the register allocator emit any
 * instructions between the capture of the instruction pointer and the actual call instruction.
 */
public final class CFunctionSnippets extends SubstrateTemplates implements Snippets {

    private final SnippetInfo prologue = snippet(CFunctionSnippets.class, "prologueSnippet");
    private final SnippetInfo epilogue = snippet(CFunctionSnippets.class, "epilogueSnippet");

    /**
     * A unique object that identifies the frame anchor stack value. Multiple C function calls
     * inlined into the same Java method share the stack slots for the frame anchor.
     */
    private static final StackSlotIdentity frameAnchorIdentity = new StackSlotIdentity("CFunctionSnippets.frameAnchorIdentifier");

    @Snippet
    private static CPrologueData prologueSnippet(@ConstantParameter int newThreadStatus) {
        /* Push a JavaFrameAnchor to the thread-local linked list. */
        JavaFrameAnchor anchor = (JavaFrameAnchor) StackValueNode.stackValue(1, SizeOf.get(JavaFrameAnchor.class), frameAnchorIdentity);
        JavaFrameAnchors.pushFrameAnchor(anchor);

        /*
         * The content of the new anchor is uninitialized at this point. It is filled as late as
         * possible, immediately before the C call instruction, so that the pointer map for the last
         * instruction pointer matches the pointer map of the C call. The thread state transition
         * into Native state also happens immediately before the C call.
         */

        return CFunctionPrologueDataNode.cFunctionPrologueData(anchor, newThreadStatus);
    }

    @Snippet
    private static void epilogueSnippet(@ConstantParameter int oldThreadStatus) {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            if (oldThreadStatus == StatusSupport.STATUS_IN_NATIVE) {
                /*
                 * Change the VMThread status from native to Java, blocking if necessary. At this
                 * point the JavaFrameAnchor still needs to be pushed: a concurrently running
                 * safepoint code can start a stack traversal at any time.
                 */
                Safepoint.transitionNativeToJava();
            } else if (oldThreadStatus == StatusSupport.STATUS_IN_VM) {
                Safepoint.transitionVMToJava();
            } else {
                ReplacementsUtil.staticAssert(false, "Unexpected thread status");
            }
        }

        /* The thread is now back in the Java state, it is safe to pop the JavaFrameAnchor. */
        JavaFrameAnchors.popFrameAnchor();

        /*
         * Ensure that no floating reads are scheduled before we are done with the transition. All
         * memory dependencies of the replaced CEntryPointEpilogueNode are re-wired to this
         * KillMemoryNode since this is the last kill-all node of the snippet.
         */
        KillMemoryNode.killMemory(LocationIdentity.ANY_LOCATION);
    }

    private CFunctionSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        lowerings.put(CFunctionPrologueNode.class, new CFunctionPrologueLowering());
        lowerings.put(CFunctionEpilogueNode.class, new CFunctionEpilogueLowering());
    }

    class CFunctionPrologueLowering implements NodeLoweringProvider<CFunctionPrologueNode> {

        @Override
        public void lower(CFunctionPrologueNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }
            matchCallStructure(node);

            /*
             * Mark the begin (and in the epilogueSnippet the end) of the C function transition.
             * Before code generation, we need to verify that the pointer maps of all call
             * instructions (the actual C function call and the slow-path call for the
             * Native-to-Java transition have the same pointer map.
             */
            node.graph().addBeforeFixed(node, node.graph().add(new VerificationMarkerNode(node.getMarker())));

            int newThreadStatus = node.getNewThreadStatus();
            assert StatusSupport.isValidStatus(newThreadStatus);

            Arguments args = new Arguments(prologue, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.addConst("newThreadStatus", newThreadStatus);
            SnippetTemplate template = template(node, args);
            template.setMayRemoveLocation(true);
            template.instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    class CFunctionEpilogueLowering implements NodeLoweringProvider<CFunctionEpilogueNode> {

        @Override
        public void lower(CFunctionEpilogueNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }
            node.graph().addAfterFixed(node, node.graph().add(new VerificationMarkerNode(node.getMarker())));

            int oldThreadStatus = node.getOldThreadStatus();
            assert StatusSupport.isValidStatus(oldThreadStatus);

            Arguments args = new Arguments(epilogue, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.addConst("oldThreadStatus", oldThreadStatus);
            SnippetTemplate template = template(node, args);
            template.setMayRemoveLocation(true);
            template.instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    /**
     * Verify the correct structure of C function calls: A {@link CFunctionPrologueNode}, a
     * {@link InvokeNode}, and a {@link CFunctionEpilogueNode} must be in the same block.
     *
     * For later verification purposes, we match the unique marker objects of the prologue/epilogue
     * sequence.
     */
    private static void matchCallStructure(CFunctionPrologueNode prologueNode) {
        FixedNode cur = prologueNode;
        FixedNode singleInvoke = null;
        List<Node> seenNodes = new ArrayList<>();
        while (true) {
            seenNodes.add(cur);
            if (cur instanceof Invoke) {
                if (singleInvoke != null) {
                    throw VMError.shouldNotReachHere("Found more than one invoke: " + seenNodes);
                } else if (cur instanceof InvokeWithExceptionNode) {
                    throw VMError.shouldNotReachHere("Found InvokeWithExceptionNode: " + cur + " in " + seenNodes);
                }
                InvokeNode invoke = (InvokeNode) cur;

                /*
                 * We are re-using the classInit field of the InvokeNode to store the
                 * CFunctionPrologueNode. During lowering, we create a PrologueDataNode that holds
                 * all the prologue-related data that the invoke needs in the backend.
                 *
                 * The classInit field is in every InvokeNode, and it is otherwise unused by
                 * Substrate VM (it is used only by the Java HotSpot VM). If we ever need the
                 * classInit field for other purposes, we need to create a new subclass of
                 * InvokeNode, and replace the invoke here with an instance of that new subclass.
                 */
                VMError.guarantee(invoke.classInit() == null, "Re-using the classInit field to store the JavaFrameAnchor");
                invoke.setClassInit(prologueNode);

                singleInvoke = cur;
            }

            if (cur instanceof CFunctionEpilogueNode) {
                /* Success: found a matching epilogue. */
                prologueNode.getMarker().setEpilogueMarker(((CFunctionEpilogueNode) cur).getMarker());
                return;
            }

            if (!(cur instanceof FixedWithNextNode)) {
                throw VMError.shouldNotReachHere("Did not find a matching CFunctionEpilogueNode in same block: " + seenNodes);
            }
            cur = ((FixedWithNextNode) cur).next();
        }
    }

    @AutomaticFeature
    static class CFunctionSnippetsFeature implements GraalFeature {

        @Override
        @SuppressWarnings("unused")
        public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                        SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
            new CFunctionSnippets(options, factories, providers, snippetReflection, lowerings);
        }
    }
}
