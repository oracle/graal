/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.UninterruptibleAnnotationUtils;
import com.oracle.svm.core.pgo.ProfileFrame;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ProfilingUtilities {

    /** FNV-1a (64-bit) offset basis and prime, for {@link #conditionFingerprint}. */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Levels of the condition's inputs that {@link #conditionFingerprint} hashes. */
    private static final int FINGERPRINT_DEPTH = 3;

    /** One shared descriptor string per method, see {@link #methodDescriptor}. */
    private static final ConcurrentHashMap<ResolvedJavaMethod, String> METHOD_DESCRIPTORS = new ConcurrentHashMap<>();

    /** One shared frame per method and BCI, see {@link #context}. */
    private static final ConcurrentHashMap<ProfileFrame, ProfileFrame> FRAMES = new ConcurrentHashMap<>();

    public static EconomicMap<NodeSourcePosition, List<ControlSplitNode>> relevantConditionalNodesFromGraph(StructuredGraph graph) {
        return chooseConditionalNodesToInstrument(
                        getConditionalNodesFromGraph(graph).filter(n -> isNotForImplicitException((ControlSplitNode) n)).filter(n -> !hasUnknownBci(n)));
    }

    public static boolean isNotForImplicitException(ControlSplitNode conditionalNode) {
        for (Node successor : conditionalNode.successors().snapshot()) {
            if (((AbstractBeginNode) successor).next() instanceof BytecodeExceptionNode) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnknownBci(Node node) {
        for (NodeSourcePosition pos = node.getNodeSourcePosition(); pos != null; pos = pos.getCaller()) {
            if (pos.getBCI() == BytecodeFrame.UNKNOWN_BCI || pos.getBCI() == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
                return true;
            }
        }
        return false;
    }

    /**
     * Input graphs contain multiple {@link IfNode}s with the same position. This method selects the
     * most important nodes to instrument for each position. Conditionals without injected
     * probabilities are prioritized over conditionals without injected probabilities.
     *
     * @param candidateConditionalNodes candidate nodes for instrumentation.
     * @return map of position to the chosen nodes to instrument.
     */
    private static EconomicMap<NodeSourcePosition, List<ControlSplitNode>> chooseConditionalNodesToInstrument(NodeIterable<Node> candidateConditionalNodes) {
        /* choose the best candidates */
        EconomicMap<NodeSourcePosition, List<ControlSplitNode>> nodesToInstrument = EconomicMap.create();
        candidateConditionalNodes.forEach(node -> {
            NodeSourcePosition position = node.getNodeSourcePosition();
            if (position == null) {
                return;
            }
            if (nodesToInstrument.containsKey(position)) {
                List<ControlSplitNode> existingNodes = nodesToInstrument.get(position);
                nodesToInstrument.put(position, chooseRelevantConditionalNodes((ControlSplitNode) node, existingNodes));
            } else {
                List<ControlSplitNode> relevantNodes = new ArrayList<>();
                relevantNodes.add((ControlSplitNode) node);
                nodesToInstrument.put(position, relevantNodes);
            }
        });
        return nodesToInstrument;
    }

    /**
     * If two {@link IfNode}s have the same source position we need to decide which nodes to
     * instrument. We always choose the ones with probability 0.5. The rationale is that the node
     * that was injected probably should not be instrumented.
     */
    private static List<ControlSplitNode> chooseRelevantConditionalNodes(ControlSplitNode node, List<ControlSplitNode> nodes) {
        assert node.getNodeSourcePosition().equals(nodes.get(0).getNodeSourcePosition()) : "This method distinguishes between nodes with the same source position.";
        if (hasNonDefaultProbability(node)) {
            return nodes;
        }
        if (hasNonDefaultProbability(nodes.get(0))) {
            nodes.remove(0);
        }
        nodes.add(node);
        return nodes;
    }

    public static NodeIterable<Node> getConditionalNodesFromGraph(StructuredGraph graph) {
        return graph.getNodes().filter(n -> n instanceof IfNode || n instanceof SwitchNode);
    }

    /** A conditional and its calling context, innermost frame first. */
    public record ConditionalSite(ControlSplitNode node, NodeSourcePosition context) {
    }

    /**
     * Groups every candidate conditional by source position without the probability-based pruning
     * of {@link #relevantConditionalNodesFromGraph}. All physical copies are kept because the
     * profile entry of a branch is the sum over them: after loop transformations the bytecode
     * branch of a loop header exists as a peeled guard (default probability) and as the in-loop
     * exit condition (compiler-assigned probability); pruning would drop the copy that carries the
     * iteration count.
     */
    private static EconomicMap<NodeSourcePosition, List<ControlSplitNode>> allConditionalNodesFromGraph(StructuredGraph graph) {
        EconomicMap<NodeSourcePosition, List<ControlSplitNode>> groups = EconomicMap.create();
        getConditionalNodesFromGraph(graph).filter(n -> isNotForImplicitException((ControlSplitNode) n)).filter(n -> !hasUnknownBci(n)).forEach(node -> {
            NodeSourcePosition position = node.getNodeSourcePosition();
            if (position != null) {
                List<ControlSplitNode> nodes = groups.get(position);
                if (nodes == null) {
                    nodes = new ArrayList<>();
                    groups.put(position, nodes);
                }
                nodes.add((ControlSplitNode) node);
            }
        });
        return groups;
    }

    /**
     * Enumerates the conditionals that {@link BranchProfileInstrumentationPhase} instruments, in
     * deterministic graph iteration order. {@code inliningContext}, when present, is appended as
     * the caller chain of every site.
     */
    public static List<ConditionalSite> relevantConditionalSitesFromGraph(StructuredGraph graph, NodeSourcePosition inliningContext) {
        EconomicMap<NodeSourcePosition, List<ControlSplitNode>> groups = allConditionalNodesFromGraph(graph);
        List<ConditionalSite> result = new ArrayList<>();
        for (List<ControlSplitNode> group : groups.getValues()) {
            for (ControlSplitNode node : group) {
                NodeSourcePosition context = node.getNodeSourcePosition();
                if (inliningContext != null) {
                    context = context.addCaller(inliningContext);
                }
                result.add(new ConditionalSite(node, context));
            }
        }
        return result;
    }

    /** The class of the tested condition, or of the split node itself for a switch. */
    public static String conditionKind(ControlSplitNode node) {
        return node instanceof IfNode ifNode ? ifNode.condition().getClass().getName() : node.getClass().getName();
    }

    /**
     * The calling context of a node's source position as method descriptors and BCIs, innermost
     * frame first. Part of {@link #conditionFingerprint}.
     */
    private static String canonicalContext(NodeSourcePosition position) {
        StringBuilder result = new StringBuilder();
        for (NodeSourcePosition frame = position; frame != null; frame = frame.getCaller()) {
            if (!result.isEmpty()) {
                result.append('<');
            }
            result.append(methodDescriptor(frame.getMethod())).append(':').append(frame.getBCI());
        }
        return result.toString();
    }

    /**
     * Identifies the condition a branch tests. The profile writer sums all physical copies of a
     * bytecode branch into one entry. Copies whose successors have no bytecode position (an inlined
     * intrinsic or substitution, an exception edge) cannot be told apart by their successors, so
     * the writer sums them only if their {@link #conditionKind} and fingerprint are equal (see
     * {@code IprofWriter.branchEntries}).
     * <p>
     * The fingerprint is an FNV-1a hash over the node classes and source positions of the
     * condition and of its inputs, {@link #FINGERPRINT_DEPTH} levels deep. Three levels cover a
     * comparison, its operands and their inputs, and bound the cost, which grows with every level
     * of the input tree.
     */
    public static long conditionFingerprint(ControlSplitNode node) {
        long hash = fingerprintText(FNV_OFFSET_BASIS, node.getClass().getName());
        if (node instanceof IfNode ifNode) {
            hash = fingerprintNode(hash, ifNode.condition(), FINGERPRINT_DEPTH);
        }
        return hash;
    }

    /** Adds the class and source position of {@code node}, then of its inputs up to {@code depth} more levels. */
    private static long fingerprintNode(long hash, Node node, int depth) {
        long result = fingerprintText(hash, node.getClass().getName());
        NodeSourcePosition position = node.getNodeSourcePosition();
        if (position != null) {
            result = fingerprintText(result, canonicalContext(position));
        }
        if (depth > 0) {
            for (Node input : node.inputs()) {
                result = fingerprintNode(result, input, depth - 1);
            }
        }
        return result;
    }

    /** Adds each character of {@code value} to an FNV-1a hash. */
    private static long fingerprintText(long hash, String value) {
        long result = hash;
        for (int i = 0; i < value.length(); i++) {
            result ^= value.charAt(i);
            result *= FNV_PRIME;
        }
        return result;
    }

    /**
     * Canonical identity of a resolved method in a profile's calling contexts:
     * {@code <declaringClassDescriptor>.<name><methodDescriptor>}, for example
     * {@code Ljava/lang/String;.indexOf(I)I}. JVMCI renders the declaring class and the signature
     * in JVM descriptor form ({@link jdk.vm.ci.meta.JavaType#getName()},
     * {@link jdk.vm.ci.meta.Signature#toMethodDescriptor()}).
     */
    public static String methodDescriptor(ResolvedJavaMethod method) {
        if (method == null) {
            return "";
        }
        /*
         * Every instrumented site stores its whole calling context as descriptors, so the same
         * method is rendered for millions of frames. Share one string per method; otherwise the
         * duplicates fill the builder heap and the image heap.
         */
        return METHOD_DESCRIPTORS.computeIfAbsent(method, m -> m.getDeclaringClass().getName() + "." + m.getName() + m.getSignature().toMethodDescriptor());
    }

    /**
     * Whether {@code method} is Native Image runtime code, which is never instrumented:
     * <ul>
     * <li>Uninterruptible methods. They run where the thread's counter tables may not exist yet or
     * any more: while a thread attaches or exits, in the GC, at safepoints and in signal handlers. A
     * counter update there could touch freed or unallocated memory. Their declaring class does not
     * tell: substitutions declare uninterruptible code on JDK classes too.</li>
     * <li>Methods of Native Image's own classes, by package: {@code com.oracle.svm},
     * {@code org.graalvm.nativeimage} and {@code org.graalvm.word}. Their profiles would describe the
     * runtime, not the application. The stack sampler leaves out the same frames
     * ({@code StackSampleRecorder}).</li>
     * </ul>
     * JDK methods that the application calls stay instrumented, including substitutions that are
     * not uninterruptible: their branches depend on how the application uses them.
     */
    public static boolean isNativeImageRuntimeCode(ResolvedJavaMethod method) {
        if (UninterruptibleAnnotationUtils.isUninterruptible(method)) {
            return true;
        }
        String declaringClass = method.getDeclaringClass().getName();
        return declaringClass.startsWith("Lcom/oracle/svm/") || declaringClass.startsWith("Lorg/graalvm/nativeimage/") || declaringClass.startsWith("Lorg/graalvm/word/");
    }

    /**
     * The calling context of a source position, innermost frame first, as stored by every
     * instrumented site. Sites share their frame objects: many sites have the same methods and BCIs
     * in their contexts (every site in one inlined callee, for example), so each distinct frame is
     * stored once in the image heap.
     */
    public static ProfileFrame[] context(NodeSourcePosition position) {
        List<ProfileFrame> frames = new ArrayList<>();
        for (NodeSourcePosition frame = position; frame != null; frame = frame.getCaller()) {
            ProfileFrame candidate = new ProfileFrame(methodDescriptor(frame.getMethod()), frame.getBCI());
            frames.add(FRAMES.computeIfAbsent(candidate, f -> f));
        }
        return frames.toArray(ProfileFrame[]::new);
    }

    private static boolean hasNonDefaultProbability(ControlSplitNode n1) {
        double defaultProbability = 1.0 / n1.successors().count();
        for (Node s : n1.successors().snapshot()) {
            if (Double.compare(n1.probability((AbstractBeginNode) s), defaultProbability) != 0) {
                return true;
            }
        }
        return false;
    }
}
