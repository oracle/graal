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

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.SizeOf;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.posix.PosixCEntryPointSnippets;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads;

/**
 * Snippets for calling from Java to C. This is the inverse of {@link PosixCEntryPointSnippets}.
 *
 * The Java frame anchor has to be set up because the top of the stack will no longer be a Java
 * frame.
 *
 * In addition I might have to transition the thread state from being in Java code to being in
 * native code on the way in, and to transition the thread state from native code to Java code on
 * the way out. The transition is optional, based on the value of the {@linkplain CFunction}
 * annotation.
 *
 * Among the complications is that the C function may try to return while a safepoint is in
 * progress. It must not be allowed back into Java code until the safepoint is finished.
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
    private static void prologueSnippet() {
        // Push a Java frame anchor.
        JavaFrameAnchor anchor = (JavaFrameAnchor) StackValueNode.stackValue(1, SizeOf.get(JavaFrameAnchor.class), frameAnchorIdentity);
        anchor.setLastJavaSP(KnownIntrinsics.readStackPointer());
        JavaFrameAnchors.pushFrameAnchor(anchor);

        if (SubstrateOptions.MultiThreaded.getValue()) {
            // Change the VMThread status from Java to native.
            VMThreads.StatusSupport.setStatusNative();
        }
    }

    @Snippet
    private static void epilogueSnippet() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            // Change the VMThread status from native to Java, blocking if necessary.
            Safepoint.transitionNativeToJava();
        }
        // Pop the Java frame anchor.
        JavaFrameAnchors.popFrameAnchor();
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
            Arguments args = new Arguments(prologue, node.graph().getGuardsStage(), tool.getLoweringStage());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    class CFunctionEpilogueLowering implements NodeLoweringProvider<CFunctionEpilogueNode> {

        @Override
        public void lower(CFunctionEpilogueNode node, LoweringTool tool) {
            Arguments args = new Arguments(epilogue, node.graph().getGuardsStage(), tool.getLoweringStage());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
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
