/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.BitSet;

import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.meta.HotSpotWordOperationPlugin;
import org.graalvm.collections.EconomicSet;
import jdk.compiler.graal.api.replacements.SnippetReflectionProvider;
import jdk.compiler.graal.bytecode.Bytecode;
import jdk.compiler.graal.bytecode.BytecodeProvider;
import jdk.compiler.graal.bytecode.ResolvedJavaMethodBytecode;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.NodeSourcePosition;
import jdk.compiler.graal.hotspot.word.HotSpotOperation;
import jdk.compiler.graal.java.GraphBuilderPhase.Instance;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.LogicNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.AllowAssumptions;
import jdk.compiler.graal.nodes.extended.GuardingNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.IntrinsicContext;
import jdk.compiler.graal.nodes.graphbuilderconf.InvocationPlugin;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.nodes.spi.SnippetParameterInfo;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.util.Providers;
import jdk.compiler.graal.printer.GraalDebugHandlersFactory;
import jdk.compiler.graal.replacements.IntrinsicGraphBuilder;
import jdk.compiler.graal.replacements.ReplacementsImpl;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Filters certain method substitutions based on whether there is underlying hardware support for
 * them.
 */
public class HotSpotReplacementsImpl extends ReplacementsImpl {
    public HotSpotReplacementsImpl(HotSpotProviders providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
        super(new GraalDebugHandlersFactory(snippetReflection), providers, snippetReflection, bytecodeProvider, target);
    }

    HotSpotReplacementsImpl(HotSpotReplacementsImpl replacements, HotSpotProviders providers) {
        super(new GraalDebugHandlersFactory(replacements.snippetReflection), providers, replacements.snippetReflection,
                        replacements.getDefaultReplacementBytecodeProvider(), replacements.target);
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    public void maybeInitializeEncoder() {
        if (IS_IN_NATIVE_IMAGE) {
            return;
        }
        if (IS_BUILDING_NATIVE_IMAGE) {
            synchronized (HotSpotReplacementsImpl.class) {
                if (snippetEncoder == null) {
                    snippetEncoder = new SymbolicSnippetEncoder(this);
                }
            }
        }
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (method.getAnnotation(HotSpotOperation.class) != null) {
                return HotSpotWordOperationPlugin.class;
            }
        }
        return super.getIntrinsifyingPlugin(method);
    }

