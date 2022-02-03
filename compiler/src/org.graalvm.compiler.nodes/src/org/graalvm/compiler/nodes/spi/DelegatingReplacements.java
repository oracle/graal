/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.api.replacements.SnippetTemplateCache;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.BitSet;

/**
 * A convenience class for overriding just a portion of the Replacements API.
 */
public class DelegatingReplacements implements Replacements {
    protected final Replacements delegate;

    public DelegatingReplacements(Replacements delegate) {
        this.delegate = delegate;
    }

    @Override
    public CoreProviders getProviders() {
        return delegate.getProviders();
    }

    @Override
    public <T> T getInjectedArgument(Class<T> type) {
        return delegate.getInjectedArgument(type);
    }

    @Override
    public Stamp getInjectedStamp(Class<?> type, boolean nonNull) {
        return delegate.getInjectedStamp(type, nonNull);
    }

    @Override
    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return delegate.getGraphBuilderPlugins();
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        return delegate.getIntrinsifyingPlugin(method);
    }

    @Override
    public DebugContext openSnippetDebugContext(DebugContext.Description description, DebugContext outer, OptionValues options) {
        return delegate.openSnippetDebugContext(description, outer, options);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        return delegate.getSnippet(method, recursiveEntry, args, nonNullParameters, trackNodeSourcePosition, replaceePosition, options);
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        return delegate.getSnippetParameterInfo(method);
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        return delegate.isSnippet(method);
    }

    @Override
    public boolean isEncodingSnippets() {
        return delegate.isEncodingSnippets();
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        delegate.registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
    }

    @Override
    public StructuredGraph getMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context,
                    AllowAssumptions allowAssumptions, Cancellable cancellable, OptionValues options) {
        return delegate.getMethodSubstitution(plugin, original, context, allowAssumptions, cancellable, options);
    }

    @Override
    public void registerMethodSubstitution(MethodSubstitutionPlugin plugin) {
        delegate.registerMethodSubstitution(plugin);
    }

    @Override
    public void registerConditionalPlugin(InvocationPlugin plugin) {
        delegate.registerConditionalPlugin(plugin);
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod method, int invokeBci, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    AllowAssumptions allowAssumptions,
                    OptionValues options) {
        return delegate.getInlineSubstitution(method, invokeBci, inlineControl, trackNodeSourcePosition, replaceePosition, allowAssumptions, options);
    }

    @Override
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug, AllowAssumptions allowAssumptions, Cancellable cancellable) {
        return delegate.getIntrinsicGraph(method, compilationId, debug, allowAssumptions, cancellable);
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method) {
        return delegate.hasSubstitution(method);
    }

    @Override
    public BytecodeProvider getDefaultReplacementBytecodeProvider() {
        return delegate.getDefaultReplacementBytecodeProvider();
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache snippetTemplates) {
        delegate.registerSnippetTemplateCache(snippetTemplates);
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        return delegate.getSnippetTemplateCache(templatesClass);
    }

    @Override
    public JavaKind getWordKind() {
        return delegate.getWordKind();
    }
}
