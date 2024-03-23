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
package jdk.graal.compiler.java;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
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
    public static final String ADDRESS_PREFIX = ".0x";

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
    public static String findStableLambdaName(ClassInitializationPlugin cip, Providers providers, ResolvedJavaType lambdaType, OptionValues options, DebugContext debug, Object ctx,
                    Function<GraphBuilderConfiguration, GraphBuilderPhase.Instance> graphBuilderSupplier)
                    throws RuntimeException {
        ResolvedJavaMethod[] lambdaProxyMethods = Arrays.stream(lambdaType.getDeclaredMethods(false)).filter(m -> !m.isBridge() && m.isPublic()).toArray(ResolvedJavaMethod[]::new);
        /*
         * Take only the first method to build a graph, because the graph for all other methods will
         * be the same.
         */
        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(lambdaProxyMethods[0]).build();
        try (DebugContext.Scope ignored = debug.scope("Lambda target method analysis", graph, lambdaType, ctx)) {
            GraphBuilderPhase.Instance lambdaParserPhase = graphBuilderSupplier.apply(buildLambdaParserConfig(cip));
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

    /**
     * Checks if the passed type is lambda class type based on set flags and the type name.
     *
     * @param type type to be checked
     * @return true if the passed type is lambda type, false otherwise
     */

    public static boolean isLambdaType(ResolvedJavaType type) {
        String typeName = type.getName();
        return type.isFinalFlagSet() && isLambdaName(typeName);
    }

    public static boolean isLambdaName(String name) {
        return isLambdaClassName(name) && lambdaMatcher(name).find();
    }

    private static String createStableLambdaName(ResolvedJavaType lambdaType, List<ResolvedJavaMethod> targetMethods) {
        final String lambdaName = lambdaType.getName();
        assert lambdaMatcher(lambdaName).find() : "Stable name should be created for lambda types: " + lambdaName;

        Matcher m = lambdaMatcher(lambdaName);
        StringBuilder sb = new StringBuilder();
        targetMethods.forEach((targetMethod) -> sb.append(targetMethod.format("%H.%n(%P)%R")));
        // Take parameter types of constructor into consideration, see GR-52837
        for (ResolvedJavaMethod ctor : lambdaType.getDeclaredConstructors()) {
            sb.append(ctor.format("%P"));
        }
        return m.replaceFirst(Matcher.quoteReplacement(LAMBDA_CLASS_NAME_SUBSTRING + ADDRESS_PREFIX + digest(sb.toString()) + ";"));
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

    /**
     * Hashing a passed string parameter using SHA-1 hashing algorithm.
     *
     * @param value string to be hashed
     * @return hexadecimal hashed value of the passed string parameter
     */
    public static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hashing a passed byte array parameter using SHA-1 hashing algorithm.
     *
     * @param bytes byte array to be hashed
     * @return hexadecimal hashed value of the passed byte array parameter
     */
    public static String digest(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(bytes);
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new JVMCIError(ex);
        }
    }

    /**
     * Extracts lambda capturing class name from the lambda class name.
     *
     * @param className name of the lambda class
     * @return name of the lambda capturing class
     */
    public static String capturingClass(String className) {
        return className.split(LambdaUtils.SERIALIZATION_TEST_LAMBDA_CLASS_SPLIT_PATTERN)[0];
    }

    /**
     * Checks if the passed class is lambda class.
     *
     * @param clazz class to be checked
     * @return true if the clazz is lambda class, false instead
     */
    public static boolean isLambdaClass(Class<?> clazz) {
        return isLambdaClassName(clazz.getName());
    }

    /**
     * Checks if the passed class name is lambda class name.
     *
     * @param className name of the class
     * @return true if the className is lambda class name, false instead
     */
    public static boolean isLambdaClassName(String className) {
        return className.contains(LAMBDA_CLASS_NAME_SUBSTRING);
    }
}
