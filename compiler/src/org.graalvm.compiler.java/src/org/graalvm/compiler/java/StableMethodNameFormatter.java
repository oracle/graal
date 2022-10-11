/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.java.LambdaUtils.digest;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Formats method names so that different compilations of a method can be correlated. If the method
 * is a lambda, creates a stable name for the lambda by hashing all the invokes in the lambda
 * similarly to {@link LambdaUtils}.
 */
public class StableMethodNameFormatter implements Function<ResolvedJavaMethod, String> {
    /**
     * A pattern that matches the unstable part of the name of a lambda method that is replaced.
     */
    private static final Pattern LAMBDA_METHOD_PATTERN = Pattern.compile("\\$\\$Lambda\\$\\d+/0x[0-9a-f]+");

    /**
     * The part of a lambda method classname that is kept in the stable method name, so that it is
     * still clear that it is a lambda method.
     */
    private static final String LAMBDA_PREFIX = "$$Lambda$";

    /**
     * The format of the methods passed to {@link ResolvedJavaMethod#format(String)}.
     */
    private static final String METHOD_FORMAT = "%H.%n(%p)";

    /**
     * The format of the invoked methods passed to {@link ResolvedJavaMethod#format(String)}, which
     * is {@link LambdaUtils#digest hashed} later.
     */
    private static final String INVOKED_METHOD_FORMAT = "%H.%n(%P)%R";

    private final StructuredGraph graph;

    private final Providers providers;

    private GraphBuilderPhase graphBuilderPhase;

    /**
     * Cached stable method names.
     */
    private final EconomicMap<ResolvedJavaMethod, String> methodName = EconomicMap.create(Equivalence.IDENTITY);

    public StableMethodNameFormatter(StructuredGraph graph, Providers providers) {
        this.graph = graph;
        this.providers = providers;
    }

    /**
     * Returns a stable method name. If the argument is not a lambda,
     * {@link ResolvedJavaMethod#format(String) the formatted method name} can be taken directly. If
     * the argument is a lambda, then the numbers just after the substring {@code $$Lambda$} are
     * replaced with a hash of invokes similarly to {@link LambdaUtils}. Results are cached
     * (compared by identity of the method).
     *
     * @param method the method to be formatted
     * @return a stable method name.
     */
    @Override
    public String apply(ResolvedJavaMethod method) {
        String result = methodName.get(method);
        if (result != null) {
            return result;
        }
        result = findMethodName(method);
        methodName.put(method, result);
        return result;
    }

    /**
     * Find a stable method for a method that could be lambda (without using the cache).
     *
     * @see #apply(ResolvedJavaMethod)
     * @param method the method to be formatted
     * @return a stable method name
     */
    private String findMethodName(ResolvedJavaMethod method) {
        if (LambdaUtils.isLambdaType(method.getDeclaringClass())) {
            return findStableLambdaMethodName(method);
        }
        return method.format(METHOD_FORMAT);
    }

    private GraphBuilderPhase getGraphBuilderPhase() {
        if (graphBuilderPhase != null) {
            return graphBuilderPhase;
        }
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        GraphBuilderConfiguration builderConfiguration = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
        graphBuilderPhase = new GraphBuilderPhase(builderConfiguration);
        return graphBuilderPhase;
    }

    /**
     * Find a stable method name for a lambda method by replacing the numbers just after the
     * substring {@code $$Lambda$} with a hash of invokes similarly to {@link LambdaUtils}.
     *
     * @param method a lambda method to be formatted
     * @return a stable method name
     */
    @SuppressWarnings("try")
    private String findStableLambdaMethodName(ResolvedJavaMethod method) {
        StructuredGraph methodGraph = new StructuredGraph.Builder(graph.getOptions(), graph.getDebug()).method(method).build();
        try (DebugContext.Scope ignored = graph.getDebug().scope("Lambda method analysis", methodGraph, method, this)) {
            HighTierContext context = new HighTierContext(providers, null, OptimisticOptimizations.NONE);
            getGraphBuilderPhase().apply(methodGraph, context);
        } catch (Throwable e) {
            throw graph.getDebug().handle(e);
        }
        List<ResolvedJavaMethod> invokedMethods = StreamSupport.stream(methodGraph.getInvokes().spliterator(), false).map(Invoke::getTargetMethod).collect(Collectors.toList());
        String lambdaName = method.format(METHOD_FORMAT);
        Matcher matcher = LAMBDA_METHOD_PATTERN.matcher(lambdaName);
        StringBuilder sb = new StringBuilder();
        invokedMethods.forEach((targetMethod) -> sb.append(targetMethod.format(INVOKED_METHOD_FORMAT)));
        return matcher.replaceFirst(Matcher.quoteReplacement(LAMBDA_PREFIX + digest(sb.toString())));
    }
}
