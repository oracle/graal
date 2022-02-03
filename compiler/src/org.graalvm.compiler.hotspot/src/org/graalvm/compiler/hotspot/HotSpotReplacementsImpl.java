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
package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.UseEncodedGraphs;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;

import java.util.BitSet;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotWordOperationPlugin;
import org.graalvm.compiler.hotspot.word.HotSpotOperation;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.ReplacementsImpl;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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

    public void maybeInitializeEncoder(OptionValues options) {
        if (IS_IN_NATIVE_IMAGE) {
            return;
        }
        if (IS_BUILDING_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            synchronized (HotSpotReplacementsImpl.class) {
                if (snippetEncoder == null) {
                    snippetEncoder = new SymbolicSnippetEncoder(this, options);
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
    public void registerMethodSubstitution(MethodSubstitutionPlugin plugin) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (snippetEncoder != null) {
                snippetEncoder.registerMethodSubstitution(plugin);
            }
        }
    }

    @Override
    public void registerConditionalPlugin(InvocationPlugin plugin) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (snippetEncoder != null) {
                snippetEncoder.registerConditionalPlugin(plugin);
            }
        }
    }

    public void checkRegistered(MethodSubstitutionPlugin plugin) {
        snippetEncoder.checkRegistered(plugin);
    }

    @Override
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug, AllowAssumptions allowAssumptions, Cancellable cancellable) {
        boolean useEncodedGraphs = UseEncodedGraphs.getValue(debug.getOptions());
        if (IS_IN_NATIVE_IMAGE || useEncodedGraphs) {
            HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();
            InvocationPlugin plugin = replacements.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method);
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msp = (MethodSubstitutionPlugin) plugin;
                if (!IS_IN_NATIVE_IMAGE && useEncodedGraphs) {
                    replacements.maybeInitializeEncoder(debug.getOptions());
                    replacements.registerMethodSubstitution(msp);
                }
                StructuredGraph methodSubstitution = replacements.getMethodSubstitution(msp, method, ROOT_COMPILATION, allowAssumptions, cancellable, debug.getOptions());
                methodSubstitution.resetDebug(debug);
                return methodSubstitution;
            }
        }
        return super.getIntrinsicGraph(method, compilationId, debug, allowAssumptions, cancellable);
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod targetMethod, int invokeBci, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, AllowAssumptions allowAssumptions, OptionValues options) {
        if (!inlineControl.allowSubstitution()) {
            return null;
        }
        boolean useEncodedGraphs = UseEncodedGraphs.getValue(options);
        if (IS_IN_NATIVE_IMAGE || useEncodedGraphs) {
            InvocationPlugin plugin = getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
            if (plugin instanceof MethodSubstitutionPlugin && (!plugin.inlineOnly() || invokeBci >= 0)) {
                MethodSubstitutionPlugin msPlugin = (MethodSubstitutionPlugin) plugin;
                if (!IS_IN_NATIVE_IMAGE && useEncodedGraphs) {
                    maybeInitializeEncoder(options);
                    registerMethodSubstitution(msPlugin);
                }
                // This assumes the normal path creates the graph using
                // GraphBuilderConfiguration.getSnippetDefault with omits exception edges
                StructuredGraph subst = getMethodSubstitution(msPlugin, targetMethod, INLINE_AFTER_PARSING, allowAssumptions, null, options);
                return subst;
            }
        }

        return super.getInlineSubstitution(targetMethod, invokeBci, inlineControl, trackNodeSourcePosition, replaceePosition, allowAssumptions, options);
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
            assert registeredSnippets.add(method) : "Cannot register snippet twice: " + method.format("%H.%n(%p)");
            if (IS_BUILDING_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
                snippetEncoder.registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
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
            throw GraalError.shouldNotReachHere("encoded snippets not found");
        }
        return encodedSnippets;
    }

    public static void maybeEncodeSnippets(OptionValues options) {
        if (!IS_IN_NATIVE_IMAGE && snippetEncoder != null) {
            snippetEncoder.encode(options);
        }
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
        if (IS_IN_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            maybeEncodeSnippets(options);

            // Snippets graphs can contain foreign object references and
            // outlive a single compilation.
            try (CompilationContext scope = HotSpotGraalServices.enterGlobalCompilationContext()) {
                StructuredGraph graph = getEncodedSnippets().getEncodedSnippet(method, original, this, args, AllowAssumptions.NO, options);
                if (graph == null) {
                    throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
                }
                return graph;
            }
        }

        assert registeredSnippets == null || registeredSnippets.contains(method) : "Asking for snippet method that was never registered: " + method.format("%H.%n(%p)");
        return super.getSnippet(method, original, args, nonNullParameters, trackNodeSourcePosition, replaceePosition, options);
    }

    @Override
    public StructuredGraph getMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context,
                    StructuredGraph.AllowAssumptions allowAssumptions, Cancellable cancellable, OptionValues options) {
        if (IS_IN_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            maybeEncodeSnippets(options);
            return getEncodedSnippets().getMethodSubstitutionGraph(plugin, original, this, context, allowAssumptions, cancellable, options);
        }
        return null;
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
        if (!IS_IN_NATIVE_IMAGE && snippetEncoder != null) {
            return snippetEncoder.findSnippetMethod(thisMethod);
        }
        return null;
    }

    public static MetaAccessProvider noticeTypes(MetaAccessProvider metaAccess) {
        if (!IS_IN_NATIVE_IMAGE && snippetEncoder != null) {
            return snippetEncoder.noticeTypes(metaAccess);
        }
        return metaAccess;
    }
}
