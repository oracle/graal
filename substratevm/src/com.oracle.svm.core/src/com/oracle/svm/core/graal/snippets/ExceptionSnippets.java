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

import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.runtimeCall;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static jdk.graal.compiler.nodes.UnreachableNode.unreachable;

import java.util.Map;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.ReadExceptionObjectNode;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.ExceptionUnwind;

public final class ExceptionSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    @NeverInline("All methods accessing caller frame must have this annotation. " +
                    "The requirement would not be necessary for a snippet, but the annotation does not matter on the snippet root method, " +
                    "so having the annotation is easier than coding an exception to the annotation checker.")
    protected static void unwindSnippet(Throwable exception, @ConstantParameter boolean fromMethodWithCalleeSavedRegisters) {
        Pointer callerSP = readCallerStackPointer();
        if (fromMethodWithCalleeSavedRegisters) {
            runtimeCall(ExceptionUnwind.UNWIND_EXCEPTION_WITH_CALLEE_SAVED_REGISTERS, exception, callerSP);
        } else {
            runtimeCall(ExceptionUnwind.UNWIND_EXCEPTION_WITHOUT_CALLEE_SAVED_REGISTERS, exception, callerSP);
        }
        throw unreachable();
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new ExceptionSnippets(options, providers, lowerings);
    }

    private final SnippetInfo unwind;

    private ExceptionSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.unwind = snippet(providers, ExceptionSnippets.class, "unwindSnippet");
        lowerings.put(UnwindNode.class, new UnwindLowering());
    }

    protected class UnwindLowering implements NodeLoweringProvider<UnwindNode> {

        @Override
        public void lower(UnwindNode node, LoweringTool tool) {
            if (node.graph().isSubstitution()) {
                /*
                 * Unwind nodes in substitution graph will never survive. They are used as markers,
                 * though, so we should not replace them.
                 */
                return;
            }
            Arguments args = new Arguments(unwind, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("exception", node.exception());
            args.addConst("fromMethodWithCalleeSavedRegisters", ((SharedMethod) node.graph().method()).hasCalleeSavedRegisters());
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    public static class LoadExceptionObjectLowering implements NodeLoweringProvider<LoadExceptionObjectNode> {

        @Override
        public void lower(LoadExceptionObjectNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            FixedWithNextNode readRegNode = graph.add(new ReadExceptionObjectNode(StampFactory.objectNonNull()));
            graph.replaceFixedWithFixed(node, readRegNode);
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class ExceptionFeature implements InternalFeature {

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        ExceptionSnippets.registerLowerings(options, providers, lowerings);
    }
}
