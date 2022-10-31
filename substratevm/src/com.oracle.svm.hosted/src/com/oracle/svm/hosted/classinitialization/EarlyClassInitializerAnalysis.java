/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.classinitialization;

import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.GraalBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphState.GuardsStage;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.graal.thread.VMThreadLocalAccess;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.phases.EarlyConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A simple analysis of class initializers to allow early class initialization before static
 * analysis for classes with simple class initializers.
 *
 * This analysis runs before the call tree is completely built, so the scope is limited to the class
 * initializer itself, and methods that can be inlined into the class initializer. Method inlining
 * is an easy way to increase the scope. Two simple tests are used to determine if a class
 * initializer is side-effect free:
 *
 * 1) No method calls remain after parsing. This automatically precludes any virtual calls (only
 * direct calls can be inlined during parsing) and any calls to native methods.
 *
 * 2) No reads and writes of static fields apart from static fields of the class that is going to be
 * initialized. This ensures that there are no side effects. Note that we do not need to check for
 * instance fields: since no static fields are read, it is guaranteed that only instance fields of
 * newly allocated objects are accessed.
 *
 * To avoid parsing a large class initializer graph just to find out that the class cannot be
 * initialized anyway, the parsing is aborted using a
 * {@link ClassInitializerHasSideEffectsException} as soon as one of the tests fail.
 *
 * To make the analysis inter-procedural, {@link ProvenSafeClassInitializationSupport} is used when
 * a not-yet-initialized type is found. This can then lead to a recursive invocation of this early
 * class initializer analysis. To avoid infinite recursion when class initializers have cyclic
 * dependencies, the analysis bails out when a cycle is detected. As with all analysis done by
 * {@link ProvenSafeClassInitializationSupport}, there is no synchronization between threads, so the
 * same class and the same dependencies can be concurrently analyzed by multiple threads.
 */
final class EarlyClassInitializerAnalysis {

    private final ProvenSafeClassInitializationSupport classInitializationSupport;
    private final Providers originalProviders;
    private final HighTierContext context;

    EarlyClassInitializerAnalysis(ProvenSafeClassInitializationSupport classInitializationSupport) {
        this.classInitializationSupport = classInitializationSupport;

        originalProviders = GraalAccess.getOriginalProviders();
        context = new HighTierContext(originalProviders, null, OptimisticOptimizations.NONE);
    }

