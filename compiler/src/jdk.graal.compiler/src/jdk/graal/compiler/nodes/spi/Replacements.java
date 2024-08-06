/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.spi;

import java.util.BitSet;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginInjectionProvider;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Interface for managing replacements.
 */
public interface Replacements extends GeneratedPluginInjectionProvider {

    CoreProviders getProviders();

    /**
     * Gets the object managing the various graph builder plugins used by this object when parsing
     * bytecode into a graph.
     */
    GraphBuilderConfiguration.Plugins getGraphBuilderPlugins();

    /**
     * Gets the plugin type that intrinsifies calls to {@code method}.
     */
    Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method);

    /**
     * Create a {@link DebugContext} for use with {@link Snippet} related work. Snippet processing
     * is hidden by default using the flags {@code DebugStubsAndSnippets}.
     */
    DebugContext openSnippetDebugContext(String idPrefix, ResolvedJavaMethod method, DebugContext outer, OptionValues options);

    /**
     * Gets the snippet graph derived from a given method.
     *
     * @param recursiveEntry XXX always null now?.
     * @param args arguments to the snippet if available, otherwise {@code null}
     * @param nonNullParameters
     * @param trackNodeSourcePosition
     * @param options
     * @return the snippet graph, if any, that is derived from {@code method}
     */
    StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options);

    /**
     * Get the snippet metadata required to inline the snippet.
     */
    SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method);

    /**
     * Return true if the method is a {@link Snippet}.
     */
    boolean isSnippet(ResolvedJavaMethod method);

    /**
     * Registers a method as snippet.
     */
    void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options);

    /**
     * Marks a plugin as conditionally applied. In the contenxt of libgraal conditional plugins
     * can't be used in during graph encoding for snippets and method substitutions and this is used
     * to detect violations of this restriction.
     */
    void registerConditionalPlugin(InvocationPlugin plugin);

    /**
     * Gets a graph that is a substitution for a given method.
     *
     * @param invokeBci the call site BCI for the substitution
     * @param inlineControl
     * @param trackNodeSourcePosition
     * @param replaceePosition
     * @param allowAssumptions
     * @param options
     * @return the graph, if any, that is a substitution for {@code method}
     */
    StructuredGraph getInlineSubstitution(ResolvedJavaMethod method, int invokeBci, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    AllowAssumptions allowAssumptions, OptionValues options);

    /**
     * Determines if there may be a
     * {@linkplain #getInlineSubstitution(ResolvedJavaMethod, int, Invoke.InlineControl, boolean, NodeSourcePosition, AllowAssumptions, OptionValues)
     * substitution graph} for a given method.
     *
     * A call to {@link #getInlineSubstitution} may still return {@code null} for {@code method} and
     * substitution may be based on an {@link InvocationPlugin} that returns {@code false} for
     * {@link InvocationPlugin#execute} making it impossible to create a substitute graph.
     *
     * @return true iff there may be a substitution graph available for {@code method}
     */
    boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options);

    /**
     * Gets the provider for accessing the bytecode of a substitution method if no other provider is
     * associated with the substitution method.
     */
    BytecodeProvider getDefaultReplacementBytecodeProvider();

    /**
     * Register snippet templates.
     */
    void registerSnippetTemplateCache(SnippetTemplateCache snippetTemplates);

    /**
     * Get snippet templates that were registered with
     * {@link Replacements#registerSnippetTemplateCache(SnippetTemplateCache)}.
     */
    <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass);

    /**
     * Notifies this method that no further snippets will be registered via {@link #registerSnippet}
     * or {@link #registerSnippetTemplateCache}.
     *
     * This is a hook for an implementation to check for or forbid late registration.
     */
    default void closeSnippetRegistration() {
    }

    /**
     * Gets the JavaKind corresponding to word values.
     */
    JavaKind getWordKind();
}
