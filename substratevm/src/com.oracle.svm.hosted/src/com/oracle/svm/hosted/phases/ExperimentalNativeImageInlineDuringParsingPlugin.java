/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import static com.oracle.svm.hosted.snippets.IntrinsificationPluginRegistry.CallSiteDescriptor;

import java.util.List;
import java.util.Objects;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.spi.UncheckedInterfaceProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.annotate.DeoptTest;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.NeverInlineTrivial;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.phases.AnalysisGraphBuilderPhase.AnalysisBytecodeParser;
import com.oracle.svm.hosted.phases.ExperimentalNativeImageInlineDuringParsingPlugin.CallSite;
import com.oracle.svm.hosted.phases.ExperimentalNativeImageInlineDuringParsingPlugin.InvocationResult;
import com.oracle.svm.hosted.phases.ExperimentalNativeImageInlineDuringParsingPlugin.InvocationResultInline;
import com.oracle.svm.hosted.phases.IntrinsifyMethodHandlesInvocationPlugin.IntrinsificationRegistry;
import com.oracle.svm.hosted.phases.SharedGraphBuilderPhase.SharedBytecodeParser;
import com.oracle.svm.hosted.snippets.ReflectionPlugins.ReflectionPluginRegistry;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This plugin analyses the graph for the resolved Java method and specifies what should to be
 * inlined during graph parsing before the static analysis. Plugin is searching for the methods that
 * folds to a constant.
 * <p>
 * Usage: To use this plugin, add option -H:+InlineBeforeAnalysis to the native-image.
 *
 * <h2>Structure of the ExperimentalNativeImageInlineDuringParsingPlugin</h2>
 *
 * <ul>
 *
 * <li>InvocationResultInline -- stores inline data for the currently parsed invoke (resolved Java
 * method).</li>
 *
 * <li>CallSite -- class that stores information about caller method. This information are used in
 * InvocationResultInline and saved in the dataInline map from where we read information after
 * analysis.</li>
 *
 * <li>TrivialMethodDetector -- class that analyses the callee graph and detects if it should be
 * inlined or not.</li>
 *
 * <li>MethodNodeTrackingAndInline -- innerclass of the TrivialMethodDetector that collects
 * information during graph construction to filter non-trivial methods and inline trivial invokes
 * (callees children). It is searching for the method that folds to constant, so an unlimited amount
 * of constants, parameter nodes and load filed nodes is allowed in the callees graph. On the other
 * hand, we don't want to inline if the instance node, array node, store filed node is detected, and
 * also we don't allow call target nodes and invokes that don't fold to constant. During graph
 * analysis, this class also inlines trivial invokes (callees children).</li>
 *
 * </ul>
 * <p>
 * The results of an inlining decision are placed in the
 * {@link ExperimentalNativeImageInlineDuringParsingSupport#inlineData}.
 * <p>
 * Example: Assume that we have a graph with methods R, A, B, C, where R is the root method, A calls
 * B, B calls C, and C returns the constant 1.*
 *
 * <pre>
 *       R
 *      /
 *     A
 *    /
 *   B
 *  /
 * C   <-- only returns 1
 * </pre>
 * <p>
 * We first analyze A and decide whether to enter the method for further analysis or not. In first
 * case, we proceed and analyze the nodes deeper in the graph with MethodNodeTrackingAndInline. We
 * repeat the procedure with B and C, and when the analysis of method C is complete, we decide to
 * inline it because it only returns a constant value. After that, B folds to constant, so we inline
 * this method too. Finally, for the same reason, we decide to inline A into R.
 *
 * This code is for testing purposes only and it will be replaced with a more elegant version.
 */
@SuppressWarnings("ThrowableNotThrown")
public class ExperimentalNativeImageInlineDuringParsingPlugin implements InlineInvokePlugin {

    public static class Options {
        @Option(help = "Experimental: Inline methods which folds to constant during parsing before the static analysis.")//
        public static final HostedOptionKey<Boolean> InlineBeforeAnalysis = new HostedOptionKey<>(false);

        @Option(help = "Maximum depth when inlining.")//
        public static final HostedOptionKey<Integer> InlineBeforeAnalysisMaxDepth = new HostedOptionKey<>(9);

    }

    private final ParsingReason reason;
    private final HostedProviders providers;

    public ExperimentalNativeImageInlineDuringParsingPlugin(ParsingReason reason, HostedProviders providers) {
        this.reason = reason;
        this.providers = providers;
    }

    @Override
    @SuppressWarnings("try")
    public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod callee, ValueNode[] args) {
        ResolvedJavaMethod caller = b.getMethod();
        if (inliningBeforeAnalysisNotAllowed(b, callee, caller)) {
            return null;
        }

        InvocationResult inline = null;
        CallSite callSite = new CallSite(b.getCallingContext(), toAnalysisMethod(callee));
        if (reason == ParsingReason.PointsToAnalysis) {
            DebugContext debug = b.getDebug();
            try (DebugContext.Scope ignored = debug.scope("TrivialMethodDetectorAnalysis", this);
                            AutoCloseable ignored1 = ReflectionPluginRegistry.startThreadLocalRegistry();
                            AutoCloseable ignored2 = IntrinsificationRegistry.startThreadLocalnRegistry()) {
                TrivialMethodDetector detector = new TrivialMethodDetector(providers, ((SharedBytecodeParser) b).getGraphBuilderConfig(), b.getOptions(), b.getDebug());
                InvocationResult newResult = detector.analyzeMethod(callSite, (AnalysisMethod) callee, args);
                ExperimentalNativeImageInlineDuringParsingPlugin.support().add(callSite, newResult);
                inline = newResult;
            } catch (Throwable ex) {
                debug.handle(ex);
            }
        } else {
            /*
             * When parsing for compilation, we inline method that was inlined during analysis.
             */
            inline = ExperimentalNativeImageInlineDuringParsingPlugin.support().inlineData.get(callSite);
        }

        if (inline instanceof InvocationResultInline) {
            InvocationResultInline inlineData = (InvocationResultInline) inline;
            VMError.guarantee(inlineData.callee.equals(toAnalysisMethod(callee)));

            if (reason == ParsingReason.PointsToAnalysis) {
                AnalysisMethod aMethod = (AnalysisMethod) callee;
                aMethod.registerAsImplementationInvoked(null);

                if (!aMethod.isStatic() && args[0].isConstant()) {
                    AnalysisType receiverType = (AnalysisType) StampTool.typeOrNull(args[0]);
                    if (receiverType != null) {
                        receiverType.registerAsInHeap();
                    }
                }
            }
            return InlineInfo.createStandardInlineInfo(callee);
        } else {
            return null;
        }
    }

    static boolean inliningBeforeAnalysisNotAllowed(GraphBuilderContext b, ResolvedJavaMethod callee, ResolvedJavaMethod caller) {
        return b.parsingIntrinsic() ||
                        GuardedAnnotationAccess.isAnnotationPresent(callee, NeverInline.class) || GuardedAnnotationAccess.isAnnotationPresent(callee, NeverInlineTrivial.class) ||
                        GuardedAnnotationAccess.isAnnotationPresent(callee, Uninterruptible.class) || GuardedAnnotationAccess.isAnnotationPresent(caller, Uninterruptible.class) ||
                        GuardedAnnotationAccess.isAnnotationPresent(callee, RestrictHeapAccess.class) || GuardedAnnotationAccess.isAnnotationPresent(caller, RestrictHeapAccess.class) ||
                        /*
                         * Canonicalization during inlining folds to a constant in analysis, but not
                         * for
                         * com.oracle.svm.hosted.image.NativeImageCodeCache.buildRuntimeMetadata.
                         * Either we need to re-use the analysis graphs or we have to apply the same
                         * canonicalizations for buildRuntimeMetadata.
                         */
                        GuardedAnnotationAccess.isAnnotationPresent(caller, DeoptTest.class) ||
                        b.getDepth() > ExperimentalNativeImageInlineDuringParsingPlugin.Options.InlineBeforeAnalysisMaxDepth.getValue(b.getOptions()) ||
                        isRecursiveCall(b.getCallingContext(), callee) ||
                        /*
                         * We don't want to process invokes if bci is not unique.
                         */
                        b.bciCanBeDuplicated();
    }

    public static boolean isRecursiveCall(List<Pair<ResolvedJavaMethod, Integer>> callingContext, ResolvedJavaMethod callee) {
        return callingContext.stream().map(Pair::getLeft).anyMatch(caller -> caller.equals(callee));
    }

    public static ExperimentalNativeImageInlineDuringParsingSupport support() {
        return ImageSingletons.lookup(ExperimentalNativeImageInlineDuringParsingSupport.class);
    }

    static AnalysisMethod toAnalysisMethod(ResolvedJavaMethod method) {
        if (method instanceof AnalysisMethod) {
            return (AnalysisMethod) method;
        } else {
            return ((HostedMethod) method).getWrapped();
        }
    }

    /**
     * Stores information about caller method. This information are used in
     * {@link InvocationResultInline}.
     */
    static final class CallSite extends CallSiteDescriptor {
        final AnalysisMethod callee;

        CallSite(List<Pair<ResolvedJavaMethod, Integer>> callingContext, AnalysisMethod callee) {
            super(callingContext);
            this.callee = callee;
        }

        @Override
        public int hashCode() {
            return super.hashCode() ^ callee.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && callee.equals(((CallSite) obj).callee);
        }

        @Override
        public String toString() {
            return super.toString() + callee.format("%h.%n(%p)");
        }
    }

    static class InvocationResult {
        static final InvocationResult ANALYSIS_TOO_COMPLICATED = new InvocationResult() {
            @Override
            public String toString() {
                return "Analysis to complicated.";
            }
        };
    }

    public static class InvocationResultInline extends InvocationResult {
        final CallSite callSite;
        final AnalysisMethod callee;

        InvocationResultInline(CallSite callSite, AnalysisMethod callee) {
            this.callSite = callSite;
            this.callee = callee;
        }

        @Override
        public String toString() {
            return callSite + " -> " + callee.format("%h.%n(%p)");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InvocationResultInline that = (InvocationResultInline) o;
            return callSite.equals(that.callSite) &&
                            Objects.equals(callee, that.callee);
        }

        @Override
        public int hashCode() {
            return Objects.hash(callSite, callee);
        }
    }
}

