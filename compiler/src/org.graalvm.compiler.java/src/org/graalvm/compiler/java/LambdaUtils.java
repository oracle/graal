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
package org.graalvm.compiler.java;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

public final class LambdaUtils {
    private static final Pattern LAMBDA_PATTERN = Pattern.compile("\\$\\$Lambda\\$\\d+/\\d+");
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final GraphBuilderPhase LAMBDA_PARSER_PHASE = new GraphBuilderPhase(buildLambdaParserConfig());

    private static GraphBuilderConfiguration buildLambdaParserConfig() {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        plugins.setClassInitializationPlugin(new NoClassInitializationPlugin());
        return GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
    }

    private LambdaUtils() {
    }

    @SuppressWarnings("try")
    public static String findStableLambdaName(Providers providers, ResolvedJavaType key, OptionValues options, DebugContext debug, Object ctx) throws RuntimeException {
        ResolvedJavaMethod[] lambdaProxyMethods = Arrays.stream(key.getDeclaredMethods()).filter(m -> !m.isBridge() && m.isPublic()).toArray(ResolvedJavaMethod[]::new);
        assert lambdaProxyMethods.length == 1 : "There must be only one method calling the target.";
        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(lambdaProxyMethods[0]).build();
        try (DebugContext.Scope ignored = debug.scope("Lambda target method analysis", graph, key, ctx)) {
            HighTierContext context = new HighTierContext(providers, null, OptimisticOptimizations.NONE);
            LAMBDA_PARSER_PHASE.apply(graph, context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        Optional<Invoke> lambdaTargetInvokeOption = StreamSupport.stream(graph.getInvokes().spliterator(), false).findFirst();
        if (!lambdaTargetInvokeOption.isPresent()) {
            throw new JVMCIError("Lambda without a target invoke.");
        }
        String lambdaTargetName = LambdaUtils.createStableLambdaName(key, lambdaTargetInvokeOption.get().getTargetMethod());
        return lambdaTargetName;
    }

    public static boolean isLambdaType(ResolvedJavaType type) {
        String typeName = type.getName();
        return type.isFinalFlagSet() && typeName.contains("/") && /* isVMAnonymousClass */ typeName.contains("$$Lambda$") && /*
                                                                                                                              * shortcut
                                                                                                                              * to
                                                                                                                              * avoid
                                                                                                                              * regex
                                                                                                                              */ lambdaMatcher(type.getName()).find();
    }

    private static String createStableLambdaName(ResolvedJavaType lambdaType, ResolvedJavaMethod targetMethod) {
        assert lambdaMatcher(lambdaType.getName()).find() : "Stable name should be created only for lambda types.";
        Matcher m = lambdaMatcher(lambdaType.getName());
        String stableTargetMethod = digest(targetMethod.format("%H.%n(%P)%R"));
        return m.replaceFirst("\\$\\$Lambda\\$" + stableTargetMethod);
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
            md.update(value.getBytes("UTF-8"));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new JVMCIError(ex);
        }
    }

    private static class NoClassInitializationPlugin implements ClassInitializationPlugin {

        @Override
        public boolean supportsLazyInitialization(ConstantPool cp) {
            return true;
        }

        @Override
        public void loadReferencedType(GraphBuilderContext builder, ConstantPool cp, int cpi, int bytecode) {
        }

        @Override
        public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState, ValueNode[] classInit) {
            return false;
        }
    }

}
