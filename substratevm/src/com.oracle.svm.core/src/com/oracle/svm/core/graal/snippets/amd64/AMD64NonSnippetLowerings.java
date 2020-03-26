/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets.amd64;

import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.amd64.AMD64ConvertSnippets;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.NonSnippetLowerings;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class AMD64NonSnippetLowerings extends NonSnippetLowerings {

    @SuppressWarnings("unused")
    public static void registerLowerings(RuntimeConfiguration runtimeConfig, Predicate<ResolvedJavaMethod> mustNotAllocatePredicate, OptionValues options, Iterable<DebugHandlersFactory> factories,
                    Providers providers, SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        new AMD64NonSnippetLowerings(runtimeConfig, mustNotAllocatePredicate, options, factories, providers, snippetReflection, lowerings);
    }

    private AMD64NonSnippetLowerings(RuntimeConfiguration runtimeConfig, Predicate<ResolvedJavaMethod> mustNotAllocatePredicate, OptionValues options, Iterable<DebugHandlersFactory> factories,
                    Providers providers, SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(runtimeConfig, mustNotAllocatePredicate, options, factories, providers, snippetReflection, lowerings);

        lowerings.put(FloatConvertNode.class, new FloatConvertLowering(options, factories, providers, snippetReflection, ConfigurationValues.getTarget()));
    }

    private static class FloatConvertLowering implements NodeLoweringProvider<FloatConvertNode> {

        private final AMD64ConvertSnippets.Templates convertSnippets;

        FloatConvertLowering(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            convertSnippets = new AMD64ConvertSnippets.Templates(options, factories, providers, snippetReflection, target);
        }

        @Override
        public void lower(FloatConvertNode node, LoweringTool tool) {
            convertSnippets.lower(node, tool);
        }
    }
}
