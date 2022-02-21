/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.EncodedSnippets.methodKey;
import static org.graalvm.compiler.hotspot.HotSpotReplacementsImpl.isGraalClass;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.SymbolicJVMCIReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.hotspot.EncodedSnippets.GraalCapability;
import org.graalvm.compiler.hotspot.EncodedSnippets.GraphData;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicEncodedGraph;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicResolvedJavaField;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicResolvedJavaMethod;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicResolvedJavaMethodBytecode;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.ForeignCallStub;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.PartialIntrinsicCallTargetNode;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetIntegerHistogram;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecode;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * This class performs graph encoding using {@link GraphEncoder} but also converts JVMCI type and
 * method references into a symbolic form that can be resolved at graph decode time using
 * {@link SymbolicJVMCIReference}.
 *
 * An instance of this class only exist when
 * {@link jdk.vm.ci.services.Services#IS_BUILDING_NATIVE_IMAGE} is true.
 */
public class SymbolicSnippetEncoder {

    /**
     * A mapping from the method substitution method to the original method name. The string key and
     * values are produced using {@link EncodedSnippets#methodKey(ResolvedJavaMethod)}.
     */
    private final EconomicMap<String, String> originalMethods = EconomicMap.create();

    private final HotSpotReplacementsImpl originalReplacements;

    static class SnippetKey {

        final ResolvedJavaMethod method;
        final ResolvedJavaMethod original;
        private final Class<?> receiverClass;

        SnippetKey(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver) {
            this.method = method;
            this.original = original;
            assert method.isStatic() == (receiver == null) : "static must not have receiver and non-static must";
            this.receiverClass = receiver != null ? receiver.getClass() : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SnippetKey that = (SnippetKey) o;
            return Objects.equals(method, that.method) && Objects.equals(original, that.original) && Objects.equals(receiverClass, that.receiverClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, original, receiverClass);
        }

        public String keyString() {
            return methodKey(method);
        }

        public Class<?> receiverClass() {
            return receiverClass;
        }

        @Override
        public String toString() {
            return "SnippetKey{" +
                            "method=" + method +
                            ", original=" + original +
                            ", receiverClass=" + receiverClass +
                            '}';
        }
    }

    private final EconomicMap<SnippetKey, BiFunction<OptionValues, HotSpotSnippetReplacementsImpl, StructuredGraph>> pendingSnippetGraphs = EconomicMap.create();

    private final EconomicMap<String, SnippetParameterInfo> snippetParameterInfos = EconomicMap.create();

    private final EconomicSet<InvocationPlugin> conditionalPlugins = EconomicSet.create();

    /**
     * The invocation plugins which were delayed during graph preparation.
     */
    private final Set<ResolvedJavaMethod> delayedInvocationPluginMethods = new HashSet<>();

    void addDelayedInvocationPluginMethod(ResolvedJavaMethod method) {
        delayedInvocationPluginMethods.add(method);
    }

    public void clearSnippetParameterNames() {
        for (SnippetParameterInfo info : snippetParameterInfos.getValues()) {
            info.clearNames();
        }
    }

    protected class SnippetInlineInvokePlugin implements InlineInvokePlugin {

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if (method.getAnnotation(Fold.class) != null) {
                delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, originalReplacements.getDefaultReplacementBytecodeProvider());
        }

        @Override
        public void notifyAfterInline(ResolvedJavaMethod methodToInline) {
            assert methodToInline.getAnnotation(Fold.class) == null : methodToInline;
        }
    }

    /**
     * This plugin disables the snippet counter machinery.
     */
    static final class SnippetCounterPlugin implements NodePlugin {
        String snippetCounterName = 'L' + SnippetCounter.class.getName().replace('.', '/') + ';';
        String snippetIntegerHistogramName = 'L' + SnippetIntegerHistogram.class.getName().replace('.', '/') + ';';

        private final ReplacementsImpl snippetReplacements;

        private SnippetCounterPlugin(ReplacementsImpl snippetReplacements) {
            this.snippetReplacements = snippetReplacements;
        }

        @Override
        public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
            if (field.getName().equals("group") && field.getDeclaringClass().getName().equals(snippetCounterName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(JavaConstant.NULL_POINTER, b.getMetaAccess()));
                return true;
            }
            if (field.getType().getName().equals(snippetCounterName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReplacements.snippetReflection.forObject(SnippetCounter.DISABLED_COUNTER), b.getMetaAccess()));
                return true;
            }

            if (field.getType().getName().equals(snippetIntegerHistogramName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReplacements.snippetReflection.forObject(SnippetIntegerHistogram.DISABLED_COUNTER), b.getMetaAccess()));
                return true;
            }
            return false;
        }
    }

    SymbolicSnippetEncoder(HotSpotReplacementsImpl replacements) {
        this.originalReplacements = replacements;
    }

    synchronized void registerConditionalPlugin(InvocationPlugin plugin) {
        conditionalPlugins.add(plugin);
    }

    @SuppressWarnings("try")
    private StructuredGraph buildGraph(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, BitSet nonNullParameters,
                    boolean trackNodeSourcePosition, OptionValues options, ReplacementsImpl snippetReplacements) {
        assert method.hasBytecodes() : "Snippet must not be abstract or native";
        Object[] args = null;
        if (receiver != null) {
            args = new Object[method.getSignature().getParameterCount(true)];
            args[0] = receiver;
        }
        // Dumps of the graph preparation step can be captured with -H:Dump=LibGraal:2 and
        // MethodFilter can be used to focus on particular snippets.
        IntrinsicContext.CompilationContext contextToUse = IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
        try (DebugContext debug = snippetReplacements.openDebugContext("LibGraalBuildGraph_", method, options)) {
            StructuredGraph graph;
            try (DebugContext.Scope s = debug.scope("LibGraal", method)) {
                graph = snippetReplacements.makeGraph(debug, snippetReplacements.getDefaultReplacementBytecodeProvider(), method, args, nonNullParameters, original,
                                trackNodeSourcePosition, null, contextToUse);

                // Check if all methods which should be inlined are really inlined.
                for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                    ResolvedJavaMethod callee = callTarget.targetMethod();
                    if (!delayedInvocationPluginMethods.contains(callee) && !Objects.equals(callee, original) && !Objects.equals(callee, method)) {
                        throw GraalError.shouldNotReachHere("method " + callee.format("%H.%n") + " not inlined in snippet " + method.getName() + " (maybe not final?)");
                    }
                }
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After buildGraph");
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            assert verifySnippetEncodeDecode(debug, method, original, args, trackNodeSourcePosition, graph);
            assert graph.getAssumptions() == null : graph;
            return graph;
        }
    }

    /**
     * Helper class to provide more precise information about the source of an illegal object when
     * encoding graphs.
     */
    private static class CheckingGraphEncoder extends GraphEncoder {
        CheckingGraphEncoder(Architecture architecture) {
            super(architecture);
        }

        @Override
        protected void addObject(Object object) {
            checkIllegalSnippetObjects(object);
            super.addObject(object);
        }
    }

    /**
     * Check for Objects which should never appear in an encoded snippet.
     */
    private static void checkIllegalSnippetObjects(Object o) {
        if (o instanceof HotSpotSignature || o instanceof ClassfileBytecode) {
            throw new GraalError("Illegal object in encoded snippet: " + o);
        }
    }

    @SuppressWarnings("try")
    private boolean verifySnippetEncodeDecode(DebugContext debug, ResolvedJavaMethod method, ResolvedJavaMethod original, Object[] args, boolean trackNodeSourcePosition,
                    StructuredGraph graph) {
        // Verify the encoding and decoding process
        GraphEncoder encoder = new CheckingGraphEncoder(HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch);
        encoder.prepare(graph);
        encoder.finishPrepare();
        int startOffset = encoder.encode(graph);
        EncodedGraph encodedGraph = new EncodedGraph(encoder.getEncoding(), startOffset, encoder.getObjects(), encoder.getNodeClasses(), graph);

        HotSpotProviders originalProvider = originalReplacements.getProviders();

        SnippetReflectionProvider snippetReflection = originalProvider.getSnippetReflection();
        SymbolicSnippetEncoder.HotSpotSubstrateConstantReflectionProvider constantReflection = new SymbolicSnippetEncoder.HotSpotSubstrateConstantReflectionProvider(
                        originalProvider.getConstantReflection());
        HotSpotProviders newProviders = new HotSpotProviders(originalProvider.getMetaAccess(), originalProvider.getCodeCache(), constantReflection,
                        originalProvider.getConstantFieldProvider(), originalProvider.getForeignCalls(), originalProvider.getLowerer(), null, originalProvider.getSuites(),
                        originalProvider.getRegisters(), snippetReflection, originalProvider.getWordTypes(), originalProvider.getStampProvider(),
                        originalProvider.getPlatformConfigurationProvider(), originalProvider.getMetaAccessExtensionProvider(), originalProvider.getLoopsDataProvider(), originalProvider.getConfig());
        HotSpotSnippetReplacementsImpl filteringReplacements = new HotSpotSnippetReplacementsImpl(newProviders, snippetReflection,
                        originalProvider.getReplacements().getDefaultReplacementBytecodeProvider(), originalProvider.getCodeCache().getTarget());
        filteringReplacements.setGraphBuilderPlugins(originalProvider.getReplacements().getGraphBuilderPlugins());
        try (DebugContext.Scope scope = debug.scope("VerifySnippetEncodeDecode", graph)) {
            SnippetObjectFilter filter = new SnippetObjectFilter(originalProvider);
            for (int i = 0; i < encodedGraph.getNumObjects(); i++) {
                filter.filterSnippetObject(debug, encodedGraph.getObject(i));
            }
            StructuredGraph snippet = filteringReplacements.makeGraph(debug, filteringReplacements.getDefaultReplacementBytecodeProvider(), method, args, null, original,
                            trackNodeSourcePosition, null);
            SymbolicEncodedGraph symbolicGraph = new SymbolicEncodedGraph(encodedGraph, method.getDeclaringClass(), null);
            StructuredGraph decodedSnippet = EncodedSnippets.decodeSnippetGraph(symbolicGraph, original != null ? original : method, original, originalReplacements, null,
                            StructuredGraph.AllowAssumptions.ifNonNull(graph.getAssumptions()), graph.getOptions(), false);
            String snippetString = getCanonicalGraphString(snippet, true, false);
            String decodedSnippetString = getCanonicalGraphString(decodedSnippet, true, false);
            if (snippetString.equals(decodedSnippetString)) {
                debug.log("Snippet decode for %s produces exactly same graph", method);
                debug.dump(DebugContext.VERBOSE_LEVEL, decodedSnippet, "Decoded snippet graph for %s", method);
            } else {
                debug.log("Snippet decode for %s produces different graph", method);
                debug.log("%s", compareGraphStrings(snippet, snippetString, decodedSnippet,
                                decodedSnippetString));
                debug.dump(DebugContext.VERBOSE_LEVEL, snippet, "Snippet graph for %s", method);
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Encoded snippet graph for %s", method);
                debug.dump(DebugContext.VERBOSE_LEVEL, decodedSnippet, "Decoded snippet graph for %s", method);
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }
        return true;
    }

    /**
     * Encode all pending graphs and return the result.
     */
    @SuppressWarnings("try")
    private synchronized EncodedSnippets encodeSnippets(OptionValues options) {
        GraphBuilderConfiguration.Plugins plugins = originalReplacements.getGraphBuilderPlugins();
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        GraphBuilderConfiguration.Plugins copy = new GraphBuilderConfiguration.Plugins(plugins, invocationPlugins);
        HotSpotProviders providers = originalReplacements.getProviders().copyWith(new HotSpotSubstrateConstantReflectionProvider(originalReplacements.getProviders().getConstantReflection()));
        HotSpotSnippetReplacementsImpl snippetReplacements = new HotSpotSnippetReplacementsImpl(originalReplacements, providers.copyWith());
        snippetReplacements.setGraphBuilderPlugins(copy);
        copy.clearInlineInvokePlugins();
        copy.appendInlineInvokePlugin(new SnippetInlineInvokePlugin());
        copy.appendNodePlugin(new SnippetCounterPlugin(snippetReplacements));

        EconomicMap<SnippetKey, StructuredGraph> preparedSnippetGraphs = EconomicMap.create();
        MapCursor<SnippetKey, BiFunction<OptionValues, HotSpotSnippetReplacementsImpl, StructuredGraph>> cursor = pendingSnippetGraphs.getEntries();
        while (cursor.advance()) {
            SnippetKey key = cursor.getKey();
            preparedSnippetGraphs.put(key, cursor.getValue().apply(options, snippetReplacements));
        }

        DebugContext debug = snippetReplacements.openSnippetDebugContext("SnippetEncoder", null, options);
        try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {
            for (StructuredGraph graph : preparedSnippetGraphs.getValues()) {
                for (Node node : graph.getNodes()) {
                    node.setNodeSourcePosition(null);
                }
            }
            return encodeSnippets(debug, preparedSnippetGraphs);
        }
    }

    synchronized void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition) {
        if (HotSpotReplacementsImpl.snippetsAreEncoded()) {
            throw new GraalError("Snippet encoding has already been done");
        }

        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        SnippetKey key = new SnippetKey(method, original, receiver);
        findSnippetMethod(method);

        if (!pendingSnippetGraphs.containsKey(key)) {
            if (original != null) {
                originalMethods.put(key.keyString(), methodKey(original));
            }
            SnippetParameterInfo info = new SnippetParameterInfo(method);
            snippetParameterInfos.put(key.keyString(), info);

            int i = 0;
            int offset = 0;
            if (!method.isStatic()) {
                assert info.isConstantParameter(0) : "receiver is always constant";
                ensureSnippetTypeAvailable(method.getDeclaringClass());
                i++;
                offset = 1;
            }
            for (; i < info.getParameterCount(); i++) {
                if (info.isConstantParameter(i) || info.isVarargsParameter(i)) {
                    JavaType type = method.getSignature().getParameterType(i - offset, method.getDeclaringClass());
                    if (type instanceof ResolvedJavaType) {
                        ResolvedJavaType resolvedJavaType = (ResolvedJavaType) type;
                        if (info.isVarargsParameter(i)) {
                            resolvedJavaType = resolvedJavaType.getElementalType();
                        }
                        assert resolvedJavaType.isPrimitive() || isGraalClass(resolvedJavaType) : method + ": only Graal classes can be @ConstantParameter or @VarargsParameter: " + type;
                        ensureSnippetTypeAvailable(resolvedJavaType);
                    } else {
                        throw new InternalError(type.toString());
                    }
                }
            }
            pendingSnippetGraphs.put(key, new BiFunction<>() {
                @Override
                public StructuredGraph apply(OptionValues cmopileOptions, HotSpotSnippetReplacementsImpl snippetReplacements) {
                    return buildGraph(method, original, receiver, SnippetParameterInfo.getNonNullParameters(info), trackNodeSourcePosition,
                                    cmopileOptions, snippetReplacements);
                }
            });
        }
    }

    ResolvedJavaMethod findSnippetMethod(ResolvedJavaMethod method) {
        ResolvedJavaType type = method.getDeclaringClass();
        JavaConstant mirror = originalReplacements.getProviders().getConstantReflection().asJavaClass(type);
        Class<?> clazz = originalReplacements.getProviders().getSnippetReflection().asObject(Class.class, mirror);
        SnippetResolvedJavaType snippetType = lookupSnippetType(clazz);
        assert (snippetType != null);
        SnippetResolvedJavaMethod m = new SnippetResolvedJavaMethod(snippetType, method);
        return snippetType.add(m);
    }

    private void ensureSnippetTypeAvailable(ResolvedJavaType type) {
        if (!type.getElementalType().isPrimitive()) {
            Objects.requireNonNull(getSnippetType(type));
        }
    }

    private SnippetResolvedJavaType getSnippetType(ResolvedJavaType type) {
        // Ensure types are available for the implementors of ResolvedJavaType
        lookupSnippetType(type.getClass());

        JavaConstant mirror = originalReplacements.getProviders().getConstantReflection().asJavaClass(type);
        Class<?> clazz = originalReplacements.getProviders().getSnippetReflection().asObject(Class.class, mirror);
        return lookupSnippetType(clazz);
    }

    @SuppressWarnings("try")
    private boolean verifySingle(DebugContext debug, StructuredGraph graph) {
        try (DebugContext.Scope scope = debug.scope("FilterSingleSnippet", graph)) {

            EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graph, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch);
            SnippetObjectFilter filter = new SnippetObjectFilter(originalReplacements.getProviders());
            for (int i = 0; i < encodedGraph.getNumObjects(); i++) {
                filter.filterSnippetObject(debug, encodedGraph.getObject(i));
            }
            return true;
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    private synchronized EncodedSnippets encodeSnippets(DebugContext debug, EconomicMap<SnippetKey, StructuredGraph> preparedSnippetGraphs) {
        GraphEncoder encoder = new GraphEncoder(HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch, debug);
        for (StructuredGraph graph : preparedSnippetGraphs.getValues()) {
            graph.resetDebug(debug);
            assert verifySingle(debug, graph);
            encoder.prepare(graph);
        }
        encoder.finishPrepare();

        EconomicMap<String, GraphData> graphDatas = EconomicMap.create();
        MapCursor<SnippetKey, StructuredGraph> cursor = preparedSnippetGraphs.getEntries();
        while (cursor.advance()) {
            SnippetKey key = cursor.getKey();
            String keyString = key.keyString();
            GraphData previous = graphDatas.get(keyString);
            GraphData data = GraphData.create(encoder.encode(cursor.getValue()), originalMethods.get(keyString), snippetParameterInfos.get(keyString), key.receiverClass(), previous);
            graphDatas.put(keyString, data);
        }

        // Ensure a few types are available
        lookupSnippetType(GraalHotSpotVMConfig.class);
        lookupSnippetType(NamedLocationIdentity.class);
        lookupSnippetType(SnippetTemplate.EagerSnippetInfo.class);
        lookupSnippetType(ForeignCallStub.class);

        SnippetObjectFilter filter = new SnippetObjectFilter(originalReplacements.getProviders());
        byte[] snippetEncoding = encoder.getEncoding();
        Object[] snippetObjects = encoder.getObjects();
        for (int i = 0; i < snippetObjects.length; i++) {
            Object o = filter.filterSnippetObject(debug, snippetObjects[i]);
            debug.log("snippetObjects[%d] = %s -> %s", i, o != null ? o.getClass().getSimpleName() : null, o);
            snippetObjects[i] = o;
        }
        debug.log("Encoded %d snippet preparedSnippetGraphs using %d bytes with %d objects", graphDatas.size(), snippetEncoding.length, snippetObjects.length);
        return new EncodedSnippets(snippetEncoding, snippetObjects, encoder.getNodeClasses(), graphDatas, snippetTypes);
    }

    /**
     * Encode any outstanding graphs and return true if any work was done.
     */
    @SuppressWarnings("try")
    public synchronized boolean encode(OptionValues options) {
        EncodedSnippets encodedSnippets = encodeSnippets(options);
        if (encodedSnippets != null) {
            HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);
            return true;
        }
        return false;
    }

    static class HotSpotSubstrateConstantReflectionProvider implements ConstantReflectionProvider {

        private final ConstantReflectionProvider constantReflection;

        HotSpotSubstrateConstantReflectionProvider(ConstantReflectionProvider constantReflection) {
            this.constantReflection = constantReflection;
        }

        HashSet<JavaConstant> safeConstants = new HashSet<>();

        @Override
        public Boolean constantEquals(Constant x, Constant y) {
            return constantReflection.constantEquals(x, y);
        }

        @Override
        public Integer readArrayLength(JavaConstant array) {
            return constantReflection.readArrayLength(array);
        }

        @Override
        public JavaConstant readArrayElement(JavaConstant array, int index) {
            return constantReflection.readArrayElement(array, index);
        }

        @Override
        public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
            JavaConstant javaConstant = constantReflection.readFieldValue(field, receiver);
            String fieldClass = field.getDeclaringClass().getName();
            if (fieldClass.contains("java/util/EnumMap") || field.getType().getName().contains("java/util/EnumMap")) {
                throw new GraalError("Snippets should not use EnumMaps in generated code");
            }

            if (!safeConstants.contains(receiver) &&
                            !fieldClass.contains("graalvm") &&
                            !fieldClass.contains("com/oracle/graal") &&
                            !fieldClass.contains("jdk/vm/ci/") &&
                            !fieldClass.contains("jdk/internal/vm/compiler") &&
                            !field.getName().equals("TYPE")) {
                // Only permit constant reflection on compiler classes. This is necessary primarily
                // because of the boxing snippets which are compiled as snippets but are really just
                // regular JDK java sources that are being compiled like a snippet. These shouldn't
                // permit constant folding during graph preparation as that embeds constants from
                // the runtime into a compiler graph.
                return null;
            }
            if (javaConstant.getJavaKind() == JavaKind.Object) {
                safeConstants.add(javaConstant);
            }
            return javaConstant;
        }

        @Override
        public JavaConstant boxPrimitive(JavaConstant source) {
            return constantReflection.boxPrimitive(source);
        }

        @Override
        public JavaConstant unboxPrimitive(JavaConstant source) {
            return constantReflection.unboxPrimitive(source);
        }

        @Override
        public JavaConstant forString(String value) {
            return constantReflection.forString(value);
        }

        @Override
        public ResolvedJavaType asJavaType(Constant constant) {
            return constantReflection.asJavaType(constant);
        }

        @Override
        public MethodHandleAccessProvider getMethodHandleAccess() {
            return constantReflection.getMethodHandleAccess();
        }

        @Override
        public MemoryAccessProvider getMemoryAccessProvider() {
            return constantReflection.getMemoryAccessProvider();
        }

        @Override
        public JavaConstant asJavaClass(ResolvedJavaType type) {
            return constantReflection.asJavaClass(type);
        }

        @Override
        public Constant asObjectHub(ResolvedJavaType type) {
            return constantReflection.asObjectHub(type);
        }
    }

    class SnippetObjectFilter {

        private final HotSpotWordTypes wordTypes;

        SnippetObjectFilter(HotSpotProviders providers) {
            this.wordTypes = providers.getWordTypes();
        }

        SnippetReflectionProvider getSnippetReflection() {
            return originalReplacements.getProviders().getSnippetReflection();
        }

        EconomicMap<Object, Object> cachedFilteredObjects = EconomicMap.create();

        /**
         * Objects embedded in encoded graphs might need to be converted into a symbolic form so
         * convert the object or pass it through.
         */
        private Object filterSnippetObject(DebugContext debug, Object o) {
            checkIllegalSnippetObjects(o);
            Object cached = cachedFilteredObjects.get(0);
            if (cached != null) {
                return cached;
            }
            if (o instanceof HotSpotResolvedJavaMethod) {
                HotSpotResolvedJavaMethod method = (HotSpotResolvedJavaMethod) o;
                if (isGraalClass(method.getDeclaringClass())) {
                    ResolvedJavaMethod snippetMethod = findSnippetMethod(method);
                    cachedFilteredObjects.put(method, snippetMethod);
                    return snippetMethod;
                }
                return filterMethod(debug, method);
            } else if (o instanceof HotSpotResolvedJavaField) {
                return filterField(debug, (HotSpotResolvedJavaField) o);
            } else if (o instanceof HotSpotResolvedJavaType) {
                return filterType(debug, (HotSpotResolvedJavaType) o);
            } else if (o instanceof HotSpotObjectConstant) {
                return new SnippetObjectConstant(getSnippetReflection().asObject(Object.class, (HotSpotObjectConstant) o));
            } else if (o instanceof NodeSourcePosition) {
                // Filter these out for now. These can't easily be handled because these positions
                // description snippet methods which might not be available in the runtime.
                return null;
            } else if (o instanceof HotSpotForeignCallsProvider || o instanceof GraalHotSpotVMConfig || o instanceof HotSpotWordTypes || o instanceof TargetDescription ||
                            o instanceof SnippetReflectionProvider) {
                return new GraalCapability(o.getClass());
            } else if (o instanceof Stamp) {
                return filterStamp(debug, (Stamp) o);
            } else if (o instanceof StampPair) {
                return filterStampPair(debug, (StampPair) o);
            } else if (o instanceof ResolvedJavaMethodBytecode) {
                return filterBytecode(debug, (ResolvedJavaMethodBytecode) o);
            }
            return o;
        }

        private SymbolicResolvedJavaMethod filterMethod(DebugContext debug, ResolvedJavaMethod method) {
            SymbolicResolvedJavaMethod symbolic = (SymbolicResolvedJavaMethod) cachedFilteredObjects.get(method);
            if (symbolic != null) {
                return symbolic;
            }
            if (isGraalClass(method.getDeclaringClass())) {
                throw new GraalError("Graal methods shouldn't leak into image: " + method);
            }
            UnresolvedJavaType type = (UnresolvedJavaType) filterType(debug, method.getDeclaringClass());
            String methodName = method.getName();
            String signature = method.getSignature().toMethodDescriptor();
            symbolic = new SymbolicResolvedJavaMethod(type, methodName, signature);
            debug.log(DebugContext.VERBOSE_LEVEL, "filtered %s -> %s", method, symbolic);
            cachedFilteredObjects.put(method, symbolic);
            return symbolic;
        }

        private SymbolicResolvedJavaMethodBytecode filterBytecode(DebugContext debug, ResolvedJavaMethodBytecode bytecode) {
            SymbolicResolvedJavaMethodBytecode symbolic = (SymbolicResolvedJavaMethodBytecode) cachedFilteredObjects.get(bytecode);
            if (symbolic != null) {
                return symbolic;
            }
            symbolic = new EncodedSnippets.SymbolicResolvedJavaMethodBytecode(filterMethod(debug, bytecode.getMethod()));
            debug.log(DebugContext.VERBOSE_LEVEL, "filtered %s -> %s", bytecode, symbolic);
            cachedFilteredObjects.put(bytecode, filterMethod(debug, bytecode.getMethod()));
            return symbolic;
        }

        private JavaType filterType(DebugContext debug, ResolvedJavaType type) {
            UnresolvedJavaType unresolvedJavaType = (UnresolvedJavaType) cachedFilteredObjects.get(type);
            if (unresolvedJavaType != null) {
                return unresolvedJavaType;
            }
            if (isGraalClass(type)) {
                throw new GraalError("Graal types shouldn't leak into image: " + type);
            }
            unresolvedJavaType = UnresolvedJavaType.create(type.getName());
            debug.log(DebugContext.VERBOSE_LEVEL, "filtered %s -> %s", type, unresolvedJavaType);
            cachedFilteredObjects.put(type, unresolvedJavaType);
            return unresolvedJavaType;
        }

        private Object filterField(DebugContext debug, HotSpotResolvedJavaField field) {
            if (!field.getDeclaringClass().getName().startsWith("Ljava/lang/")) {
                // Might require adjustments in HotSpotSubstrateConstantReflectionProvider
                throw new InternalError("All other fields must have been resolved: " + field);
            }
            UnresolvedJavaType declaringType = (UnresolvedJavaType) filterType(debug, field.getDeclaringClass());
            String name = field.getName();
            UnresolvedJavaType signature = (UnresolvedJavaType) filterType(debug, (ResolvedJavaType) field.getType());
            boolean isStatic = field.isStatic();
            return new SymbolicResolvedJavaField(declaringType, name, signature, isStatic);
        }

        private Object filterStampPair(DebugContext debug, StampPair stampPair) {
            if (stampPair.getTrustedStamp() instanceof AbstractObjectStamp) {
                Object cached = cachedFilteredObjects.get(stampPair);
                if (cached != null) {
                    return cached;
                }
                Object trustedStamp = filterStamp(debug, stampPair.getTrustedStamp());
                Object uncheckdStamp = filterStamp(debug, stampPair.getUncheckedStamp());
                if (trustedStamp instanceof ObjectStamp && uncheckdStamp instanceof ObjectStamp) {
                    cached = StampPair.create((Stamp) trustedStamp, (Stamp) uncheckdStamp);
                } else {
                    cached = new EncodedSnippets.SymbolicStampPair(trustedStamp, uncheckdStamp);
                }
                debug.log(DebugContext.VERBOSE_LEVEL, "filtered %s -> %s", stampPair, cached);
                cachedFilteredObjects.put(stampPair, cached);
                return cached;
            }
            return stampPair;
        }

        private Object filterStamp(DebugContext debug, Stamp stamp) {
            if (stamp == null) {
                return null;
            }
            Object cached = cachedFilteredObjects.get(stamp);
            if (cached != null) {
                return cached;
            }
            if (stamp instanceof AbstractObjectStamp) {
                AbstractObjectStamp objectStamp = (AbstractObjectStamp) stamp;
                ResolvedJavaType type = objectStamp.type();
                if (type == null) {
                    return stamp;
                }
                if (wordTypes.isWord(type)) {
                    throw new InternalError("should have converted Word types by now");
                }
                if (type instanceof SnippetResolvedJavaType) {
                    throw new InternalError(stamp.toString());
                }
                ResolvedJavaType elementalType = type.getElementalType();
                if (elementalType.getName().startsWith("Ljdk/vm/ci") || elementalType.getName().startsWith("Lorg/graalvm/") || elementalType.getName().startsWith("Lcom/oracle/graal/")) {
                    if (!type.equals(elementalType)) {
                        // Ensure that the underlying type is available
                        ensureSnippetTypeAvailable(elementalType);
                    }
                    type = getSnippetType(type);
                    assert type != null : type;
                    cached = new ObjectStamp(type, objectStamp.isExactType(), objectStamp.nonNull(), objectStamp.alwaysNull(), objectStamp.isAlwaysArray());
                } else {
                    cached = stamp.makeSymbolic();
                }
                debug.log(DebugContext.VERBOSE_LEVEL, "filtered %s -> %s", stamp, cached);
                cachedFilteredObjects.put(stamp, cached);
                return cached;
            } else {
                cached = stamp.makeSymbolic();
                if (cached != null) {
                    cachedFilteredObjects.put(stamp, cached);
                    return cached;
                }
            }
            return stamp;
        }
    }

    private static String compareGraphStrings(StructuredGraph expectedGraph, String expectedString, StructuredGraph actualGraph, String actualString) {
        if (!expectedString.equals(actualString)) {
            String[] expectedLines = expectedString.split("\n");
            String[] actualLines = actualString.split("\n");
            int diffIndex = -1;
            int limit = Math.min(actualLines.length, expectedLines.length);
            String marker = " <<<";
            for (int i = 0; i < limit; i++) {
                if (!expectedLines[i].equals(actualLines[i])) {
                    diffIndex = i;
                    break;
                }
            }
            if (diffIndex == -1) {
                // Prefix is the same so add some space after the prefix
                diffIndex = limit;
                if (actualLines.length == limit) {
                    actualLines = Arrays.copyOf(actualLines, limit + 1);
                    actualLines[diffIndex] = "";
                } else {
                    assert expectedLines.length == limit;
                    expectedLines = Arrays.copyOf(expectedLines, limit + 1);
                    expectedLines[diffIndex] = "";
                }
            }
            // Place a marker next to the first line that differs
            expectedLines[diffIndex] = expectedLines[diffIndex] + marker;
            actualLines[diffIndex] = actualLines[diffIndex] + marker;
            String ediff = String.join("\n", expectedLines);
            String adiff = String.join("\n", actualLines);
            return "mismatch in preparedSnippetGraphs:\n========= expected (" + expectedGraph + ") =========\n" + ediff + "\n\n========= actual (" + actualGraph + ") =========\n" + adiff;
        } else {
            return "mismatch in preparedSnippetGraphs";
        }
    }

    private static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.EARLIEST);
        StructuredGraph.ScheduleResult scheduleResult = graph.getLastSchedule();

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        List<String> constantsLines = new ArrayList<>();

        StringBuilder result = new StringBuilder();
        for (Block block : scheduleResult.getCFG().getBlocks()) {
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ).append(' ');
            }
            result.append('\n');
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode || node instanceof ParameterNode)) {
                        if (node instanceof ConstantNode) {
                            if (checkConstants) {
                                String name = node.toString(Verbosity.Name);
                                if (excludeVirtual) {
                                    constantsLines.add(name);
                                } else {
                                    constantsLines.add(name + "    (" + filteredUsageCount(node) + ")");
                                }
                            }
                        } else {
                            int id;
                            if (canonicalId.get(node) != null) {
                                id = canonicalId.get(node);
                            } else {
                                id = nextId++;
                                canonicalId.set(node, id);
                            }
                            String name = node.getClass().getSimpleName();
                            result.append("  ").append(id).append('|').append(name);
                            if (node instanceof AccessFieldNode) {
                                result.append('#');
                                result.append(((AccessFieldNode) node).field());
                            }
                            if (!excludeVirtual) {
                                result.append("    (");
                                result.append(filteredUsageCount(node));
                                result.append(')');
                            }
                            result.append('\n');
                        }
                    }
                }
            }
        }

        StringBuilder constantsLinesResult = new StringBuilder();
        if (checkConstants) {
            constantsLinesResult.append(constantsLines.size()).append(" constants:\n");
        }
        Collections.sort(constantsLines);
        for (String s : constantsLines) {
            constantsLinesResult.append(s);
            constantsLinesResult.append('\n');
        }

        return constantsLinesResult.toString() + result.toString();
    }

    private static int filteredUsageCount(Node node) {
        return node.usages().filter(n -> !(n instanceof FrameState)).count();
    }

    /**
     * This horror show of classes exists solely get {@link HotSpotSnippetBytecodeParser} to be used
     * as the parser for these snippets.
     */
    class HotSpotSnippetReplacementsImpl extends HotSpotReplacementsImpl {
        HotSpotSnippetReplacementsImpl(HotSpotReplacementsImpl replacements, HotSpotProviders providers) {
            super(replacements, providers);
        }

        HotSpotSnippetReplacementsImpl(HotSpotProviders providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
            super(providers, snippetReflection, bytecodeProvider, target);
        }

        @Override
        protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
            return new SnippetGraphMaker(this, substitute, original);
        }

        @Override
        public boolean isEncodingSnippets() {
            return true;
        }

    }

    class SnippetGraphMaker extends ReplacementsImpl.GraphMaker {
        SnippetGraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            super(replacements, substitute, substitutedMethod);
        }

        @Override
        protected GraphBuilderPhase.Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                        IntrinsicContext initialIntrinsicContext) {
            return new HotSpotSnippetGraphBuilderPhase(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected void finalizeGraph(StructuredGraph graph) {
            if (substitutedMethod != null) {
                for (MethodCallTargetNode target : graph.getNodes(MethodCallTargetNode.TYPE)) {
                    if (substitutedMethod.equals(target.targetMethod())) {
                        // Replace call to original method with a placeholder
                        PartialIntrinsicCallTargetNode partial = graph.add(
                                        new PartialIntrinsicCallTargetNode(target.invokeKind(), substitutedMethod, target.returnStamp(), target.arguments().toArray(new ValueNode[0])));
                        target.replaceAndDelete(partial);
                    }
                }
            }
            super.finalizeGraph(graph);
        }
    }

    class HotSpotSnippetGraphBuilderPhase extends GraphBuilderPhase.Instance {
        HotSpotSnippetGraphBuilderPhase(CoreProviders theProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            super(theProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new HotSpotSnippetBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    class HotSpotSnippetBytecodeParser extends BytecodeParser {
        HotSpotSnippetBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
        }

        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            // Fold is always deferred but NodeIntrinsics may have to wait if all their arguments
            // aren't constant yet.
            return plugin.isGeneratedFromFoldOrNodeIntrinsic();
        }

        @Override
        public boolean shouldDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.isGeneratedFromFoldOrNodeIntrinsic();
        }

        @Override
        protected boolean canInlinePartialIntrinsicExit() {
            return false;
        }

        @Override
        protected boolean tryInvocationPlugin(CallTargetNode.InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
                return false;
            }

            InvocationPlugin plugin = graphBuilderConfig.getPlugins().getInvocationPlugins().lookupInvocation(targetMethod, options);
            if (plugin != null && conditionalPlugins.contains(plugin)) {
                // Because supporting arbitrary plugins in the context of encoded graphs is complex
                // we disallow it. This limitation can be worked around through the use of method
                // substitutions.
                throw new GraalError("conditional plugins are unsupported in snippets and method substitutions: " + targetMethod + " " + plugin);
            }
            return super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
        }
    }

    private static final Map<Class<?>, SnippetResolvedJavaType> snippetTypes = new HashMap<>();

    private static synchronized SnippetResolvedJavaType lookupSnippetType(Class<?> clazz) {
        SnippetResolvedJavaType type = null;
        if (isGraalClass(clazz)) {
            type = snippetTypes.get(clazz);
            if (type == null) {
                type = createType(clazz);
            }
        }
        return type;
    }

    private static synchronized SnippetResolvedJavaType createType(Class<?> clazz) {
        SnippetResolvedJavaType type;
        type = new SnippetResolvedJavaType(clazz);
        snippetTypes.put(clazz, type);
        if (clazz.isArray()) {
            // Create the chain of array classes
            Class<?> arrayClass = clazz;
            SnippetResolvedJavaType arrayType = type;
            while (arrayClass.isArray()) {
                SnippetResolvedJavaType component = lookupSnippetType(arrayClass.getComponentType());
                component.setArrayOfType(arrayType);
                arrayClass = arrayClass.getComponentType();
                arrayType = component;
            }
        }
        return type;
    }

    private static class MetaAccessProviderDelegate implements MetaAccessProvider {
        MetaAccessProvider delegate;

        MetaAccessProviderDelegate(MetaAccessProvider metaAccess) {
            this.delegate = metaAccess;
        }

        @Override
        public ResolvedJavaType lookupJavaType(Class<?> clazz) {
            lookupSnippetType(clazz);
            return delegate.lookupJavaType(clazz);
        }

        @Override
        public ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod) {
            return delegate.lookupJavaMethod(reflectionMethod);
        }

        @Override
        public ResolvedJavaField lookupJavaField(Field reflectionField) {
            return delegate.lookupJavaField(reflectionField);
        }

        @Override
        public ResolvedJavaType lookupJavaType(JavaConstant constant) {
            return delegate.lookupJavaType(constant);
        }

        @Override
        public long getMemorySize(JavaConstant constant) {
            return delegate.getMemorySize(constant);
        }

        @Override
        public Signature parseMethodDescriptor(String methodDescriptor) {
            return delegate.parseMethodDescriptor(methodDescriptor);
        }

        @Override
        public JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId) {
            return delegate.encodeDeoptActionAndReason(action, reason, debugId);
        }

        @Override
        public JavaConstant encodeSpeculation(SpeculationLog.Speculation speculation) {
            return delegate.encodeSpeculation(speculation);
        }

        @Override
        public SpeculationLog.Speculation decodeSpeculation(JavaConstant constant, SpeculationLog speculationLog) {
            return delegate.decodeSpeculation(constant, speculationLog);
        }

        @Override
        public DeoptimizationReason decodeDeoptReason(JavaConstant constant) {
            return delegate.decodeDeoptReason(constant);
        }

        @Override
        public DeoptimizationAction decodeDeoptAction(JavaConstant constant) {
            return delegate.decodeDeoptAction(constant);
        }

        @Override
        public int decodeDebugId(JavaConstant constant) {
            return delegate.decodeDebugId(constant);
        }

        @Override
        public int getArrayBaseOffset(JavaKind elementKind) {
            return delegate.getArrayBaseOffset(elementKind);
        }

        @Override
        public int getArrayIndexScale(JavaKind elementKind) {
            return delegate.getArrayIndexScale(elementKind);
        }

    }

    /**
     * Returns a proxy {@link MetaAccessProvider} that can log types which are looked up during
     * normal processing. This is used to ensure that types needed for libgraal are available.
     */
    public static MetaAccessProvider noticeTypes(MetaAccessProvider metaAccess) {
        return new MetaAccessProviderDelegate(metaAccess);
    }
}