/**
 * This class detects what can be inlined.
 */
class TrivialMethodDetector {

    private final HostedProviders providers;
    private final GraphBuilderConfiguration prototypeGraphBuilderConfig;
    private final OptionValues options;
    private final DebugContext debug;

    TrivialMethodDetector(HostedProviders providers, GraphBuilderConfiguration originalGraphBuilderConfig, OptionValues options, DebugContext debug) {
        this.debug = debug;
        this.prototypeGraphBuilderConfig = makePrototypeGraphBuilderConfig(originalGraphBuilderConfig);
        this.options = options;
        this.providers = providers;
    }

    private static GraphBuilderConfiguration makePrototypeGraphBuilderConfig(GraphBuilderConfiguration originalGraphBuilderConfig) {
        GraphBuilderConfiguration result = originalGraphBuilderConfig.copy();
        result.getPlugins().clearInlineInvokePlugins();
        for (InlineInvokePlugin inlineInvokePlugin : originalGraphBuilderConfig.getPlugins().getInlineInvokePlugins()) {
            if (!(inlineInvokePlugin instanceof ExperimentalNativeImageInlineDuringParsingPlugin)) {
                result.getPlugins().appendInlineInvokePlugin(inlineInvokePlugin);
            }
        }
        return result;
    }

    @SuppressWarnings("try")
    InvocationResult analyzeMethod(CallSite callSite, AnalysisMethod method, ValueNode[] args) {
        if (!method.hasBytecodes()) {
            /* Native method. */
            return InvocationResult.ANALYSIS_TOO_COMPLICATED;
        } else if (providers.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method) != null) {
            /* Method has an invocation plugin that we must not miss. */
            return InvocationResult.ANALYSIS_TOO_COMPLICATED;
        } else if (method.isSynchronized()) {
            /*
             * Synchronization operations will always bring us above the node limit, so no point in
             * starting an analysis.
             */
            return InvocationResult.ANALYSIS_TOO_COMPLICATED;
        }

