/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
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
    public DebugContext openSnippetDebugContext(String idPrefix, ResolvedJavaMethod method, DebugContext outer, OptionValues options) {
        return delegate.openSnippetDebugContext(idPrefix, method, outer, options);
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
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        delegate.registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
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
    public boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options) {
        return delegate.hasSubstitution(method, options);
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
