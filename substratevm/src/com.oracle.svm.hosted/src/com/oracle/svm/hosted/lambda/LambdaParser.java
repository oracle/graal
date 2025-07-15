/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.lambda;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.MethodHandlePlugin;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class LambdaParser {
    public static List<Class<?>> getLambdaClassesInClass(Class<?> declaringClass, List<Class<?>> implementedInterfaces) {
        List<Class<?>> result = new ArrayList<>();
        for (Method method : declaringClass.getDeclaredMethods()) {
            result.addAll(getLambdaClassesInMethod(method, implementedInterfaces));
        }
        return result;
    }

    public static List<Class<?>> getLambdaClassesInMethod(Method capturingMethod, List<Class<?>> implementedInterfaces) {
        ResolvedJavaMethod method = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaMethod(capturingMethod);
        StructuredGraph graph = createMethodGraph(method, new OptionValues(OptionValues.newOptionMap()));
        NodeIterable<ConstantNode> constantNodes = ConstantNode.getConstantNodes(graph);
        List<Class<?>> lambdaClasses = new ArrayList<>();
        for (ConstantNode cNode : constantNodes) {
            Class<?> lambdaClass = getLambdaClassFromConstantNode(cNode);
            if (lambdaClass != null && implementedInterfaces.stream().allMatch(i -> i.isAssignableFrom(lambdaClass))) {
                lambdaClasses.add(lambdaClass);
            }
        }
        return lambdaClasses;
    }

    /**
     * Create a {@link StructuredGraph} using {@link LambdaGraphBuilderPhase.LambdaBytecodeParser},
     * a simple {@link BytecodeParser}.
     */
    @SuppressWarnings("try")
    public static StructuredGraph createMethodGraph(ResolvedJavaMethod method, OptionValues options) {
        GraphBuilderPhase lambdaParserPhase = new LambdaParser.LambdaGraphBuilderPhase();
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getName());
        DebugContext debug = new DebugContext.Builder(options, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).description(description).build();

        HighTierContext context = new HighTierContext(GraalAccess.getOriginalProviders(), null, OptimisticOptimizations.NONE);
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug)
                        .method(method)
                        .recordInlinedMethods(false)
                        .build();
        try (DebugContext.Scope ignored = debug.scope("ParsingToMaterializeLambdas")) {
            lambdaParserPhase.apply(graph, context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return graph;
    }

    public static Stream<? extends ResolvedJavaMethod> allExecutablesDeclaredInClass(ResolvedJavaType t) {
        return Stream.concat(Stream.concat(
                        Arrays.stream(t.getDeclaredMethods(false)),
                        Arrays.stream(t.getDeclaredConstructors(false))),
                        t.getClassInitializer() == null ? Stream.empty() : Stream.of(t.getClassInitializer()));
    }

    /**
     * Get the lambda class in the constant if it is a {@code DirectMethodHandle}, by getting the
     * declaring class of the {@code member} field.
     */
    public static Class<?> getLambdaClassFromConstantNode(ConstantNode constantNode) {
        Constant constant = constantNode.getValue();
        Class<?> lambdaClass = getLambdaClassFromMemberField(constant);

        if (lambdaClass == null) {
            return null;
        }

        return LambdaUtils.isLambdaClass(lambdaClass) ? lambdaClass : null;
    }

    private static Class<?> getLambdaClassFromMemberField(Constant constant) {
        ResolvedJavaType constantType = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType((JavaConstant) constant);

        if (constantType == null) {
            return null;
        }

        ResolvedJavaField[] fields = constantType.getInstanceFields(true);
        ResolvedJavaField targetField = null;
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals("member")) {
                targetField = field;
                break;
            }
        }

        if (targetField == null) {
            return null;
        }

        JavaConstant fieldValue = GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(targetField, (JavaConstant) constant);
        Member memberField = GraalAccess.getOriginalProviders().getSnippetReflection().asObject(Member.class, fieldValue);
        return memberField.getDeclaringClass();
    }

    static class LambdaGraphBuilderPhase extends GraphBuilderPhase {
        LambdaGraphBuilderPhase() {
            super(buildLambdaParserConfig());
        }

        LambdaGraphBuilderPhase(GraphBuilderConfiguration config) {
            super(config);
        }

        private static GraphBuilderConfiguration buildLambdaParserConfig() {
            GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
            plugins.setClassInitializationPlugin(new NoClassInitializationPlugin());
            plugins.prependNodePlugin(new MethodHandlePlugin(GraalAccess.getOriginalProviders().getConstantReflection().getMethodHandleAccess(), false));
            return GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
        }

        @Override
        public GraphBuilderPhase copyWithConfig(GraphBuilderConfiguration config) {
            return new LambdaGraphBuilderPhase(config);
        }

        static class LambdaBytecodeParser extends BytecodeParser {
            protected LambdaBytecodeParser(Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
                super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            }
        }

        @Override
        protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            return new Instance(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext) {
                @Override
                protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
                    return new LambdaBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
                }
            };
        }
    }
}