        GraphBuilderConfiguration graphBuilderConfig = prototypeGraphBuilderConfig.copy();
        graphBuilderConfig.getPlugins().appendInlineInvokePlugin(new TrivialChildrenInline(callSite));
        graphBuilderConfig.getPlugins().appendParameterPlugin(new TrivialMethodDetectorParameterPlugin(args));
        MethodNodeTracking methodNodeTracking = new MethodNodeTracking();

        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();

        try (DebugContext.Scope ignored = debug.scope("InlineDuringParsingAnalysis", graph, method, this)) {

            TrivialMethodDetectorGraphBuilderPhase builderPhase = new TrivialMethodDetectorGraphBuilderPhase(providers, graphBuilderConfig, OptimisticOptimizations.NONE, null,
                            providers.getWordTypes());

            try (NodeEventScope ignored1 = graph.trackNodeEvents(methodNodeTracking)) {
                builderPhase.apply(graph);
            }

            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "InlineDuringParsingAnalysis successful");
            return new InvocationResultInline(callSite, method);
        } catch (TrivialMethodDetectorBailoutException ex) {
            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "InlineDuringParsingAnalysis failed with %s", ex);
            /*
             * We saw not allowed node and decide to not inline that invocation.
             */
            return InvocationResult.ANALYSIS_TOO_COMPLICATED;
        } catch (Throwable ex) {
            if (Assertions.assertionsEnabled()) {
                throw debug.handle(ex);
            }
            /*
             * Whatever else happens during the analysis is non-fatal because we can just not inline
             * that invocation.
             */
            return InvocationResult.ANALYSIS_TOO_COMPLICATED;
        }
    }

    /**
     * We collect information during graph construction to filter non-trivial methods.
     */
    static class MethodNodeTracking extends NodeEventListener {

        @Override
        public void nodeAdded(Node node) {
            if (node instanceof ConstantNode) {
                /* An unlimited amount of constants is allowed. */
                /* Nothing to do, an unlimited amount of constants is allowed. We like constants. */
            } else if (node instanceof ParameterNode) {
                /* Nothing to do, an unlimited amount of parameters is allowed. */
                /* Nothing to do. */
            } else if (node instanceof ReturnNode) {
                /*
                 * Nothing to do, returning a value is fine. We don't allow control flow so there
                 * can never be more than one return.
                 */
            } else if (node instanceof LoadFieldNode) {
                /* Nothing to do, it's ok to read a static or instance field. */
            } else if (node instanceof FrameState) {
                if (((FrameState) node).bci == 0) {
                    /* Nothing to do, it's ok to have frame state for the start node. */
                } else {
                    throw new TrivialMethodDetectorBailoutException("Only frame state for the start node is allowed: " + node);
                }
            } else if (node instanceof NewArrayNode || node instanceof NewInstanceNode) {
                /*
                 * We go further in the analysis to check if any callees can be inline into this
                 * method. If there was non-constant array or object, method won't be inlined
                 * because of elimination for existing store field nodes.
                 */
            } else {
                throw new TrivialMethodDetectorBailoutException("Node not allowed: " + node);

            }
        }
    }

    /**
     * Inline trivial invokes (children).
     */
    class TrivialChildrenInline implements InlineInvokePlugin {
        private final CallSite callSite;

        TrivialChildrenInline(CallSite callSite) {
            this.callSite = callSite;
        }

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod callee, ValueNode[] args) {
            if (ExperimentalNativeImageInlineDuringParsingPlugin.inliningBeforeAnalysisNotAllowed(b, callee, b.getMethod())) {
                return null;
            }

            /*
             * In order to detect recursion it is necessary to preserve all information about
             * callers from the beginning of the analysis to the current caller. We can find this
             * information in the call site because it's callee is the current caller.
             */
            List<Pair<ResolvedJavaMethod, Integer>> callingContext = concatCallingContexts(b.getCallingContext());

            if (ExperimentalNativeImageInlineDuringParsingPlugin.isRecursiveCall(callingContext, callee)) {
                return null;
            }

            CallSite newCallSite = new CallSite(callingContext, ExperimentalNativeImageInlineDuringParsingPlugin.toAnalysisMethod(callee));
            InvocationResult inline = analyzeMethod(newCallSite, (AnalysisMethod) callee, args);

            if (inline instanceof InvocationResultInline) {
                return InlineInfo.createStandardInlineInfo(callee);
            } else {
                return null;
            }
        }

        private List<Pair<ResolvedJavaMethod, Integer>> concatCallingContexts(List<Pair<ResolvedJavaMethod, Integer>> baseContext) {
            for (int i = 0; i < callSite.getLength(); i++) {
                baseContext.add(Pair.create(callSite.getCaller()[i], callSite.getBci()[i]));
            }
            return baseContext;
        }
    }
}

