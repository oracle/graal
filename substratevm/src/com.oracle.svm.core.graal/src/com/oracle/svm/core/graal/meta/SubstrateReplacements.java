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
package com.oracle.svm.core.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The replacements implementation for the compiler at runtime. All snippets and method
 * substitutions are pre-compiled at image generation (and not on demand when a snippet is
 * instantiated).
 */
public class SubstrateReplacements extends ReplacementsImpl {

    @Platforms(Platform.HOSTED_ONLY.class)
    public interface GraphMakerFactory {
        GraphMaker create(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected static class Builder {
        protected final GraphMakerFactory graphMakerFactory;
        protected final Map<ResolvedJavaMethod, StructuredGraph> graphs;
        protected final Set<ResolvedJavaMethod> delayedInvocationPluginMethods;

        protected Builder(GraphMakerFactory graphMakerFactory) {
            this.graphMakerFactory = graphMakerFactory;
            this.graphs = new HashMap<>();
            this.delayedInvocationPluginMethods = new HashSet<>();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected class SnippetInlineInvokePlugin implements InlineInvokePlugin {

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            assert b.parsingIntrinsic();
            assert builder != null;
            if (hasGeneratedInvocationPluginAnnotation(method)) {
                builder.delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, null, defaultBytecodeProvider);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)//
    private Builder builder;

    private InvocationPlugins snippetInvocationPlugins;
    private byte[] snippetEncoding;
    private Object[] snippetObjects;
    private NodeClass<?>[] snippetNodeClasses;
    private Map<ResolvedJavaMethod, Integer> snippetStartOffsets;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateReplacements(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target,
                    GraphMakerFactory graphMakerFactory) {
        // Snippets cannot have optimistic assumptions.
        super(options, new GraalDebugHandlersFactory(snippetReflection), providers, snippetReflection, bytecodeProvider, target);
        this.builder = new Builder(graphMakerFactory);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerImmutableObjects(Feature.CompilationAccess access) {
        access.registerAsImmutable(this);
        access.registerAsImmutable(snippetEncoding);
        access.registerAsImmutable(snippetObjects);
        access.registerAsImmutable(snippetNodeClasses);
        access.registerAsImmutable(snippetStartOffsets, o -> true);
        access.registerAsImmutable(snippetInvocationPlugins, o -> true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Collection<StructuredGraph> getSnippetGraphs(boolean trackNodeSourcePosition) {
        List<StructuredGraph> result = new ArrayList<>(snippetStartOffsets.size());
        for (ResolvedJavaMethod method : snippetStartOffsets.keySet()) {
            result.add(getSnippet(method, null, null, trackNodeSourcePosition, null));
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public NodeClass<?>[] getSnippetNodeClasses() {
        return snippetNodeClasses;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Collection<ResolvedJavaMethod> getSnippetMethods() {
        return snippetStartOffsets.keySet();
    }

    @Override
    public void setGraphBuilderPlugins(Plugins plugins) {
        Plugins copy = new Plugins(plugins);
        copy.clearInlineInvokePlugins();
        for (InlineInvokePlugin plugin : plugins.getInlineInvokePlugins()) {
            copy.appendInlineInvokePlugin(plugin == this ? new SnippetInlineInvokePlugin() : plugin);
        }
        super.setGraphBuilderPlugins(copy);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition) {
        Integer startOffset = snippetStartOffsets.get(method);
        if (startOffset == null) {
            throw VMError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
        }

        ParameterPlugin parameterPlugin = null;
        if (args != null) {
            parameterPlugin = new ConstantBindingParameterPlugin(args, providers.getMetaAccess(), snippetReflection);
        }

        EncodedGraph encodedGraph = new EncodedGraph(snippetEncoding, startOffset, snippetObjects, snippetNodeClasses, null, null, null, false, trackNodeSourcePosition);
        try (DebugContext debug = openDebugContext("SVMSnippet_", method)) {
            StructuredGraph result = new StructuredGraph.Builder(options, debug).method(method).trackNodeSourcePosition(trackNodeSourcePosition).build();
            PEGraphDecoder graphDecoder = new PEGraphDecoder(ConfigurationValues.getTarget().arch, result, providers.getMetaAccess(), providers.getConstantReflection(),
                            providers.getConstantFieldProvider(), providers.getStampProvider(), null, snippetInvocationPlugins, new InlineInvokePlugin[0], parameterPlugin, null, null, null) {
                @Override
                protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod lookupMethod, ResolvedJavaMethod originalMethod, BytecodeProvider intrinsicBytecodeProvider, boolean track) {
                    if (lookupMethod.equals(method)) {
                        assert !track || encodedGraph.trackNodeSourcePosition();
                        return encodedGraph;
                    } else {
                        throw VMError.shouldNotReachHere(method.format("%H.%n(%p)"));
                    }
                }
            };

            graphDecoder.decode(method, trackNodeSourcePosition);

            assert result.verify();
            return result;
        }
    }

    /**
     * Compiles the snippet and stores the graph.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void registerSnippet(ResolvedJavaMethod method, boolean trackNodeSourcePosition) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert method.hasBytecodes() : "Snippet must not be abstract or native";
        assert builder.graphs.get(method) == null : "snippet registered twice: " + method.getName();

        try (DebugContext debug = openDebugContext("Snippet_", method)) {
            StructuredGraph graph = makeGraph(debug, defaultBytecodeProvider, method, null, null, trackNodeSourcePosition, null);

            // Check if all methods which should be inlined are really inlined.
            for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = callTarget.targetMethod();
                if (!builder.delayedInvocationPluginMethods.contains(callee)) {
                    throw shouldNotReachHere("method " + callee.getName() + " not inlined in snippet " + method.getName() + " (maybe not final?)");
                }
            }

            builder.graphs.put(method, graph);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Set<ResolvedJavaMethod> getDelayedInvocationPluginMethods() {
        return builder.delayedInvocationPluginMethods;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void encodeSnippets() {
        GraphEncoder encoder = new GraphEncoder(ConfigurationValues.getTarget().arch);
        for (StructuredGraph graph : builder.graphs.values()) {
            encoder.prepare(graph);
        }
        encoder.finishPrepare();

        snippetStartOffsets = new HashMap<>();
        for (Map.Entry<ResolvedJavaMethod, StructuredGraph> entry : builder.graphs.entrySet()) {
            snippetStartOffsets.put(entry.getKey(), encoder.encode(entry.getValue()));
        }
        snippetEncoding = encoder.getEncoding();
        snippetObjects = encoder.getObjects();
        snippetNodeClasses = encoder.getNodeClasses();

        snippetInvocationPlugins = makeInvocationPlugins(getGraphBuilderPlugins(), builder, Function.identity());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static InvocationPlugins makeInvocationPlugins(GraphBuilderConfiguration.Plugins plugins, Builder builder, Function<Object, Object> objectReplacer) {
        Map<ResolvedJavaMethod, InvocationPlugin> result = new HashMap<>(builder.delayedInvocationPluginMethods.size());
        for (ResolvedJavaMethod method : builder.delayedInvocationPluginMethods) {
            ResolvedJavaMethod replacedMethod = (ResolvedJavaMethod) objectReplacer.apply(method);
            InvocationPlugin plugin = plugins.getInvocationPlugins().lookupInvocation(replacedMethod);
            assert plugin != null : "expected invocation plugin for " + replacedMethod;
            result.put(replacedMethod, plugin);
        }
        return new InvocationPlugins(result, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void copyFrom(SubstrateReplacements copyFrom, Function<Object, Object> objectReplacer) {
        snippetInvocationPlugins = makeInvocationPlugins(getGraphBuilderPlugins(), copyFrom.builder, objectReplacer);

        snippetEncoding = Arrays.copyOf(copyFrom.snippetEncoding, copyFrom.snippetEncoding.length);
        snippetNodeClasses = Arrays.copyOf(copyFrom.snippetNodeClasses, copyFrom.snippetNodeClasses.length);
        snippetObjects = new Object[copyFrom.snippetObjects.length];
        for (int i = 0; i < snippetObjects.length; i++) {
            snippetObjects[i] = objectReplacer.apply(copyFrom.snippetObjects[i]);
        }
        snippetStartOffsets = new HashMap<>(copyFrom.snippetStartOffsets.size());
        for (Map.Entry<ResolvedJavaMethod, Integer> entry : copyFrom.snippetStartOffsets.entrySet()) {
            snippetStartOffsets.put((ResolvedJavaMethod) objectReplacer.apply(entry.getKey()), entry.getValue());
        }
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, int callerBci) {
        return false;
    }

    @Override
    public Bytecode getSubstitutionBytecode(ResolvedJavaMethod method) {
        return null;
    }

    @Override
    public StructuredGraph getSubstitution(ResolvedJavaMethod original, int invokeBci, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosiion) {
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    protected final GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
        return builder.graphMakerFactory.create(this, substitute, substitutedMethod);
    }
}
