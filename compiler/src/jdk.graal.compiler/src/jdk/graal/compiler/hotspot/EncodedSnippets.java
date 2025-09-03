/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;
import static jdk.graal.compiler.hotspot.HotSpotReplacementsImpl.isGraalClass;
import static jdk.graal.compiler.hotspot.HotSpotReplacementsImpl.snippetsAreEncoded;
import static jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecode;
import jdk.graal.compiler.core.CompilationWrapper;
import jdk.graal.compiler.core.GraalCompilerOptions;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.SymbolicJVMCIReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NodeClassMap;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.ParameterPlugin;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ConstantBindingParameterPlugin;
import jdk.graal.compiler.replacements.PEGraphDecoder;
import jdk.graal.compiler.replacements.PartialIntrinsicCallTargetNode;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

public class EncodedSnippets {
    /**
     * Returns true if the current runtime or current compilation thread uses encoded snippets and
     * the snippets are already encoded. This is always true when executing in libgraal. If the
     * current thread is replaying a libgraal compilation in jargraal, the method returns true after
     * the snippets are encoded.
     *
     * @return true if encoded snippets are in use and already encoded
     */
    public static boolean isAfterSnippetEncoding() {
        return inRuntimeCode() || snippetsAreEncoded();
    }

    /**
     * Metadata about a graph encoded in {@link EncodedSnippets#snippetEncoding}.
     */
    abstract static class GraphData {
        /**
         * The offset of the graph encoding in {@link EncodedSnippets#snippetEncoding}.
         */
        int startOffset;

        /**
         * The key of the original method if the graph is for a substitute method.
         *
         * @see EncodedSnippets#methodKey(ResolvedJavaMethod)
         */
        String originalMethod;

        /**
         * Info about a snippet's parameters derived from annotations and class file attributes.
         */
        SnippetParameterInfo info;

        GraphData(int startOffset, String originalMethod, SnippetParameterInfo info) {
            this.startOffset = startOffset;
            this.originalMethod = originalMethod;
            this.info = info;
        }

        /**
         * Records the data for an encoded graph. Most graphs are from static methods and can only
         * have a single instantiation but snippets might come from a non-static method and rely on
         * the type of the receiver to devirtualize invokes. In that case each pair of method and
         * receiver represents a potentially different instantiation and these are linked into a
         * chain of {@link VirtualGraphData VirtualGraphDatas}.
         *
         * @param startOffset offset of the encoded graph
         * @param originalMethod method parsed for the graph
         * @param snippetParameterInfo parameter information for snippets
         * @param receiverClass type of the receiver for non-static methods
         * @param existingGraph a previous encoding of this same graph
         */
        public static GraphData create(int startOffset, String originalMethod, SnippetParameterInfo snippetParameterInfo, Class<?> receiverClass, GraphData existingGraph) {
            if (receiverClass == null) {
                assert existingGraph == null : originalMethod;
                return new StaticGraphData(startOffset, originalMethod, snippetParameterInfo);
            } else {
                return new VirtualGraphData(startOffset, originalMethod, snippetParameterInfo, receiverClass, (VirtualGraphData) existingGraph);
            }
        }

        /**
         * Return the proper starting offset based on the actual receiver type of the instantiation
         * which may be null.
         */
        abstract int getStartOffset(Class<?> receiverClass);
    }

    /**
     * Graph data for a snippet or method substitution defined by a static method.
     */
    static class StaticGraphData extends GraphData {

        StaticGraphData(int startOffset, String originalMethod, SnippetParameterInfo info) {
            super(startOffset, originalMethod, info);
        }

        @Override
        int getStartOffset(Class<?> receiverClass) {
            assert receiverClass == null : receiverClass;
            return startOffset;
        }
    }

    /**
     * Graph data for a snippet defined by a virtual method. Method substitutions can't be virtual.
     */
    static class VirtualGraphData extends GraphData {
        private final Class<?> receiverClass;
        private final VirtualGraphData next;

        VirtualGraphData(int startOffset, String originalMethod, SnippetParameterInfo info, Class<?> receiverClass, VirtualGraphData next) {
            super(startOffset, originalMethod, info);
            this.receiverClass = receiverClass;
            this.next = next;
        }

        @Override
        int getStartOffset(Class<?> aClass) {
            VirtualGraphData start = this;
            while (start != null) {
                if (start.receiverClass == aClass) {
                    return start.startOffset;
                }
                start = start.next;
            }
            throw GraalError.shouldNotReachHere("missing receiver type " + aClass); // ExcludeFromJacocoGeneratedReport
        }
    }

