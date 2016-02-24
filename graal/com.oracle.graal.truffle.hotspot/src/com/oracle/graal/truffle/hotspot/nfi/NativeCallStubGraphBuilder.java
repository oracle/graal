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
package com.oracle.graal.truffle.hotspot.nfi;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.GraalCompiler;
import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.hotspot.HotSpotCompiledCodeBuilder;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.BoxNode;
import com.oracle.graal.nodes.extended.UnboxNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.replacements.ConstantBindingParameterPlugin;

/**
 * Utility creating a graph for a stub used to call a native function.
 */
public class NativeCallStubGraphBuilder {

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

    NativeCallStubGraphBuilder(HotSpotProviders providers, Backend backend, RawNativeCallNodeFactory factory) {
        this.providers = providers;
        this.backend = backend;
        this.factory = factory;

        Registration r = new Registration(providers.getGraphBuilderPlugins().getInvocationPlugins(), HotSpotNativeFunctionHandle.class);
        r.register2("call", Receiver.class, Object[].class, new CallPlugin());

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

        Suites suites = providers.getSuites().getDefaultSuites();
        LIRSuites lirSuites = providers.getSuites().getDefaultLIRSuites();

        StructuredGraph g = new StructuredGraph(callStubMethod, AllowAssumptions.NO);
        CompilationResult compResult = GraalCompiler.compileGraph(g, callStubMethod, providers, backend, graphBuilder, OptimisticOptimizations.ALL, DefaultProfilingInfo.get(TriState.UNKNOWN), suites,
                        lirSuites, new CompilationResult(), CompilationResultBuilderFactory.Default);

        try (Scope s = Debug.scope("CodeInstall", providers.getCodeCache(), g.method())) {
            HotSpotCompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(g.method(), null, compResult);
            function.code = providers.getCodeCache().addCode(g.method(), compiledCode, null, null);
        } catch (Throwable e) {
            throw Debug.handle(e);
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

            LoadIndexedNode boxedElement = b.add(new LoadIndexedNode(argArray, idx, JavaKind.Object));
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
