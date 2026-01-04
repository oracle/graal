/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.graal;

import java.util.BitSet;
import java.util.Map;

import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class DummyReplacements implements Replacements {
    private final CoreProviders providers;

    public DummyReplacements(Providers providers) {
        this.providers = providers.copyWith(this);
    }

    @Override
    public <T> T getInjectedArgument(Class<T> type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Stamp getInjectedStamp(Class<?> type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public CoreProviders getProviders() {
        return providers;
    }

    @Override
    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Map<SnippetTemplate.CacheKey, SnippetTemplate> getTemplatesCache() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public DebugContext openSnippetDebugContext(String idPrefix, ResolvedJavaMethod method, DebugContext outer, OptionValues options) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod method, int invokeBci, boolean isInOOMETry, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, StructuredGraph.AllowAssumptions allowAssumptions, OptionValues options) {
        throw GraalError.unimplementedOverride();

    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public BytecodeProvider getDefaultReplacementBytecodeProvider() {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache snippetTemplates) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public JavaKind getWordKind() {
        throw GraalError.unimplementedOverride();
    }
}