    private final byte[] snippetEncoding;
    private final Object[] snippetObjects;
    private final NodeClassMap snippetNodeClasses;
    private final UnmodifiableEconomicMap<String, GraphData> graphDatas;
    private final UnmodifiableEconomicMap<Class<?>, SnippetResolvedJavaType> snippetTypes;

    EncodedSnippets(byte[] snippetEncoding, Object[] snippetObjects, NodeClassMap snippetNodeClasses, UnmodifiableEconomicMap<String, GraphData> graphDatas,
                    UnmodifiableEconomicMap<Class<?>, SnippetResolvedJavaType> snippetTypes) {
        this.snippetEncoding = snippetEncoding;
        this.snippetObjects = snippetObjects;
        this.snippetNodeClasses = snippetNodeClasses;
        this.graphDatas = graphDatas;
        this.snippetTypes = snippetTypes;
    }

    public NodeClassMap getSnippetNodeClasses() {
        return snippetNodeClasses;
    }

    public ResolvedJavaType lookupSnippetType(Class<?> clazz) {
        SnippetResolvedJavaType type = snippetTypes.get(clazz);
        if (type == null && isGraalClass(clazz)) {
            /*
             * During snippet encoding, references to Graal classes from snippets are tracked. If a
             * class isn't found in this path at runtime it means something was missed. However,
             * Truffle on jargraal can look up Graal classes that are not snippet types.
             */
            throw new GraalError("Missing Graal class " + clazz.getName());
        }
        return type;
    }

    /**
     * Gets the signature of a method including all parameter and return type information that can
     * be used as a symbolic key for lookup.
     */
    public static String methodKey(ResolvedJavaMethod method) {
        return method.format("%H.%n(%P)%R");
    }

