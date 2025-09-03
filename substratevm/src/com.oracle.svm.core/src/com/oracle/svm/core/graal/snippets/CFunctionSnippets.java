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
import java.util.function.Supplier;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.VerificationMarkerNode;
import com.oracle.svm.core.graal.stackvalue.LoweredStackValueNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueDataNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.nodes.CPrologueData;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.ThreadStatusTransition;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.LateLoweredNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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

    private final SnippetInfo prologue;
    private final SnippetInfo epilogue;

    /**
     * A unique object that identifies the frame anchor stack value. Multiple C function calls
     * inlined into the same Java method share the stack slots for the frame anchor.
     */
    private static final StackSlotIdentity frameAnchorIdentity = new StackSlotIdentity("CFunctionSnippets.frameAnchorIdentifier", true);

    @Snippet
    private static CPrologueData prologueSnippet(@ConstantParameter int newThreadStatus) {
        /* Push a JavaFrameAnchor to the thread-local linked list. */
        JavaFrameAnchor anchor = (JavaFrameAnchor) LoweredStackValueNode.loweredStackValue(SizeOf.get(JavaFrameAnchor.class), FrameAccess.wordSize(), frameAnchorIdentity);
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
        if (oldThreadStatus == StatusSupport.STATUS_IN_NATIVE) {
            ThreadStatusTransition.fromNativeToJava(true);
        } else if (oldThreadStatus == StatusSupport.STATUS_IN_VM) {
            ThreadStatusTransition.fromVMToJava(true);
        } else {
            ReplacementsUtil.staticAssert(false, "Unexpected thread status");
        }

        /*
         * Ensure that no floating reads are scheduled before we are done with the transition. All
         * memory dependencies of the replaced CEntryPointEpilogueNode are re-wired to this
         * KillMemoryNode since this is the last kill-all node of the snippet.
         */
        MembarNode.memoryBarrier(MembarNode.FenceKind.NONE, LocationIdentity.ANY_LOCATION);
    }

    CFunctionSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.prologue = snippet(providers, CFunctionSnippets.class, "prologueSnippet", Snippet.SnippetType.TRANSPLANTED_SNIPPET);
        this.epilogue = snippet(providers, CFunctionSnippets.class, "epilogueSnippet", Snippet.SnippetType.TRANSPLANTED_SNIPPET);

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

            ResolvedJavaMethod target = prologue.getMethod();
            Stamp returnStamp = StampFactory.forKind(target.getSignature().getReturnKind());
            StructuredGraph graph = node.graph();
            final Supplier<SnippetTemplate> templateSupplier = new Supplier<>() {
                @Override
                public SnippetTemplate get() {
                    int newThreadStatus = node.getNewThreadStatus();
                    assert StatusSupport.isValidStatus(newThreadStatus);
                    Arguments args = new Arguments(prologue, node.graph(), tool.getLoweringStage());
                    args.add("newThreadStatus", newThreadStatus);
                    SnippetTemplate template = template(tool, node, args);
                    return template;
                }
            };
            LateLoweredNode lateInvoke = graph.add(new LateLoweredNode(target, returnStamp, new ValueNode[]{ConstantNode.forInt(node.getNewThreadStatus(), graph)}, templateSupplier));
            lateInvoke.setStateBefore(node.stateBefore());
            lateInvoke.setStateAfter(node.stateAfter());
            node.graph().replaceFixedWithFixed(node, lateInvoke);
            // we do not want the backend to spill or rematerialize any constants in the basic
            // blocks inlined via these snippets
            lateInvoke.setAfterInlineeBasicBlockAction(x -> x.setCanUseBlockAsSpillTarget(false));
        }
    }

    class CFunctionEpilogueLowering implements NodeLoweringProvider<CFunctionEpilogueNode> {
        @Override
        public void lower(CFunctionEpilogueNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return;
            }
            node.graph().addAfterFixed(node, node.graph().add(new VerificationMarkerNode(node.getMarker())));
            ResolvedJavaMethod target = prologue.getMethod();
            Stamp returnStamp = StampFactory.forKind(target.getSignature().getReturnKind());
            StructuredGraph graph = node.graph();
            Supplier<SnippetTemplate> templateSupplier = new Supplier<>() {
                @Override
                public SnippetTemplate get() {
                    int oldThreadStatus = node.getOldThreadStatus();
                    assert StatusSupport.isValidStatus(oldThreadStatus);
                    Arguments args = new Arguments(epilogue, node.graph(), tool.getLoweringStage());
                    args.add("oldThreadStatus", oldThreadStatus);
                    SnippetTemplate template = template(tool, node, args);
                    return template;
                }
            };
            LateLoweredNode lateInvoke = graph.add(new LateLoweredNode(target, returnStamp, new ValueNode[]{ConstantNode.forInt(node.getOldThreadStatus(), graph)}, templateSupplier));
            lateInvoke.setStateBefore(node.stateBefore());
            lateInvoke.setStateAfter(node.stateAfter());
            node.graph().replaceFixedWithFixed(node, lateInvoke);
            // we do not want the backend to spill or rematerialize any constants in the basic
            // blocks inlined via these snippets
            lateInvoke.setAfterInlineeBasicBlockAction(x -> x.setCanUseBlockAsSpillTarget(false));
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
}

/**
 * {@link CFunctionSnippets} may only be used for code that cannot be deoptimized. Otherwise,
 * deoptimization could destroy stack allocated {@link JavaFrameAnchor} structs when rewriting the
 * stack.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
class CFunctionSnippetsFeature implements InternalFeature {
    @Override
    @SuppressWarnings("unused")
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        if (hosted) {
            new CFunctionSnippets(options, providers, lowerings);
        }
    }
}
