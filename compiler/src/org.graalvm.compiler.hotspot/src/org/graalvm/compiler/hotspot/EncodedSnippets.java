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
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.runtime.JVMCI.getRuntime;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.UseEncodedGraphs;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.SymbolicJVMCIReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.PartialIntrinsicCallTargetNode;

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
    abstract static class GraphData {
        int startOffset;
        String originalMethod;
        SnippetParameterInfo info;

        GraphData(int startOffset, String originalMethod, SnippetParameterInfo info) {
            this.startOffset = startOffset;
            this.originalMethod = originalMethod;
            this.info = info;
        }

        /**
         * Record the data for an encoded graph. Most graphs are from static methods and can only
         * have a single instantiation but snippets might come for a non-static method and rely on
         * the type of the receiver to devirtualize invokes. In that case each pair of method and
         * receiver represents a potentially different instantiation and these are linked into a
         * chain of {@link VirtualGraphData VirtualGraphDatas}.
         *
         * @param startOffset offset of the encoded graph
         * @param originalMethod method parsed for the graph
         * @param snippetParameterInfo parameter information for snippets
         * @param receiverClass static type of the receiver for non-virtual methods
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
            assert receiverClass == null;
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
            throw GraalError.shouldNotReachHere("missing receiver type " + aClass);
        }
    }

    private final byte[] snippetEncoding;
    private final Object[] snippetObjects;
    private final NodeClass<?>[] snippetNodeClasses;
    private final UnmodifiableEconomicMap<String, GraphData> graphDatas;
    private final Map<Class<?>, SnippetResolvedJavaType> snippetTypes;

    EncodedSnippets(byte[] snippetEncoding, Object[] snippetObjects, NodeClass<?>[] snippetNodeClasses, UnmodifiableEconomicMap<String, GraphData> graphDatas,
                    Map<Class<?>, SnippetResolvedJavaType> snippetTypes) {
        this.snippetEncoding = snippetEncoding;
        this.snippetObjects = snippetObjects;
        this.snippetNodeClasses = snippetNodeClasses;
        this.graphDatas = graphDatas;
        this.snippetTypes = snippetTypes;
    }

    public NodeClass<?>[] getSnippetNodeClasses() {
        return snippetNodeClasses;
    }

    ResolvedJavaType lookupSnippetType(Class<?> clazz) {
        return snippetTypes.get(clazz);
    }

    public void visitImmutable(Consumer<Object> visitor) {
        visitor.accept(snippetEncoding);
        visitor.accept(snippetNodeClasses);
        visitor.accept(graphDatas);
    }

    /**
     * Generate a String name for a method including all type information. Used as a symbolic key
     * for lookup.
     */
    public static String methodKey(ResolvedJavaMethod method) {
        return method.format("%H.%n(%P)");
    }

    StructuredGraph getEncodedSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, HotSpotReplacementsImpl replacements, Object[] args, StructuredGraph.AllowAssumptions allowAssumptions,
                    OptionValues options) {
        GraphData data = null;
        if (graphDatas != null) {
            data = graphDatas.get(methodKey(method));
        }
        if (data == null) {
            if (IS_IN_NATIVE_IMAGE) {
                throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
            } else {
                return null;
            }
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
        SymbolicEncodedGraph encodedGraph = new SymbolicEncodedGraph(snippetEncoding, startOffset, snippetObjects, snippetNodeClasses, data.originalMethod, declaringClass);
        return decodeSnippetGraph(encodedGraph, method, original, replacements, args, allowAssumptions, options, IS_IN_NATIVE_IMAGE);
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
    }

    @SuppressWarnings("try")
    static StructuredGraph decodeSnippetGraph(SymbolicEncodedGraph encodedGraph, ResolvedJavaMethod method, ResolvedJavaMethod original, HotSpotReplacementsImpl replacements, Object[] args,
                    StructuredGraph.AllowAssumptions allowAssumptions, OptionValues options, boolean mustSucceed) {
        Providers providers = replacements.getProviders();
        ParameterPlugin parameterPlugin = null;
        if (args != null) {
            MetaAccessProvider meta = HotSpotReplacementsImpl.noticeTypes(providers.getMetaAccess());
            SnippetReflectionProvider snippetReflection = replacements.snippetReflection;
            if (IS_IN_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
                snippetReflection = new LibGraalSnippetReflectionProvider(snippetReflection);
            }
            parameterPlugin = new ConstantBindingParameterPlugin(args, meta, snippetReflection);
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
                PEGraphDecoder graphDecoder = new SubstitutionGraphDecoder(providers, result, replacements, parameterPlugin, method, INLINE_AFTER_PARSING, encodedGraph, mustSucceed);
                graphDecoder.decode(method, isSubstitution, encodedGraph.trackNodeSourcePosition());
                postDecode(debug, result, original);
                assert result.verify();
                return result;
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    private static void postDecode(DebugContext debug, StructuredGraph result, ResolvedJavaMethod original) {
        debug.dump(DebugContext.VERBOSE_LEVEL, result, "Before PartialIntrinsicCallTargetNode replacement");
        for (PartialIntrinsicCallTargetNode partial : result.getNodes(PartialIntrinsicCallTargetNode.TYPE)) {
            // Ensure the orignal method matches
            assert partial.checkName(original);
            ValueNode[] arguments = partial.arguments().toArray(new ValueNode[partial.arguments().size()]);
            MethodCallTargetNode target = result.add(new MethodCallTargetNode(partial.invokeKind(), original,
                            arguments, partial.returnStamp(), null));
            partial.replaceAndDelete(target);
        }
        debug.dump(DebugContext.VERBOSE_LEVEL, result, "After decoding");
        for (ValueNode n : result.getNodes().filter(ValueNode.class)) {
            if (n instanceof ConstantNode) {
                ConstantNode constant = (ConstantNode) n;
                if (constant.asConstant() instanceof SnippetObjectConstant) {
                    throw new InternalError(n.toString(Verbosity.Debugger));
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
                        IntrinsicContext.CompilationContext context, EncodedGraph encodedGraph, boolean mustSucceed) {
            super(providers.getCodeCache().getTarget().arch, result, providers, null,
                            replacements.getGraphBuilderPlugins().getInvocationPlugins(), new InlineInvokePlugin[0], parameterPlugin,
                            null, null, null, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false);
            this.method = method;
            this.encodedGraph = encodedGraph;
            this.mustSucceed = mustSucceed;
            this.intrinsic = new IntrinsicContext(method, null, replacements.getDefaultReplacementBytecodeProvider(), context, false);
        }

        @Override
        protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod lookupMethod,
                        BytecodeProvider intrinsicBytecodeProvider,
                        boolean isSubstitution,
                        boolean trackNodeSourcePosition) {
            if (lookupMethod.equals(method)) {
                return encodedGraph;
            } else {
                throw GraalError.shouldNotReachHere(method.format("%H.%n(%p)"));
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

    static class SymbolicEncodedGraph extends EncodedGraph {

        private final ResolvedJavaType[] accessingClasses;
        private final String originalMethod;

        SymbolicEncodedGraph(byte[] encoding, int startOffset, Object[] objects, NodeClass<?>[] types, String originalMethod, ResolvedJavaType... accessingClasses) {
            super(encoding, startOffset, objects, types, null, null, false, false);
            this.accessingClasses = accessingClasses;
            this.originalMethod = originalMethod;
        }

        SymbolicEncodedGraph(EncodedGraph encodedGraph, ResolvedJavaType declaringClass, String originalMethod) {
            this(encodedGraph.getEncoding(), encodedGraph.getStartOffset(), encodedGraph.getObjects(), encodedGraph.getNodeClasses(),
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
            } else if (o instanceof GraalCapability) {
                replacement = ((GraalCapability) o).resolve(((GraalJVMCICompiler) getRuntime().getCompiler()).getGraalRuntime());
            } else {
                return o;
            }
            if (replacement != null) {
                objects[i] = o = replacement;
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

    /**
     * Symbolic reference to an object which can be retrieved from
     * {@link GraalRuntime#getCapability(Class)}.
     */
    static class GraalCapability {
        final Class<?> capabilityClass;

        GraalCapability(Class<?> capabilityClass) {
            this.capabilityClass = capabilityClass;
        }

        public Object resolve(GraalRuntime runtime) {
            Object capability = runtime.getCapability(this.capabilityClass);
            if (capability != null) {
                assert capability.getClass() == capabilityClass;
                return capability;
            }
            throw new InternalError(this.capabilityClass.getName());
        }

        @Override
        public String toString() {
            return "GraalCapability{" +
                            "capabilityClass=" + capabilityClass +
                            '}';
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
            for (ResolvedJavaMethod method : methodName.equals("<init>") ? resolvedType.getDeclaredConstructors() : resolvedType.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getSignature().toMethodDescriptor().equals(signature)) {
                    return method;
                }
            }
            throw new NoClassDefFoundError("Can't resolve " + type.getName() + " with " + accessingClass.getName());
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
