/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot.nfi;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.LateRegistration;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;

import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * Utility creating a graph for a stub used to call a native function.
 */
public class NativeCallStubGraphBuilder {

    private final OptionValues options;
    private final HotSpotProviders providers;
    private final Backend backend;
    private final RawNativeCallNodeFactory factory;

    private final ResolvedJavaMethod callStubMethod;

    private class CallPlugin implements InvocationPlugin {

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
            JavaConstant constHandle = receiver.get().asJavaConstant();
            if (constHandle != null) {
                HotSpotNativeFunctionHandle handle = providers.getSnippetReflection().asObject(HotSpotNativeFunctionHandle.class, constHandle);
                buildNativeCall(b, handle.getPointer(), arg, handle.getReturnType(), handle.getArgumentTypes());
                return true;
            } else {
                return false;
            }
        }
    }

    private static class NativeCallStub {

        @SuppressWarnings("unused")
        private static Object libCall(HotSpotNativeFunctionHandle function, Object[] args) {
            return function.call(args);
        }
    }

    NativeCallStubGraphBuilder(OptionValues options, HotSpotProviders providers, Backend backend, RawNativeCallNodeFactory factory) {
        this.options = options;
        this.providers = providers;
        this.backend = backend;
        this.factory = factory;

        InvocationPlugins plugins = providers.getGraphBuilderPlugins().getInvocationPlugins();
        try (LateRegistration r = new LateRegistration(plugins, HotSpotNativeFunctionHandle.class)) {
            r.register(new CallPlugin(), "call", Receiver.class, Object[].class);
        }

        ResolvedJavaType stubClass = providers.getMetaAccess().lookupJavaType(NativeCallStub.class);
        ResolvedJavaMethod[] methods = stubClass.getDeclaredMethods();
        assert methods.length == 1 && methods[0].getName().equals("libCall");
        this.callStubMethod = methods[0];
    }

    /**
     * Creates and installs a stub for calling a native function.
     */
    @SuppressWarnings("try")
    void installNativeFunctionStub(HotSpotNativeFunctionHandle function) {
        Plugins plugins = new Plugins(providers.getGraphBuilderPlugins());
        plugins.prependParameterPlugin(new ConstantBindingParameterPlugin(new Object[]{function, null}, providers.getMetaAccess(), providers.getSnippetReflection()));

        PhaseSuite<HighTierContext> graphBuilder = new PhaseSuite<>();
        graphBuilder.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(plugins)));

        Suites suites = providers.getSuites().getDefaultSuites(options);
        LIRSuites lirSuites = providers.getSuites().getDefaultLIRSuites(options);

        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        DebugContext debug = DebugContext.create(options, snippetReflection);
        StructuredGraph g = new StructuredGraph.Builder(options, debug).method(callStubMethod).compilationId(backend.getCompilationIdentifier(callStubMethod)).build();
        CompilationResult compResult = GraalCompiler.compileGraph(g, callStubMethod, providers, backend, graphBuilder, OptimisticOptimizations.ALL, DefaultProfilingInfo.get(TriState.UNKNOWN), suites,
                        lirSuites, new CompilationResult(), CompilationResultBuilderFactory.Default);

        HotSpotCodeCacheProvider codeCache = providers.getCodeCache();
        try (DebugContext.Scope s = debug.scope("CodeInstall", codeCache, g.method(), compResult);
                        DebugContext.Activation a = debug.activate()) {
            HotSpotCompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(codeCache, g.method(), null, compResult);
            function.code = codeCache.addCode(g.method(), compiledCode, null, null);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private void buildNativeCall(GraphBuilderContext b, HotSpotNativeFunctionPointer functionPointer, ValueNode argArray, Class<?> returnType, Class<?>... argumentTypes) {
        JavaConstant functionPointerNode = JavaConstant.forLong(functionPointer.getRawValue());
        ValueNode[] arguments = getParameters(b, argArray, argumentTypes);

        FixedWithNextNode callNode = b.add(factory.createRawCallNode(getKind(returnType), functionPointerNode, arguments));

        // box result
        if (callNode.getStackKind() != JavaKind.Void) {
            if (callNode.getStackKind() == JavaKind.Object) {
                throw new IllegalArgumentException("Return type not supported: " + returnType.getName());
            }
            ResolvedJavaType type = b.getMetaAccess().lookupJavaType(callNode.getStackKind().toBoxedJavaClass());
            b.addPush(JavaKind.Object, new BoxNode(callNode, type, callNode.getStackKind()));
        } else {
            ValueNode zero = b.add(ConstantNode.forLong(0));
            b.addPush(JavaKind.Object, new BoxNode(zero, b.getMetaAccess().lookupJavaType(Long.class), JavaKind.Long));
        }
    }

    private static ValueNode[] getParameters(GraphBuilderContext b, ValueNode argArray, Class<?>[] argumentTypes) {
        ValueNode[] ret = new ValueNode[argumentTypes.length];
        for (int i = 0; i < argumentTypes.length; i++) {
            // load boxed array element:
            ValueNode idx = b.add(ConstantNode.forInt(i));
            Class<?> type = argumentTypes[i];
            JavaKind kind = getKind(type);

            LoadIndexedNode boxedElement = b.add(new LoadIndexedNode(b.getAssumptions(), argArray, idx, JavaKind.Object));
            if (kind == JavaKind.Object) {
                // array value
                JavaKind arrayElementKind = getElementKind(type);
                int displacement = getArrayBaseOffset(arrayElementKind);
                ValueNode dispNode = b.add(ConstantNode.forLong(displacement));
                ret[i] = b.add(new OffsetAddressNode(boxedElement, dispNode));
            } else {
                // boxed primitive value
                ret[i] = b.add(new UnboxNode(boxedElement, kind));
            }
        }
        return ret;
    }

    public static JavaKind getElementKind(Class<?> clazz) {
        Class<?> componentType = clazz.getComponentType();
        if (componentType == null) {
            throw new IllegalArgumentException("Parameter type not supported: " + clazz);
        }
        if (componentType.isPrimitive()) {
            return JavaKind.fromJavaClass(componentType);
        }
        throw new IllegalArgumentException("Parameter type not supported: " + clazz);
    }

    private static JavaKind getKind(Class<?> clazz) {
        if (clazz == int.class || clazz == Integer.class) {
            return JavaKind.Int;
        } else if (clazz == long.class || clazz == Long.class) {
            return JavaKind.Long;
        } else if (clazz == char.class || clazz == Character.class) {
            return JavaKind.Char;
        } else if (clazz == byte.class || clazz == Byte.class) {
            return JavaKind.Byte;
        } else if (clazz == float.class || clazz == Float.class) {
            return JavaKind.Float;
        } else if (clazz == double.class || clazz == Double.class) {
            return JavaKind.Double;
        } else if (clazz == int[].class || clazz == long[].class || clazz == char[].class || clazz == byte[].class || clazz == float[].class || clazz == double[].class) {
            return JavaKind.Object;
        } else if (clazz == void.class) {
            return JavaKind.Void;
        } else {
            throw new IllegalArgumentException("Type not supported: " + clazz);
        }
    }
}
