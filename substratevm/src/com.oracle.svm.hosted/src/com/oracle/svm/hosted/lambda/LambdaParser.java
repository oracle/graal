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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.Bytecodes;
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
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.MethodHandlePlugin;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool.BootstrapMethodInvocation;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class LambdaParser {
    private static final ConcurrentHashMap<Class<?>, CaptureSites> captureSitesByCapturingClass = new ConcurrentHashMap<>();

    public static List<Class<?>> getLambdaClassesInClass(Class<?> declaringClass, List<Class<?>> implementedInterfaces) {
        List<Class<?>> result = new ArrayList<>();
        for (Method method : declaringClass.getDeclaredMethods()) {
            result.addAll(getLambdaClassesInMethod(method, implementedInterfaces));
        }
        return result;
    }

    public static List<Class<?>> getLambdaClassesInMethod(Method capturingMethod, List<Class<?>> implementedInterfaces) {
        ResolvedJavaMethod method = GuestAccess.get().getProviders().getMetaAccess().lookupJavaMethod(capturingMethod);
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
    public static StructuredGraph createMethodGraph(ResolvedJavaMethod method, OptionValues options) {
        GraphBuilderPhase lambdaParserPhase = new LambdaParser.LambdaGraphBuilderPhase();
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getName());
        DebugContext debug = new DebugContext.Builder(options, new GraalDebugHandlersFactory(GuestAccess.get().getSnippetReflection())).description(description).build();

        HighTierContext context = new HighTierContext(GuestAccess.get().getProviders(), null, OptimisticOptimizations.NONE);
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug)
                        .method(method)
                        .recordInlinedMethods(false)
                        .build();
        try (DebugContext.Scope _ = debug.scope("ParsingToMaterializeLambdas")) {
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
     * Get the lambda class represented by the constant, if it can be identified from its captured
     * method handle or receiver.
     */
    public static Class<?> getLambdaClassFromConstantNode(ConstantNode constantNode) {
        Constant constant = constantNode.getValue();
        Class<?> lambdaClass = getLambdaClass((JavaConstant) constant);

        if (lambdaClass == null) {
            return null;
        }

        return LambdaUtils.isLambdaClass(lambdaClass) ? lambdaClass : null;
    }

    private static Class<?> getLambdaClass(JavaConstant javaConstant) {
        Providers providers = GuestAccess.get().getProviders();
        ResolvedJavaType constantType = providers.getMetaAccess().lookupJavaType(javaConstant);

        if (constantType == null) {
            return null;
        }

        ResolvedJavaField[] fields = constantType.getInstanceFields(true);
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals("member")) {
                JavaConstant fieldValue = providers.getConstantReflection().readFieldValue(field, javaConstant);
                Member memberField = providers.getSnippetReflection().asObject(Member.class, fieldValue);
                if (memberField != null) {
                    return memberField.getDeclaringClass();
                }
            } else if (field.getName().equals("argL0")) {
                JavaConstant fieldValue = providers.getConstantReflection().readFieldValue(field, javaConstant);
                Object argL0 = providers.getSnippetReflection().asObject(Object.class, fieldValue);
                if (argL0 != null) {
                    return argL0.getClass();
                }
            }
        }
        return null;
    }

    public static String findLambdaCaptureSite(Class<?> lambdaClass) {
        if (!LambdaUtils.isLambdaClass(lambdaClass)) {
            throw VMError.shouldNotReachHere("Expected a lambda class: " + lambdaClass.getName());
        }
        try {
            Class<?> capturingClass = Class.forName(LambdaUtils.capturingClass(lambdaClass.getName()), false, lambdaClass.getClassLoader());
            return captureSitesByCapturingClass.computeIfAbsent(capturingClass, LambdaParser::findLambdaCaptureSites).lookup(lambdaClass);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("Could not load capturing class for lambda class " + lambdaClass.getName(), e);
        }
    }

    public static Class<?> findLambdaClassForCaptureSite(Class<?> capturingClass, String captureSite) {
        return captureSitesByCapturingClass.computeIfAbsent(capturingClass, LambdaParser::findLambdaCaptureSites).lookupLambdaClass(captureSite);
    }

    private static CaptureSites findLambdaCaptureSites(Class<?> capturingClass) {
        ResolvedJavaType capturingType = GuestAccess.get().getProviders().getMetaAccess().lookupJavaType(capturingClass);
        try {
            capturingType.link();
        } catch (Throwable e) {
            /*
             * The types are linked on AnalysisType construction anyway, so it can be linked here.
             * Any exception is ignored for the same reason as in AnalysisType.
             */
        }

        Map<String, BootstrapMethodInvocation> invocationsBySite = new HashMap<>();
        for (ResolvedJavaMethod method : allExecutablesDeclaredInClass(capturingType).filter(m -> m.getCode() != null).toList()) {
            for (BytecodeStream stream = new BytecodeStream(method.getCode()); stream.currentBCI() < stream.endBCI(); stream.next()) {
                if (stream.currentBC() != Bytecodes.INVOKEDYNAMIC) {
                    continue;
                }

                int bci = stream.currentBCI();
                String captureSite = method.format("%H.%n(%P)%R") + "@" + bci;
                BootstrapMethodInvocation bootstrapInvocation;
                int cpi = stream.readCPI4();
                try {
                    bootstrapInvocation = method.getConstantPool().lookupBootstrapMethodInvocation(cpi, Bytecodes.INVOKEDYNAMIC);
                } catch (LinkageError e) {
                    /*
                     * An unresolved site cannot identify a lambda. If it is needed later, the
                     * lookup will fail with a diagnostic that includes the requested site.
                     */
                    continue;
                }
                if (bootstrapInvocation == null) {
                    throw VMError.shouldNotReachHere("Missing bootstrap method invocation for " + method.format("%H.%n(%P)%R") + "@" + bci);
                }

                invocationsBySite.put(captureSite, bootstrapInvocation);
            }
        }

        return new CaptureSites(invocationsBySite);
    }

    private record CaptureSites(Map<String, BootstrapMethodInvocation> invocationsBySite, ConcurrentHashMap<Class<?>, String> sitesByLambdaClass) {
        CaptureSites(Map<String, BootstrapMethodInvocation> invocationsBySite) {
            this(invocationsBySite, new ConcurrentHashMap<>());
        }

        String lookup(Class<?> lambdaClass) {
            String cachedCaptureSite = sitesByLambdaClass.get(lambdaClass);
            if (cachedCaptureSite != null) {
                return cachedCaptureSite;
            }

            String captureSite = null;
            for (Map.Entry<String, BootstrapMethodInvocation> entry : invocationsBySite.entrySet()) {
                String currentCaptureSite = entry.getKey();
                JavaConstant bootstrapConstant = entry.getValue().lookup();
                Class<?> foundLambdaClass = bootstrapConstant == null ? null : getLambdaClass(bootstrapConstant);
                if (foundLambdaClass == null || !LambdaUtils.isLambdaClass(foundLambdaClass)) {
                    continue;
                }
                String previousCaptureSite = sitesByLambdaClass.putIfAbsent(foundLambdaClass, currentCaptureSite);
                if (previousCaptureSite != null && !previousCaptureSite.equals(currentCaptureSite)) {
                    throw VMError.shouldNotReachHere("Multiple capture sites found for lambda class " + lambdaClass.getName() + ": " + previousCaptureSite + " and " + currentCaptureSite);
                }
                if (!lambdaClass.getName().equals(foundLambdaClass.getName())) {
                    continue;
                }
                captureSite = currentCaptureSite;
            }
            VMError.guarantee(captureSite != null, "Could not find capture site for lambda class %s", lambdaClass.getName());
            return captureSite;
        }

        Class<?> lookupLambdaClass(String captureSite) {
            BootstrapMethodInvocation bootstrapInvocation = invocationsBySite.get(captureSite);
            VMError.guarantee(bootstrapInvocation != null, "Persisted bootstrap method invocation cannot be linked in the extension layer: %s", captureSite);
            bootstrapInvocation.resolve();

            JavaConstant bootstrapConstant = bootstrapInvocation.lookup();
            VMError.guarantee(bootstrapConstant != null, "Could not resolve bootstrap method for capture site %s", captureSite);

            Class<?> lambdaClass = getLambdaClass(bootstrapConstant);
            VMError.guarantee(lambdaClass != null && LambdaUtils.isLambdaClass(lambdaClass), "Could not resolve lambda class for capture site %s", captureSite);
            sitesByLambdaClass.put(lambdaClass, captureSite);
            return lambdaClass;
        }
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
            plugins.prependNodePlugin(new MethodHandlePlugin(GuestAccess.get().getProviders().getConstantReflection().getMethodHandleAccess(), false));
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
