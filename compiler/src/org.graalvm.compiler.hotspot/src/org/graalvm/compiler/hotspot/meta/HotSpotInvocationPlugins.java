/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.nodes.MacroInvokable;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extension of {@link InvocationPlugins} that disables plugins based on runtime configuration.
 */
final class HotSpotInvocationPlugins extends InvocationPlugins {
    private final HotSpotGraalRuntimeProvider graalRuntime;
    private final GraalHotSpotVMConfig config;
    private final UnimplementedGraalIntrinsics unimplementedIntrinsics;
    private Map<String, Integer> missingIntrinsicMetrics;

    public static class Options {
        @Option(help = "Print a warning when a missing intrinsic is seen.", type = OptionType.Debug) public static final OptionKey<Boolean> WarnMissingIntrinsic = new OptionKey<>(false);
    }

    /**
     * Predicates that determine which types may be intrinsified.
     */
    private final List<Predicate<ResolvedJavaType>> intrinsificationPredicates = new ArrayList<>();

    HotSpotInvocationPlugins(HotSpotGraalRuntimeProvider graalRuntime, GraalHotSpotVMConfig config, CompilerConfiguration compilerConfiguration,
                    TargetDescription target, OptionValues options) {
        this.graalRuntime = graalRuntime;
        this.config = config;
        if (Options.WarnMissingIntrinsic.getValue(options)) {
            this.unimplementedIntrinsics = new UnimplementedGraalIntrinsics(config, target.arch);
        } else {
            this.unimplementedIntrinsics = null;
        }
        this.missingIntrinsicMetrics = null;
        registerIntrinsificationPredicate(runtime().getIntrinsificationTrustPredicate(compilerConfiguration.getClass()));
    }

    @Override
    protected void register(InvocationPlugin plugin, boolean isOptional, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
        if (!config.usePopCountInstruction) {
            if (name.equals("bitCount")) {
                GraalError.guarantee(declaringClass.equals(Integer.class) || declaringClass.equals(Long.class), declaringClass.getTypeName());
                return;
            }
        }
        if (!config.useUnalignedAccesses) {
            if (name.endsWith("Unaligned") && declaringClass.getTypeName().equals("jdk.internal.misc.Unsafe")) {
                return;
            }
        }
        super.register(plugin, isOptional, allowOverwrite, declaringClass, name, argumentTypes);
    }

    @Override
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        for (Node node : newNodes) {
            if (node instanceof MacroInvokable) {
                // MacroNode based plugins can only be used for inlining since they
                // require a valid bci should they need to replace themselves with
                // an InvokeNode during lowering.
                GraalError.guarantee(plugin.inlineOnly(), "plugin that creates a %s (%s) must return true for inlineOnly(): %s", MacroInvokable.class.getSimpleName(), node, plugin);
            }
        }
        super.checkNewNodes(b, plugin, newNodes);
    }

    @Override
    public void registerIntrinsificationPredicate(Predicate<ResolvedJavaType> predicate) {
        intrinsificationPredicates.add(predicate);
    }

    @Override
    public boolean canBeIntrinsified(ResolvedJavaType declaringClass) {
        boolean ok = false;
        for (Predicate<ResolvedJavaType> p : intrinsificationPredicates) {
            ok |= p.test(declaringClass);
        }
        if (!ok) {
            if (graalRuntime.isBootstrapping()) {
                throw GraalError.shouldNotReachHere("Class declaring a method for which a Graal intrinsic is available should be trusted for intrinsification: " + declaringClass.toJavaName());
            }
            return false;
        }
        return true;
    }

    @Override
    public void notifyNoPlugin(ResolvedJavaMethod targetMethod, OptionValues options) {
        if (Options.WarnMissingIntrinsic.getValue(options)) {
            String method = String.format("%s.%s%s", targetMethod.getDeclaringClass().toJavaName().replace('.', '/'), targetMethod.getName(), targetMethod.getSignature().toMethodDescriptor());
            if (unimplementedIntrinsics.isMissing(method)) {
                int currentCount;
                synchronized (unimplementedIntrinsics) {
                    if (missingIntrinsicMetrics == null) {
                        missingIntrinsicMetrics = new HashMap<>();
                        try {
                            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                if (missingIntrinsicMetrics.size() > 0) {
                                    TTY.println("[Warning] Missing intrinsics found: %d", missingIntrinsicMetrics.size());
                                    missingIntrinsicMetrics.entrySet().stream().sorted(Comparator.comparing(Entry::getValue, Comparator.reverseOrder())).forEach(entry -> {
                                        TTY.println("        - %d occurrences during parsing: %s", entry.getValue(), entry.getKey());
                                    });
                                }
                            }));
                        } catch (IllegalStateException e) {
                            // shutdown in progress, no need to register the hook
                        }
                    }
                    currentCount = missingIntrinsicMetrics.compute(method, (key, cnt) -> cnt == null ? 1 : Math.addExact(cnt, 1));
                }
                if (currentCount == 1) {
                    TTY.println("[Warning] Missing intrinsic %s found during parsing.", method);
                }
            }
        }
    }
}