    @Override
    public void registerConditionalPlugin(InvocationPlugin plugin) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (snippetEncoder != null) {
                snippetEncoder.registerConditionalPlugin(plugin);
            }
        }
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (b.parsingIntrinsic() && snippetEncoder != null) {
                if (getIntrinsifyingPlugin(method) != null) {
                    snippetEncoder.addDelayedInvocationPluginMethod(method);
                    return;
                }
            }
        }
        super.notifyNotInlined(b, method, invoke);
    }

    public static class HotSpotIntrinsicGraphBuilder extends IntrinsicGraphBuilder {

        public HotSpotIntrinsicGraphBuilder(OptionValues options, DebugContext debug, CoreProviders providers, Bytecode code, int invokeBci, AllowAssumptions allowAssumptions) {
            super(options, debug, providers, code, invokeBci, allowAssumptions);
        }

        public HotSpotIntrinsicGraphBuilder(OptionValues options, DebugContext debug, CoreProviders providers, Bytecode code, int invokeBci, AllowAssumptions allowAssumptions,
                        GraphBuilderConfiguration graphBuilderConfig) {
            super(options, debug, providers, code, invokeBci, allowAssumptions, graphBuilderConfig);
        }

        @Override
        public GuardingNode intrinsicRangeCheck(LogicNode condition, boolean negated) {
            return HotSpotBytecodeParser.doIntrinsicRangeCheck(this, condition, negated);
        }
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options) {
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method, options);
        return plugin != null;
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod method, int invokeBci, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    AllowAssumptions allowAssumptions, OptionValues options) {
        assert invokeBci >= 0 : method;
        if (!inlineControl.allowSubstitution()) {
            return null;
        }
        StructuredGraph result;
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method, options);
        if (plugin != null) {
            Bytecode code = new ResolvedJavaMethodBytecode(method);
            try (DebugContext debug = openSnippetDebugContext("Substitution_", method, options)) {
                result = new HotSpotIntrinsicGraphBuilder(options, debug, providers, code, invokeBci, allowAssumptions).buildGraph(plugin);
            }
        } else {
            result = null;
        }
        return result;
    }

    // When assertions are enabled, these fields are used to ensure all snippets are
    // registered during Graal initialization which in turn ensures that native image
    // building will not miss any snippets.
    @NativeImageReinitialize private EconomicSet<ResolvedJavaMethod> registeredSnippets = EconomicSet.create();
    private boolean snippetRegistrationClosed;

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        assert method.isStatic() || receiver != null : "must have a constant type for the receiver";
        if (!IS_IN_NATIVE_IMAGE) {
            assert !snippetRegistrationClosed : "Cannot register snippet after registration is closed: " + method.format("%H.%n(%p)");
            if (registeredSnippets.add(method)) {
                if (IS_BUILDING_NATIVE_IMAGE) {
                    snippetEncoder.registerSnippet(method, original, receiver, trackNodeSourcePosition);
                }
            }
        }
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        if (IS_IN_NATIVE_IMAGE) {
            return getEncodedSnippets().getSnippetParameterInfo(method);
        }
        return super.getSnippetParameterInfo(method);
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        if (IS_IN_NATIVE_IMAGE) {
            return getEncodedSnippets().isSnippet(method);
        }
        return super.isSnippet(method);
    }

    @Override
    public void closeSnippetRegistration() {
        snippetRegistrationClosed = true;
    }

    public static EncodedSnippets getEncodedSnippets() {
        if (encodedSnippets == null) {
            throw GraalError.shouldNotReachHere("encoded snippets not found"); // ExcludeFromJacocoGeneratedReport
        }
        return encodedSnippets;
    }

    public static boolean snippetsAreEncoded() {
        return encodedSnippets != null;
    }

    public void clearSnippetParameterNames() {
        assert snippetEncoder != null;
        snippetEncoder.clearSnippetParameterNames();
    }

    static void setEncodedSnippets(EncodedSnippets encodedSnippets) {
        HotSpotReplacementsImpl.encodedSnippets = encodedSnippets;
    }

    public boolean encode(OptionValues options) {
        SymbolicSnippetEncoder encoder = snippetEncoder;
        if (encoder != null) {
            return encoder.encode(options);
        }
        return false;
    }

    private static volatile EncodedSnippets encodedSnippets;

    @NativeImageReinitialize private static SymbolicSnippetEncoder snippetEncoder;

    @SuppressWarnings("try")
    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        if (IS_IN_NATIVE_IMAGE) {
            // Snippets graphs can contain foreign object references and
            // outlive a single compilation.
            try (CompilationContext scope = HotSpotGraalServices.enterGlobalCompilationContext()) {
                StructuredGraph graph = getEncodedSnippets().getEncodedSnippet(method, original, this, args, AllowAssumptions.NO, options);
                if (graph == null) {
                    throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)")); // ExcludeFromJacocoGeneratedReport
                }
                return graph;
            }
        }

        assert registeredSnippets == null || registeredSnippets.contains(method) : "Asking for snippet method that was never registered: " + method.format("%H.%n(%p)");
        return super.getSnippet(method, original, args, nonNullParameters, trackNodeSourcePosition, replaceePosition, options);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getInjectedArgument(Class<T> capability) {
        if (capability.equals(GraalHotSpotVMConfig.class)) {
            return (T) getProviders().getConfig();
        }
        return super.getInjectedArgument(capability);
    }

    public ResolvedJavaMethod findSnippetMethod(ResolvedJavaMethod thisMethod) {
        if (IS_BUILDING_NATIVE_IMAGE && !IS_IN_NATIVE_IMAGE) {
            if (snippetEncoder == null) {
                throw new GraalError("findSnippetMethod called before initialization of Replacements");
            }
            return snippetEncoder.findSnippetMethod(thisMethod);
        }
        return null;
    }

    public static MetaAccessProvider noticeTypes(MetaAccessProvider metaAccess) {
        if (IS_BUILDING_NATIVE_IMAGE && !IS_IN_NATIVE_IMAGE) {
            return SymbolicSnippetEncoder.noticeTypes(metaAccess);
        }
        return metaAccess;
    }

    static boolean isGraalClass(ResolvedJavaType type) {
        return isGraalClass(type.toClassName());
    }

    static boolean isGraalClass(Class<?> clazz) {
        return isGraalClass(clazz.getName());
    }

    static boolean isGraalClass(String className) {
        String elementClassName;
        if (className.charAt(0) == '[') {
            elementClassName = className;
            while (elementClassName.charAt(0) == '[') {
                elementClassName = elementClassName.substring(1);
            }
            if (elementClassName.charAt(0) != 'L') {
                // Primitive class
                return false;
            }

            // Strip leading 'L' and trailing ';'
            elementClassName = elementClassName.substring(1, elementClassName.length() - 1);
        } else {
            elementClassName = className;
        }
        return elementClassName.startsWith("jdk.vm.ci.") ||
                        elementClassName.startsWith("jdk.compiler.graal.") ||
                        elementClassName.startsWith("org.graalvm.") ||
                        elementClassName.startsWith("com.oracle.graal.");
    }

    @Override
    protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
        return new HotSpotGraphMaker(this, substitute, original);
    }

    static class HotSpotGraphMaker extends GraphMaker {

        HotSpotGraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            super(replacements, substitute, substitutedMethod);
        }

        @Override
        protected Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            return new HotSpotGraphBuilderInstance(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

    }
}
