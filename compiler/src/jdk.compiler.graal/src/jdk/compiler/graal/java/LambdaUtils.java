/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.java;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.Invoke;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.IntrinsicContext;
import jdk.compiler.graal.nodes.graphbuilderconf.InvocationPlugins;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.tiers.HighTierContext;
import jdk.compiler.graal.phases.util.Providers;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class LambdaUtils {

    private static final Pattern LAMBDA_PATTERN = Pattern.compile("\\$\\$Lambda[/.][^/]+;");
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    public static final String LAMBDA_SPLIT_PATTERN = "\\$\\$Lambda";
    public static final String LAMBDA_CLASS_NAME_SUBSTRING = "$$Lambda";
    public static final String SERIALIZATION_TEST_LAMBDA_CLASS_SUBSTRING = "$$Lambda";
    public static final String SERIALIZATION_TEST_LAMBDA_CLASS_SPLIT_PATTERN = "\\$\\$Lambda";

    private static GraphBuilderConfiguration buildLambdaParserConfig(ClassInitializationPlugin cip) {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        plugins.setClassInitializationPlugin(cip);
        return GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
    }

    private LambdaUtils() {
    }

    /**
     * Creates a stable name for a lambda by hashing all the invokes in the lambda. Lambda class
     * names are typically created based on an increasing atomic counter (e.g.
     * {@code Test$$Lambda$23}). A stable name is created by replacing the substring after
     * {@code "$$Lambda$"} with a hash of the method descriptor for each method invoked by the
     * lambda.
     *
     * Starting from JDK17, the lambda classes can have additional interfaces that lambda should
     * implement. This further means that lambda can have more than one public method (public and
     * not bridge).
     *
     * The scala lambda classes have by default one additional interface with one method. This
     * method has the same signature as the original one but with generalized parameters (all
     * parameters are Object types) and serves as a wrapper that casts parameters to specialized
     * types and calls an original method.
     *
     * @param cip plugin to
     *            {@link ClassInitializationPlugin#loadReferencedType(GraphBuilderContext, jdk.vm.ci.meta.ConstantPool, int, int)
     *            load} new types
     * @param providers providers to use when processing the lambda code
     * @param lambdaType the lambda type to analyze
     * @param options options to use when analyzing the lamda code
     * @param debug debug context to nest the analysis into
     * @param ctx context to use for the
     *            {@link DebugContext#scope(java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object)}
     * @return stable name for the lambda class
     */
    @SuppressWarnings("try")
    public static String findStableLambdaName(ClassInitializationPlugin cip, Providers providers, ResolvedJavaType lambdaType, OptionValues options, DebugContext debug, Object ctx)
                    throws RuntimeException {
        ResolvedJavaMethod[] lambdaProxyMethods = Arrays.stream(lambdaType.getDeclaredMethods(false)).filter(m -> !m.isBridge() && m.isPublic()).toArray(ResolvedJavaMethod[]::new);
        /*
         * Take only the first method to build a graph, because the graph for all other methods will
         * be the same.
         */
        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(lambdaProxyMethods[0]).build();
        try (DebugContext.Scope ignored = debug.scope("Lambda target method analysis", graph, lambdaType, ctx)) {
            GraphBuilderPhase lambdaParserPhase = new LambdaGraphBuilder(LambdaUtils.buildLambdaParserConfig(cip));
            HighTierContext context = new HighTierContext(providers, null, OptimisticOptimizations.NONE);
            lambdaParserPhase.apply(graph, context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        List<ResolvedJavaMethod> invokedMethods = StreamSupport.stream(graph.getInvokes().spliterator(), false).map(Invoke::getTargetMethod).collect(Collectors.toList());
        if (invokedMethods.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Lambda without a target invoke: ").append(lambdaType.toClassName());
            for (ResolvedJavaMethod m : lambdaType.getDeclaredMethods(false)) {
                sb.append("\n  Method: ").append(m);
            }
            throw new JVMCIError(sb.toString());
        }
        return createStableLambdaName(lambdaType, invokedMethods);
    }

    public static boolean isLambdaType(ResolvedJavaType type) {
        String typeName = type.getName();
        return type.isFinalFlagSet() && isLambdaName(typeName);
    }

    public static boolean isLambdaName(String name) {
        return name.contains(LAMBDA_CLASS_NAME_SUBSTRING) && lambdaMatcher(name).find();
    }

    private static String createStableLambdaName(ResolvedJavaType lambdaType, List<ResolvedJavaMethod> targetMethods) {
        final String lambdaName = lambdaType.getName();
        assert lambdaMatcher(lambdaName).find() : "Stable name should be created for lambda types: " + lambdaName;

        Matcher m = lambdaMatcher(lambdaName);
        StringBuilder sb = new StringBuilder();
        targetMethods.forEach((targetMethod) -> sb.append(targetMethod.format("%H.%n(%P)%R")));
        return m.replaceFirst(Matcher.quoteReplacement("$$Lambda$" + digest(sb.toString()) + ";"));
    }

    private static Matcher lambdaMatcher(String value) {
        return LAMBDA_PATTERN.matcher(value);
    }

    public static String toHex(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX[(b >> 4) & 0xf]);
            r.append(HEX[b & 0xf]);
        }
        return r.toString();
    }

    public static String digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(value.getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new JVMCIError(ex);
        }
    }

    public static String capturingClass(String className) {
        return className.split(LambdaUtils.SERIALIZATION_TEST_LAMBDA_CLASS_SPLIT_PATTERN)[0];
    }

    private static final class LambdaGraphBuilder extends GraphBuilderPhase {

        private LambdaGraphBuilder(GraphBuilderConfiguration config) {
            super(config);
        }

        @Override
        protected GraphBuilderPhase.Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts,
                        IntrinsicContext initialIntrinsicContext) {
            return new Instance(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext);
        }

        private static class Instance extends GraphBuilderPhase.Instance {
            Instance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
                super(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext);
            }

            @Override
            protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
                return new LambdaBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
            }
        }
    }

    private static class LambdaBytecodeParser extends BytecodeParser {

        LambdaBytecodeParser(GraphBuilderPhase.Instance instance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            super(instance, graph, parent, method, entryBCI, intrinsicContext);
        }

        @Override
        protected Object lookupConstant(int cpi, int opcode, boolean allowBootstrapMethodInvocation) {
            /*
             * Native Image forces bootstrap method invocation at build time until support has been
             * added for doing the invocation at runtime (GR-45806)
             */
            return super.lookupConstant(cpi, opcode, true);
        }
    }
}
