/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.phases;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GraphDecoder;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.PEGraphDecoder;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.util.ClassUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Inlining before the static analysis improves the precision of the analysis especially when
 * constants are propagated. So the goal is to inline callees that are folded to constants.
 *
 * Sometimes, constant folding of callees requires quite deep inlining, when constants are
 * propagated through chains of wrapper methods. So we want to be able to look deep for potential
 * inlining. On the other hand, only very small methods can be inlined before analysis in order to
 * not bloat the type flow graph and later on machine code for infrequently executed methods. So we
 * do not want to use the regular method inlining mechanism that propagates argument constants into
 * a possibly large graph and then runs the CanonicalizerPhase. Instead, we leverage the graph
 * decoding also used by the Truffle partial evaluator. When a direct method invoke is seen during
 * decoding, we by default go into the callee. When the callee produces too many nodes (configurable
 * via the {@link InlineBeforeAnalysisPolicy}) inlining is aborted, i.e., already created nodes from
 * the callee are deleted and a non-inlined invoke is created instead.
 */
public class InlineBeforeAnalysis {

    public static class Options {
        @Option(help = "Inline methods before static analysis")//
        public static final OptionKey<Boolean> InlineBeforeAnalysis = new OptionKey<>(true);
    }

    @SuppressWarnings("try")
    public static StructuredGraph decodeGraph(PointsToAnalysis bb, AnalysisMethod method, AnalysisParsedGraph analysisParsedGraph) {
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
        DebugContext debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getProviders().getSnippetReflection())).description(description).build();

        StructuredGraph result = new StructuredGraph.Builder(bb.getOptions(), debug)
                        .method(method)
                        .recordInlinedMethods(false)
                        .trackNodeSourcePosition(analysisParsedGraph.getEncodedGraph().trackNodeSourcePosition())
                        .build();

        try (DebugContext.Scope s = debug.scope("InlineBeforeAnalysis", result)) {

            if (bb.strengthenGraalGraphs() && Options.InlineBeforeAnalysis.getValue(bb.getOptions())) {
                InlineBeforeAnalysisGraphDecoder<?> decoder = new InlineBeforeAnalysisGraphDecoder<>(bb, bb.getHostVM().inlineBeforeAnalysisPolicy(), result);
                decoder.decode(method, false, result.trackNodeSourcePosition());
            } else {
                /*
                 * No inlining, so faithfully reconstruct the encoded graph without any
                 * optimizations. We could skip the encoding in this case, but since inlining before
                 * analysis is planned to be the default it is not worth optimizing for this case.
                 */
                GraphDecoder decoder = new GraphDecoder(AnalysisParsedGraph.HOST_ARCHITECTURE, result);
                decoder.decode(analysisParsedGraph.getEncodedGraph());
            }

            debug.dump(DebugContext.BASIC_LEVEL, result, "InlineBeforeAnalysis after decode");
            return result;
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }
}

class InlineBeforeAnalysisGraphDecoder<S extends InlineBeforeAnalysisPolicy.Scope> extends PEGraphDecoder {

    class InlineBeforeAnalysisMethodScope extends PEMethodScope {

        private final S policyScope;

        protected boolean inliningAborted;

        InlineBeforeAnalysisMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method,
                        InvokeData invokeData, int inliningDepth, ValueNode[] arguments) {
            super(targetGraph, caller, callerLoopScope, encodedGraph, method, invokeData, inliningDepth, arguments);

            if (caller == null) {
                /*
                 * The root method that we are decoding, i.e., inlining into. No policy, because the
                 * whole method must of course be decoded.
                 */
                policyScope = null;
            } else if (caller.caller == null) {
                /*
                 * The first level of method inlining, i.e., the top scope from the inlining policy
                 * point of view.
                 */
                policyScope = policy.createTopScope();
                if (graph.getDebug().isLogEnabled()) {
                    graph.getDebug().logv(repeat("  ", inliningDepth) + "createTopScope for " + method.format("%H.%n(%p)") + ": " + policyScope);
                }
            } else {
                /* Nested inlining. */
                policyScope = policy.openCalleeScope((cast(caller)).policyScope);
                if (graph.getDebug().isLogEnabled()) {
                    graph.getDebug().logv(repeat("  ", inliningDepth) + "openCalleeScope for " + method.format("%H.%n(%p)") + ": " + policyScope);
                }
            }
        }
    }

    private final PointsToAnalysis bb;
    private final InlineBeforeAnalysisPolicy<S> policy;

    InlineBeforeAnalysisGraphDecoder(PointsToAnalysis bb, InlineBeforeAnalysisPolicy<S> policy, StructuredGraph graph) {
        super(AnalysisParsedGraph.HOST_ARCHITECTURE, graph, bb.getProviders(), null,
                        bb.getProviders().getGraphBuilderPlugins().getInvocationPlugins(),
                        new InlineInvokePlugin[]{new InlineBeforeAnalysisInlineInvokePlugin(policy)},
                        null, null, null, null,
                        new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true);
        this.bb = bb;
        this.policy = policy;

        if (graph.getDebug().isLogEnabled()) {
            graph.getDebug().logv("InlineBeforeAnalysis: decoding " + graph.method().format("%H.%n(%p)"));
        }
    }

    @Override
    protected PEMethodScope createMethodScope(StructuredGraph targetGraph, PEMethodScope caller, LoopScope callerLoopScope, EncodedGraph encodedGraph, ResolvedJavaMethod method, InvokeData invokeData,
                    int inliningDepth, ValueNode[] arguments) {
        return new InlineBeforeAnalysisMethodScope(targetGraph, caller, callerLoopScope, encodedGraph, method, invokeData, inliningDepth, arguments);
    }

    @Override
    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider, boolean isSubstitution, boolean trackNodeSourcePosition) {
        AnalysisMethod aMethod = (AnalysisMethod) method;
        return aMethod.ensureGraphParsed(bb).getEncodedGraph();
    }

    @Override
    protected Node addFloatingNode(MethodScope methodScope, Node node) {
        assert node.isUnregistered() : "If node is already in the graph, we would count it twice";
        maybeAbortInlining(methodScope, node);
        return super.addFloatingNode(methodScope, node);
    }

    @Override
    protected Node canonicalizeFixedNode(MethodScope methodScope, Node node) {
        Node canonical = super.canonicalizeFixedNode(methodScope, node);
        /*
         * When no canonicalization was done, we check the node that was decoded (which is already
         * alive, but we know it was just decoded and therefore not checked yet).
         *
         * When canonicalization was done, we do not check an already alive node because that node
         * was already counted when it was added.
         */
        if (canonical == node || (canonical != null && canonical.isUnregistered())) {
            maybeAbortInlining(methodScope, canonical);
        }
        return canonical;
    }

    @Override
    protected void handleNonInlinedInvoke(MethodScope methodScope, LoopScope loopScope, InvokeData invokeData) {
        maybeAbortInlining(methodScope, invokeData.callTarget);
        super.handleNonInlinedInvoke(methodScope, loopScope, invokeData);
    }

    private void maybeAbortInlining(MethodScope ms, Node node) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        if (!methodScope.inliningAborted && methodScope.isInlinedMethod()) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv(repeat("  ", methodScope.inliningDepth) + "  node " + node + ": " + methodScope.policyScope);
            }
            if (!policy.processNode(methodScope.policyScope, node)) {
                if (graph.getDebug().isLogEnabled()) {
                    graph.getDebug().logv(repeat("  ", methodScope.inliningDepth) + "    abort!");
                }
                methodScope.inliningAborted = true;
            }
        }
    }

    @Override
    protected LoopScope processNextNode(MethodScope ms, LoopScope loopScope) {
        InlineBeforeAnalysisMethodScope methodScope = cast(ms);
        if (methodScope.inliningAborted) {
            /*
             * Inlining is aborted, so stop processing this node and also clear the work-list of
             * nodes. Note that this only stops the creation of fixed nodes. Floating nodes are
             * decoded recursively starting from fixed nodes, and there is no good way to
             * immediately stop that decoding once the policy limit is exceeded.
             */
            loopScope.nodesToProcess.clear();
            return loopScope;
        }
        return super.processNextNode(methodScope, loopScope);
    }

    @Override
    protected void finishInlining(MethodScope is) {
        InlineBeforeAnalysisMethodScope inlineScope = cast(is);
        InlineBeforeAnalysisMethodScope callerScope = cast(inlineScope.caller);
        LoopScope callerLoopScope = inlineScope.callerLoopScope;
        InvokeData invokeData = inlineScope.invokeData;

        if (inlineScope.inliningAborted) {
            if (graph.getDebug().isLogEnabled()) {
                graph.getDebug().logv(repeat("  ", callerScope.inliningDepth) + "  aborted " + invokeData.callTarget.targetMethod().format("%H.%n(%p)") + ": " + inlineScope.policyScope);
            }
            if (callerScope.policyScope != null) {
                policy.abortCalleeScope(callerScope.policyScope, inlineScope.policyScope);
            }
            if (invokeData.invokePredecessor.next() != null) {
                killControlFlowNodes(inlineScope, invokeData.invokePredecessor.next());
                assert invokeData.invokePredecessor.next() == null : "Successor must have been a fixed node created in the aborted scope, which is deleted now";
            }
            invokeData.invokePredecessor.setNext(invokeData.invoke.asFixedNode());

            if (inlineScope.exceptionPlaceholderNode != null) {
                assert invokeData.invoke instanceof InvokeWithExceptionNode;
                assert lookupNode(callerLoopScope, invokeData.exceptionOrderId) == inlineScope.exceptionPlaceholderNode;
                registerNode(callerLoopScope, invokeData.exceptionOrderId, null, true, true);
                ValueNode exceptionReplacement = makeStubNode(callerScope, callerLoopScope, invokeData.exceptionOrderId);
                inlineScope.exceptionPlaceholderNode.replaceAtUsagesAndDelete(exceptionReplacement);
            }

            handleNonInlinedInvoke(callerScope, callerLoopScope, invokeData);
            return;
        }

        if (graph.getDebug().isLogEnabled()) {
            graph.getDebug().logv(repeat("  ", callerScope.inliningDepth) + "  committed " + invokeData.callTarget.targetMethod().format("%H.%n(%p)") + ": " + inlineScope.policyScope);
        }
        if (callerScope.policyScope != null) {
            policy.commitCalleeScope(callerScope.policyScope, inlineScope.policyScope);
        }
        ((AnalysisMethod) invokeData.callTarget.targetMethod()).registerAsInlined();

        super.finishInlining(inlineScope);
    }

    /* String.repeat is only available in JDK 11 and later, so need to do our own. */
    private static String repeat(String s, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(s);
        }
        return result.toString();
    }

    /**
     * Kill fixed nodes of structured control flow. Not as generic, but faster, than
     * {@link GraphUtil#killCFG}.
     *
     * We cannot kill unused floating nodes at this point, because we are still in the middle of
     * decoding caller graphs, so floating nodes of the caller that have no usage yet can get used
     * when decoding of the caller continues. Unused floating nodes are cleaned up by the next run
     * of the CanonicalizerPhase.
     */
    private void killControlFlowNodes(PEMethodScope inlineScope, FixedNode start) {
        Deque<Node> workList = null;
        Node cur = start;
        while (true) {
            assert !cur.isDeleted();
            assert graph.isNew(inlineScope.methodStartMark, cur);

            Node next = null;
            if (cur instanceof FixedWithNextNode) {
                next = ((FixedWithNextNode) cur).next();
            } else if (cur instanceof ControlSplitNode) {
                for (Node successor : cur.successors()) {
                    if (next == null) {
                        next = successor;
                    } else {
                        if (workList == null) {
                            workList = new ArrayDeque<>();
                        }
                        workList.push(successor);
                    }
                }
            } else if (cur instanceof AbstractEndNode) {
                next = ((AbstractEndNode) cur).merge();
            } else if (cur instanceof ControlSinkNode) {
                /* End of this control flow path. */
            } else {
                throw GraalError.shouldNotReachHere();
            }

            if (cur instanceof AbstractMergeNode) {
                for (ValueNode phi : ((AbstractMergeNode) cur).phis().snapshot()) {
                    phi.replaceAtUsages(null);
                    phi.safeDelete();
                }
            }

            cur.replaceAtPredecessor(null);
            cur.replaceAtUsages(null);
            cur.safeDelete();

            if (next != null) {
                cur = next;
            } else if (workList != null && !workList.isEmpty()) {
                cur = workList.pop();
            } else {
                return;
            }
        }
    }

    /**
     * The generic type of {@link InlineBeforeAnalysisPolicy} makes the policy implementation nice,
     * at the cost of this ugly cast.
     */
    @SuppressWarnings("unchecked")
    private InlineBeforeAnalysisGraphDecoder<S>.InlineBeforeAnalysisMethodScope cast(MethodScope methodScope) {
        return (InlineBeforeAnalysisMethodScope) methodScope;
    }
}

class InlineBeforeAnalysisInlineInvokePlugin implements InlineInvokePlugin {

    private final InlineBeforeAnalysisPolicy<?> policy;

    InlineBeforeAnalysisInlineInvokePlugin(InlineBeforeAnalysisPolicy<?> policy) {
        this.policy = policy;
    }

    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (policy.shouldInlineInvoke(b, method, args)) {
            return InlineInfo.createStandardInlineInfo(method);
        } else {
            return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        }
    }
}