    @SuppressWarnings("try")
    boolean canInitializeWithoutSideEffects(Class<?> clazz, Set<Class<?>> existingAnalyzedClasses) {
        if (JavaVersionUtil.JAVA_SPEC >= 19 && Proxy.isProxyClass(clazz)) {
            /*
             * The checks below consider proxy class initialization as of JDK 19 to have side
             * effects because it accesses Class.classLoader and System.allowSecurityManager, but
             * these are not actual side effects, so we override these checks for proxy classes so
             * that they can still be initialized at build time. (GR-40009)
             */
            return true;
        }
        ResolvedJavaType type = originalProviders.getMetaAccess().lookupJavaType(clazz);
        assert type.getSuperclass() == null || type.getSuperclass().isInitialized() : "This analysis assumes that the superclass was successfully analyzed and initialized beforehand: " +
                        type.toJavaName(true);

        ResolvedJavaMethod clinit = type.getClassInitializer();
        if (clinit == null) {
            /* No class initializer, so the class can trivially be initialized. */
            return true;
        } else if (clinit.getCode() == null) {
            /*
             * Happens e.g. when linking of the class failed. Note that we really need to check for
             * getCode(), because getCodeSize() still returns a value > 0 for such methods.
             */
            return false;
        }

        Set<Class<?>> analyzedClasses = existingAnalyzedClasses;
        if (analyzedClasses == null) {
            analyzedClasses = new HashSet<>();
        } else if (analyzedClasses.contains(clazz)) {
            /* Cyclic dependency of class initializers. */
            return false;
        }
        analyzedClasses.add(clazz);

        OptionValues options = HostedOptionValues.singleton();
        DebugContext debug = new Builder(options).build();
        try (DebugContext.Scope s = debug.scope("EarlyClassInitializerAnalysis", clinit)) {
            return canInitializeWithoutSideEffects(clinit, analyzedClasses, options, debug);
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    @SuppressWarnings("try")
    private boolean canInitializeWithoutSideEffects(ResolvedJavaMethod clinit, Set<Class<?>> analyzedClasses, OptionValues options, DebugContext debug) {
        InvocationPlugins invocationPlugins = new InvocationPlugins();
        Plugins plugins = new Plugins(invocationPlugins);
        plugins.appendInlineInvokePlugin(new AbortOnRecursiveInliningPlugin());
        AbortOnUnitializedClassPlugin classInitializationPlugin = new AbortOnUnitializedClassPlugin(analyzedClasses);
        plugins.setClassInitializationPlugin(classInitializationPlugin);
        plugins.appendNodePlugin(new EarlyConstantFoldLoadFieldPlugin(originalProviders.getMetaAccess(), originalProviders.getSnippetReflection()));

        SubstrateGraphBuilderPlugins.registerClassDesiredAssertionStatusPlugin(invocationPlugins, originalProviders.getSnippetReflection());
        ReflectionPlugins.registerInvocationPlugins(classInitializationSupport.loader, originalProviders.getSnippetReflection(), null, classInitializationPlugin, invocationPlugins, null,
                        ParsingReason.EarlyClassInitializerAnalysis);

        GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);

        StructuredGraph graph = new StructuredGraph.Builder(options, debug)
                        .method(clinit)
                        .recordInlinedMethods(false)
                        .build();
        graph.getGraphState().setGuardsStage(GuardsStage.FIXED_DEOPTS);
        graph.getGraphState().setAfterStage(StageFlag.GUARD_LOWERING);
        GraphBuilderPhase.Instance builderPhase = new ClassInitializerGraphBuilderPhase(context, graphBuilderConfig, context.getOptimisticOptimizations());

        try (Graph.NodeEventScope nes = graph.trackNodeEvents(new AbortOnDisallowedNode())) {
            builderPhase.apply(graph, context);
            /*
             * If parsing is not aborted by a ClassInitializerHasSideEffectsException, it does not
             * have any side effect.
             */
            return true;

        } catch (ClassInitializerHasSideEffectsException ex) {
            return false;
        } catch (BytecodeParser.BytecodeParserError ex) {
            if (ex.getCause() instanceof ClassInitializerHasSideEffectsException) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * Parsing is aborted if any non-initialized class is encountered (apart from the class that is
     * analyzed itself).
     */
    final class AbortOnUnitializedClassPlugin extends NoClassInitializationPlugin {

        private final Set<Class<?>> analyzedClasses;

        AbortOnUnitializedClassPlugin(Set<Class<?>> analyzedClasses) {
            this.analyzedClasses = analyzedClasses;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaType type, Supplier<FrameState> frameState, ValueNode[] classInit) {
            ResolvedJavaMethod clinitMethod = b.getGraph().method();
            if (!EnsureClassInitializedNode.needsRuntimeInitialization(clinitMethod.getDeclaringClass(), type)) {
                return false;
            }
            if (classInitializationSupport.computeInitKindAndMaybeInitializeClass(ProvenSafeClassInitializationSupport.getJavaClass(type), true, analyzedClasses) != InitKind.RUN_TIME) {
                assert type.isInitialized() : "Type must be initialized now";
                return false;
            }
            throw new ClassInitializerHasSideEffectsException("Reference of class that is not initialized: " + type.toJavaName(true));
        }
    }

}

final class ClassInitializerHasSideEffectsException extends GraalBailoutException {
    private static final long serialVersionUID = 1L;

    ClassInitializerHasSideEffectsException(String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        /* Exception is used to abort parsing, stack trace is not necessary. */
        return this;
    }
}

final class AbortOnRecursiveInliningPlugin implements InlineInvokePlugin {
    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod original, ValueNode[] arguments) {
        if (b.recursiveInliningDepth(original) > 0) {
            return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        }
        if (original.getCode() == null) {
            return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        }

        /*
         * We do not restrict inlining based on method size or inlining depth. Since parsing is
         * aborted as soon as a forbidden node is added, we do not expect the graph size to grow out
         * of control.
         */
        return createStandardInlineInfo(original);
    }
}

final class AbortOnDisallowedNode extends Graph.NodeEventListener {
    @Override
    public void nodeAdded(Node node) {
        if (node instanceof Invoke) {
            throw new ClassInitializerHasSideEffectsException("Non-inlined invoke of method: " + ((Invoke) node).getTargetMethod().format("%H.%n(%p)"));

        } else if (node instanceof AccessFieldNode) {
            ResolvedJavaField field = ((AccessFieldNode) node).field();
            ResolvedJavaMethod clinit = ((StructuredGraph) node.graph()).method();
            if (field.isStatic() && !field.getDeclaringClass().equals(clinit.getDeclaringClass())) {
                throw new ClassInitializerHasSideEffectsException("Access of static field from a different class: " + field.format("%H.%n"));
            }
        } else if (node instanceof VMThreadLocalAccess) {
            throw new ClassInitializerHasSideEffectsException("Access of thread-local value");
        } else if (node instanceof UnsafeAccessNode) {
            throw VMError.shouldNotReachHere("Intrinsification of Unsafe methods is not enabled during bytecode parsing");
        }
    }
}
