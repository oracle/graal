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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.graal.nodes.UnreachableNode.unreachable;
import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.runtimeCall;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;
import static com.oracle.svm.core.snippets.SnippetRuntime.UNWIND_EXCEPTION;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.java.LoadExceptionObjectNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

public final class ExceptionSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static void unwindSnippet(Throwable exception) {
        Pointer callerSP = readCallerStackPointer();
        CodePointer callerIP = readReturnAddress();
        runtimeCall(UNWIND_EXCEPTION, exception, callerSP, callerIP);
        throw unreachable();
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new ExceptionSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private ExceptionSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        lowerings.put(UnwindNode.class, new UnwindLowering());
        lowerings.put(LoadExceptionObjectNode.class, new LoadExceptionObjectLowering());
    }

    protected class UnwindLowering implements NodeLoweringProvider<UnwindNode> {

        private final SnippetInfo unwind = snippet(ExceptionSnippets.class, "unwindSnippet");

        @Override
        public void lower(UnwindNode node, LoweringTool tool) {
            Arguments args = new Arguments(unwind, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("exception", node.exception());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class LoadExceptionObjectLowering implements NodeLoweringProvider<LoadExceptionObjectNode> {

        @Override
        public void lower(LoadExceptionObjectNode node, LoweringTool tool) {
            FrameState exceptionState = node.stateAfter();
            assert exceptionState != null;

            StructuredGraph graph = node.graph();
            FixedWithNextNode readRegNode = graph.add(new ReadReturnRegisterNode(StampFactory.objectNonNull()));
            graph.replaceFixedWithFixed(node, readRegNode);

            graph.addAfterFixed(readRegNode, graph.add(new ExceptionStateNode(exceptionState)));
        }
    }
}

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
final class ReadReturnRegisterNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<ReadReturnRegisterNode> TYPE = NodeClass.create(ReadReturnRegisterNode.class);

    /*
     * Make every node unique to prevent de-duplication. The node reads a fixed register, so it
     * needs to remain the first node immediately after the InvokeWithExceptionNode.
     */
    @SuppressWarnings("unused")//
    private final long uniqueId;
    private static final AtomicLong nextUniqueId = new AtomicLong();

    protected ReadReturnRegisterNode(Stamp stamp) {
        super(TYPE, stamp);
        uniqueId = nextUniqueId.getAndIncrement();
    }

    protected ReadReturnRegisterNode(JavaKind kind) {
        this(StampFactory.forKind(kind));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lirGenTool = gen.getLIRGeneratorTool();
        Value returnRegister = lirGenTool.getRegisterConfig().getReturnRegister(getStackKind()).asValue(
                        LIRKind.fromJavaKind(lirGenTool.target().arch, getStackKind()));
        lirGenTool.emitIncomingValues(new Value[]{returnRegister});
        gen.setResult(this, lirGenTool.emitMove(returnRegister));
    }
}

@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
final class ExceptionStateNode extends AbstractStateSplit implements Canonicalizable {
    public static final NodeClass<ExceptionStateNode> TYPE = NodeClass.create(ExceptionStateNode.class);

    protected ExceptionStateNode(FrameState stateAfter) {
        super(TYPE, StampFactory.forVoid(), stateAfter);
        assert stateAfter != null;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (stateAfter == null) {
            /* After the FrameStateAssignmentPhase, the node is unnecessary. */
            return null;
        }
        return this;
    }
}