    StructuredGraph getEncodedSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, HotSpotReplacementsImpl replacements, Object[] args, StructuredGraph.AllowAssumptions allowAssumptions,
                    OptionValues options) {
        GraphData data = null;
        if (graphDatas != null) {
            data = graphDatas.get(methodKey(method));
        }
        if (data == null) {
            throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)")); // ExcludeFromJacocoGeneratedReport
        }

        Class<?> receiverClass = null;
        if (!method.isStatic()) {
            assert args != null && args[0] != null : "must have a receiver";
            receiverClass = args[0].getClass();
        }
        int startOffset = data.getStartOffset(receiverClass);
        ResolvedJavaType declaringClass = method.getDeclaringClass();
        if (declaringClass instanceof SnippetResolvedJavaType) {
            declaringClass = replacements.getProviders().getMetaAccess().lookupJavaType(Object.class);
        }
        /*
         * If there is a possibility of a recorded/replayed compilation, we must not mutate the
         * snippet objects. During recording, this ensures that we record all relevant operations
         * and the cached objects are resolved to proxies. During replay, this ensures that no
         * proxies are stored in the snippet objects.
         */
        boolean allowCacheReplacements = replacements.getProviders().getReplayCompilationSupport() == null &&
                        GraalCompilerOptions.CompilationFailureAction.getValue(options) != CompilationWrapper.ExceptionAction.Diagnose &&
                        !DebugOptions.RecordForReplay.hasBeenSet(options);
        SymbolicEncodedGraph encodedGraph = new SymbolicEncodedGraph(snippetEncoding, startOffset, snippetObjects, allowCacheReplacements,
                        snippetNodeClasses, data.originalMethod, declaringClass);
        return decodeSnippetGraph(encodedGraph, method, original, replacements, args, allowAssumptions, options, true);
    }

    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        GraphData data = null;
        if (graphDatas != null) {
            data = graphDatas.get(methodKey(method));
        }
        assert data != null : method + " " + methodKey(method);
        SnippetParameterInfo info = data.info;
        assert info != null;
        return info;
    }

    public boolean isSnippet(ResolvedJavaMethod method) {
        GraphData data = null;
        if (graphDatas != null) {
            data = graphDatas.get(methodKey(method));
        }
        return data != null && data.info != null;
    }

    static class LibGraalSnippetReflectionProvider implements SnippetReflectionProvider {
        final SnippetReflectionProvider delegate;

        LibGraalSnippetReflectionProvider(SnippetReflectionProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public JavaConstant forObject(Object object) {
            return new SnippetObjectConstant(object);
        }

        @Override
        public <T> T asObject(Class<T> type, JavaConstant constant) {
            return delegate.asObject(type, constant);
        }

        @Override
        public JavaConstant forBoxed(JavaKind kind, Object value) {
            if (kind == JavaKind.Object) {
                return forObject(value);
            }
            return delegate.forBoxed(kind, value);
        }

        @Override
        public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
            return delegate.getInjectedNodeIntrinsicParameter(type);
        }

        @Override
        public Class<?> originalClass(ResolvedJavaType type) {
            return delegate.originalClass(type);
        }

        @Override
        public Executable originalMethod(ResolvedJavaMethod method) {
            return delegate.originalMethod(method);
        }

        @Override
        public Field originalField(ResolvedJavaField field) {
            return delegate.originalField(field);
        }
    }

    @SuppressWarnings("try")
    static StructuredGraph decodeSnippetGraph(SymbolicEncodedGraph encodedGraph, ResolvedJavaMethod method, ResolvedJavaMethod original, HotSpotReplacementsImpl replacements, Object[] args,
                    StructuredGraph.AllowAssumptions allowAssumptions, OptionValues options, boolean mustSucceed) {
        Providers providers = replacements.getProviders();
        ParameterPlugin parameterPlugin = null;
        if (args != null) {
            MetaAccessProvider meta = HotSpotReplacementsImpl.noticeTypes(providers.getMetaAccess());
            SnippetReflectionProvider snippetReflection = replacements.getProviders().getSnippetReflection();
            snippetReflection = new LibGraalSnippetReflectionProvider(snippetReflection);
            parameterPlugin = new ConstantBindingParameterPlugin(args, meta, snippetReflection);
        }
        NodePlugin[] nodePlugins = null;
        if (!inRuntimeCode() && GraalOptions.SnippetCounters.getValue(options)) {
            /*
             * SnippetCounters are supported on jargraal, but they require a plugin to fold the
             * loads of SnippetCounter objects during decoding.
             */
            nodePlugins = new NodePlugin[]{new SnippetCounterFoldingPlugin()};
        }

        try (DebugContext debug = replacements.openDebugContext("LibGraal", method, options)) {
            // @formatter:off
            boolean isSubstitution = true;
            StructuredGraph result = new StructuredGraph.Builder(options, debug, allowAssumptions)
                    .method(method)
                    .trackNodeSourcePosition(encodedGraph.trackNodeSourcePosition())
                    .setIsSubstitution(isSubstitution)
                    .build();
            // @formatter:on
            try (DebugContext.Scope scope = debug.scope("LibGraal.DecodeSnippet", result)) {
                PEGraphDecoder graphDecoder = new SubstitutionGraphDecoder(providers, result, replacements, parameterPlugin, method, INLINE_AFTER_PARSING, encodedGraph, nodePlugins, mustSucceed);
                assert result.isSubstitution();
                graphDecoder.decode(method);
                postDecode(debug, result, original, providers.getSnippetReflection(), providers.getMetaAccess());
                assert result.verify();
                return result;
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    private static void postDecode(DebugContext debug, StructuredGraph result, ResolvedJavaMethod original, SnippetReflectionProvider snippetReflection, MetaAccessProvider metaAccess) {
        debug.dump(DebugContext.VERBOSE_LEVEL, result, "Before PartialIntrinsicCallTargetNode replacement");
        for (PartialIntrinsicCallTargetNode partial : result.getNodes(PartialIntrinsicCallTargetNode.TYPE)) {
            // Ensure the original method matches
            assert partial.checkName(original);
            ValueNode[] arguments = partial.arguments().toArray(new ValueNode[partial.arguments().size()]);
            MethodCallTargetNode target = result.add(new MethodCallTargetNode(partial.invokeKind(), original,
                            arguments, partial.returnStamp(), null));
            partial.replaceAndDelete(target);
        }
        debug.dump(DebugContext.VERBOSE_LEVEL, result, "After decoding");
        for (ConstantNode constant : result.getNodes().filter(ConstantNode.class).snapshot()) {
            if (constant.asConstant() instanceof SnippetObjectConstant snippetConstant) {
                if (!inRuntimeCode() && snippetConstant.asObject(SnippetCounter.class) != null) {
                    /*
                     * Convert SnippetCounter objects wrapped in SnippetObjectConstants to HotSpot
                     * constants (when snippet counters are enabled on jargraal).
                     */
                    ConstantNode replacement = ConstantNode.forConstant(SnippetCounterFoldingPlugin.asHotSpotConstant(snippetConstant, snippetReflection), metaAccess);
                    replacement = result.unique(replacement);
                    constant.replace(result, replacement);
                } else {
                    throw new InternalError(constant.toString(Verbosity.Debugger));
                }
            }
        }
    }

    static class SubstitutionGraphDecoder extends PEGraphDecoder {
        private final ResolvedJavaMethod method;
        private final EncodedGraph encodedGraph;
        private final IntrinsicContext intrinsic;
        private final boolean mustSucceed;

        SubstitutionGraphDecoder(Providers providers, StructuredGraph result, HotSpotReplacementsImpl replacements, ParameterPlugin parameterPlugin, ResolvedJavaMethod method,
                        IntrinsicContext.CompilationContext context, EncodedGraph encodedGraph, NodePlugin[] nodePlugins, boolean mustSucceed) {
            super(providers.getCodeCache().getTarget().arch, result, providers, null,
                            replacements.getGraphBuilderPlugins().getInvocationPlugins(), new InlineInvokePlugin[0], parameterPlugin,
                            nodePlugins, null, null, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false, false);
            this.method = method;
            this.encodedGraph = encodedGraph;
            this.mustSucceed = mustSucceed;
            this.intrinsic = new IntrinsicContext(method, null, replacements.getDefaultReplacementBytecodeProvider(), context, false);
        }

        @Override
        protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod lookupMethod,
                        BytecodeProvider intrinsicBytecodeProvider) {
            if (lookupMethod.equals(method)) {
                return encodedGraph;
            } else {
                throw GraalError.shouldNotReachHere(method.format("%H.%n(%p)")); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        public IntrinsicContext getIntrinsic() {
            return intrinsic;
        }

        @Override
        protected boolean pluginReplacementMustSucceed() {
            return mustSucceed;
        }
    }

    /**
     * Performs constant folding of snippet counter code during snippet decoding on jargraal.
     * Folding the loads allows the SnippetCounterNode_add plugin to succeed.
     */
    @LibGraalSupport.HostedOnly
    private static final class SnippetCounterFoldingPlugin implements NodePlugin {
        @Override
        public boolean handleLoadField(GraphBuilderContext b, ValueNode receiver, ResolvedJavaField field) {
            if (receiver.isConstant()) {
                JavaConstant asJavaConstant = receiver.asJavaConstant();
                return tryConstantFold(b, field, asJavaConstant);
            }
            return false;
        }

        @Override
        public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
            return tryConstantFold(b, field, null);
        }

        /**
         * Converts a snippet object constant to a HotSpot constant.
         *
         * @param snippetConstant the snippet constant
         * @param snippetReflection snippet reflection
         * @return the HotSpot constant
         */
        private static JavaConstant asHotSpotConstant(SnippetObjectConstant snippetConstant, SnippetReflectionProvider snippetReflection) {
            return snippetReflection.forObject(snippetConstant.asObject(Object.class));
        }

        private static boolean tryConstantFold(GraphBuilderContext b, ResolvedJavaField field, JavaConstant receiver) {
            if (!field.isStatic() && (receiver == null || receiver.isNull())) {
                return false;
            }
            ConstantNode result = b.getConstantFieldProvider().readConstantField(field, new ConstantFieldProvider.ConstantFieldTool<>() {
                @Override
                public JavaConstant readValue() {
                    JavaConstant hotSpotReceiver = receiver;
                    if (receiver instanceof SnippetObjectConstant objectConstant) {
                        hotSpotReceiver = asHotSpotConstant(objectConstant, b.getSnippetReflection());
                    }
                    /*
                     * The result may be a HotSpotObjectConstant, which may also have field loads we
                     * need to fold.
                     */
                    return b.getConstantReflection().readFieldValue(field, hotSpotReceiver);
                }

                @Override
                public JavaConstant getReceiver() {
                    return receiver;
                }

                @Override
                public Object getReason() {
                    return b.getGraph().currentNodeSourcePosition();
                }

                @Override
                public ConstantNode foldConstant(JavaConstant ret) {
                    if (ret != null) {
                        return ConstantNode.forConstant(ret, b.getMetaAccess());
                    } else {
                        return null;
                    }
                }

                @Override
                public ConstantNode foldStableArray(JavaConstant ret, int stableDimensions, boolean isDefaultStable) {
                    if (ret != null) {
                        return ConstantNode.forConstant(ret, stableDimensions, isDefaultStable, b.getMetaAccess());
                    } else {
                        return null;
                    }
                }

                @Override
                public OptionValues getOptions() {
                    return b.getOptions();
                }
            });
            if (result != null) {
                result = b.getGraph().unique(result);
                b.push(field.getJavaKind(), result);
                return true;
            }
            return false;
        }
    }

    static class SymbolicEncodedGraph extends EncodedGraph {

        private final ResolvedJavaType[] accessingClasses;
        private final String originalMethod;
        private final boolean allowCacheReplacements;

        SymbolicEncodedGraph(byte[] encoding, int startOffset, Object[] objects, boolean allowCacheReplacements, NodeClassMap nodeClasses, String originalMethod,
                        ResolvedJavaType... accessingClasses) {
            super(encoding, startOffset, objects, nodeClasses, null, null, false, false);
            this.accessingClasses = accessingClasses;
            this.originalMethod = originalMethod;
            this.allowCacheReplacements = allowCacheReplacements;
        }

        SymbolicEncodedGraph(EncodedGraph encodedGraph, ResolvedJavaType declaringClass, String originalMethod, boolean allowCacheReplacements) {
            this(encodedGraph.getEncoding(), encodedGraph.getStartOffset(), encodedGraph.getObjects(), allowCacheReplacements, encodedGraph.getNodeClasses(),
                            originalMethod, declaringClass);
        }

        @Override
        public Object getObject(int i) {
            Object o = objects[i];
            Object replacement = null;
            Throwable error = null;
            if (o instanceof SymbolicJVMCIReference) {
                for (ResolvedJavaType type : accessingClasses) {
                    try {
                        replacement = ((SymbolicJVMCIReference<?>) o).resolve(type);
                        if (replacement != null) {
                            break;
                        }
                    } catch (NoClassDefFoundError e) {
                        error = e;
                    }
                }
            } else if (o instanceof UnresolvedJavaType) {
                for (ResolvedJavaType type : accessingClasses) {
                    try {
                        replacement = ((UnresolvedJavaType) o).resolve(type);
                        if (replacement != null) {
                            break;
                        }
                    } catch (NoClassDefFoundError e) {
                        error = e;
                    }
                }
            } else if (o instanceof UnresolvedJavaMethod) {
                throw new InternalError(o.toString());
            } else if (o instanceof UnresolvedJavaField) {
                for (ResolvedJavaType type : accessingClasses) {
                    try {
                        replacement = ((UnresolvedJavaField) o).resolve(type);
                        if (replacement != null) {
                            break;
                        }
                    } catch (NoClassDefFoundError e) {
                        error = e;
                    }
                }
            } else {
                return o;
            }
            if (replacement != null) {
                o = replacement;
                if (allowCacheReplacements) {
                    objects[i] = replacement;
                }
            } else {
                throw new GraalError(error, "Can't resolve %s", o);
            }
            return o;
        }

        @Override
        public boolean isCallToOriginal(ResolvedJavaMethod callTarget) {
            if (originalMethod != null && originalMethod.equals(EncodedSnippets.methodKey(callTarget))) {
                return true;
            }
            return super.isCallToOriginal(callTarget);
        }
    }

    static class SymbolicResolvedJavaMethod implements SymbolicJVMCIReference<ResolvedJavaMethod> {
        final UnresolvedJavaType type;
        final String methodName;
        final String signature;

        SymbolicResolvedJavaMethod(UnresolvedJavaType type, String methodName, String signature) {
            this.type = type;
            this.methodName = methodName;
            this.signature = signature;
        }

        @Override
        public String toString() {
            return "SymbolicResolvedJavaMethod{" +
                            "declaringType='" + type.getName() + '\'' +
                            ", methodName='" + methodName + '\'' +
                            ", signature='" + signature + '\'' +
                            '}';
        }

        @Override
        public ResolvedJavaMethod resolve(ResolvedJavaType accessingClass) {
            ResolvedJavaType resolvedType = type.resolve(accessingClass);
            if (resolvedType == null) {
                throw new NoClassDefFoundError("Can't resolve " + type.getName() + " with " + accessingClass.getName());
            }
            for (ResolvedJavaMethod method : methodName.equals("<init>") ? resolvedType.getDeclaredConstructors(false) : resolvedType.getDeclaredMethods(false)) {
                if (method.getName().equals(methodName) && method.getSignature().toMethodDescriptor().equals(signature)) {
                    return method;
                }
            }
            throw new NoClassDefFoundError("Can't resolve " + type.getName() + " with " + accessingClass.getName());
        }
    }

    static class SymbolicResolvedJavaFieldLocationIdentity implements SymbolicJVMCIReference<FieldLocationIdentity> {
        final SymbolicResolvedJavaField inner;

        SymbolicResolvedJavaFieldLocationIdentity(SymbolicResolvedJavaField inner) {
            this.inner = inner;
        }

        @Override
        public FieldLocationIdentity resolve(ResolvedJavaType accessingClass) {
            return new FieldLocationIdentity(inner.resolve(accessingClass));
        }

        @Override
        public String toString() {
            return "SymbolicResolvedJavaFieldLocationIdentity{" + inner.toString() + "}";
        }
    }

    static class SymbolicResolvedJavaField implements SymbolicJVMCIReference<ResolvedJavaField> {
        final UnresolvedJavaType declaringType;
        final String name;
        final UnresolvedJavaType signature;
        private final boolean isStatic;

        SymbolicResolvedJavaField(UnresolvedJavaType declaringType, String name, UnresolvedJavaType signature, boolean isStatic) {
            this.declaringType = declaringType;
            this.name = name;
            this.signature = signature;
            this.isStatic = isStatic;
        }

        @Override
        public ResolvedJavaField resolve(ResolvedJavaType accessingClass) {
            ResolvedJavaType resolvedType = declaringType.resolve(accessingClass);
            if (resolvedType == null) {
                throw new NoClassDefFoundError("Can't resolve " + declaringType.getName() + " with " + accessingClass.getName());
            }
            ResolvedJavaType resolvedFieldType = signature.resolve(accessingClass);
            if (resolvedFieldType == null) {
                throw new NoClassDefFoundError("Can't resolve " + signature.getName() + " with " + accessingClass.getName());
            }
            ResolvedJavaField[] fields = isStatic ? resolvedType.getStaticFields() : resolvedType.getInstanceFields(true);
            for (ResolvedJavaField field : fields) {
                if (field.getName().equals(name)) {
                    if (field.getType().equals(resolvedFieldType)) {
                        return field;
                    }
                }
            }
            throw new InternalError("Could not resolve " + this + " in context of " + accessingClass.toJavaName());
        }

        @Override
        public String toString() {
            return "SymbolicResolvedJavaField{" +
                            signature.getName() + ' ' +
                            declaringType.getName() + '.' +
                            name +
                            '}';
        }
    }

    static class SymbolicResolvedJavaMethodBytecode implements SymbolicJVMCIReference<ResolvedJavaMethodBytecode> {
        SymbolicResolvedJavaMethod method;

        SymbolicResolvedJavaMethodBytecode(SymbolicResolvedJavaMethod method) {
            this.method = method;
        }

        @Override
        public ResolvedJavaMethodBytecode resolve(ResolvedJavaType accessingClass) {
            return new ResolvedJavaMethodBytecode(method.resolve(accessingClass));
        }

        @Override
        public String toString() {
            return "SymbolicResolvedJavaMethodBytecode{" +
                            "method=" + method +
                            '}';
        }
    }

    static class SymbolicStampPair implements SymbolicJVMCIReference<StampPair> {
        final Object trustedStamp;
        final Object uncheckedStamp;

        SymbolicStampPair(Object trustedStamp, Object uncheckedStamp) {
            this.trustedStamp = trustedStamp;
            this.uncheckedStamp = uncheckedStamp;
        }

        @Override
        public StampPair resolve(ResolvedJavaType accessingClass) {
            return StampPair.create(resolveStamp(accessingClass, trustedStamp), resolveStamp(accessingClass, uncheckedStamp));
        }

        @Override
        public String toString() {
            return "SymbolicStampPair{" +
                            "trustedStamp=" + trustedStamp +
                            ", uncheckedStamp=" + uncheckedStamp +
                            '}';
        }

        private static Stamp resolveStamp(ResolvedJavaType accessingClass, Object stamp) {
            if (stamp == null) {
                return null;
            }
            if (stamp instanceof Stamp) {
                return (Stamp) stamp;
            }
            return (Stamp) ((SymbolicJVMCIReference<?>) stamp).resolve(accessingClass);
        }
    }

}