class TrivialMethodDetectorBailoutException extends PermanentBailoutException {

    private static final long serialVersionUID = -1063600090362390263L;

    TrivialMethodDetectorBailoutException(String message) {
        super(message);
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     */
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }
}

class TrivialMethodDetectorGraphBuilderPhase extends AnalysisGraphBuilderPhase {

    TrivialMethodDetectorGraphBuilderPhase(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext, WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new TrivialMethodDetectorBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
    }
}

class TrivialMethodDetectorBytecodeParser extends AnalysisBytecodeParser {
    TrivialMethodDetectorBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method,
                    int entryBCI,
                    IntrinsicContext intrinsicContext) {
        super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
    }

    @Override
    protected boolean needsExplicitNullCheckException(ValueNode object) {
        if (currentBlock.exceptionDispatchBlock() != null) {
            throw new TrivialMethodDetectorBailoutException("Null check inside exception handler");
        }
        return false;
    }
}

class TrivialMethodDetectorParameterPlugin implements ParameterPlugin {

    private final ValueNode[] args;

    TrivialMethodDetectorParameterPlugin(ValueNode[] args) {
        this.args = args;
    }

    @Override
    public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
        ValueNode arg = args[index];
        Stamp argStamp = arg.stamp(NodeView.DEFAULT);
        if (arg.isConstant()) {
            return new ConstantNode(arg.asConstant(), argStamp);
        } else {
            StampPair stampPair;
            if (arg instanceof UncheckedInterfaceProvider) {
                stampPair = StampPair.create(argStamp, ((UncheckedInterfaceProvider) arg).uncheckedStamp());
            } else {
                stampPair = StampPair.createSingle(argStamp);
            }
            return new ParameterNode(index, stampPair);
        }
    }
}
