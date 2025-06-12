/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.SourceLanguagePosition;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

final class ExpansionStatistics {

    private final Set<TruffleCompilerOptions.CompilationTier> enabledStages = new EconomicHashSet<>();
    private final PartialEvaluator partialEvaluator;
    private volatile TruffleCompilable previousCompilation;
    private final Set<TruffleCompilerOptions.CompilationTier> traceMethodExpansion;
    private final Set<TruffleCompilerOptions.CompilationTier> traceNodeExpansion;
    private final Map<TruffleCompilerOptions.CompilationTier, Map<ResolvedJavaMethod, Stats>> methodExpansionStatistics = new EconomicHashMap<>();
    private final Map<TruffleCompilerOptions.CompilationTier, Map<NodeClassKey, Stats>> nodeExpansionStatistics = new EconomicHashMap<>();
    private final Map<TruffleCompilerOptions.CompilationTier, Map<NodeSpecializationKey, Stats>> specializationExpansionStatistics = new EconomicHashMap<>();

    private ExpansionStatistics(PartialEvaluator partialEvaluator,
                    Set<TruffleCompilerOptions.CompilationTier> traceMethodExpansion, Set<TruffleCompilerOptions.CompilationTier> traceNodeExpansion,
                    Set<TruffleCompilerOptions.CompilationTier> methodExpansionStatistics, Set<TruffleCompilerOptions.CompilationTier> nodeExpansionStatistics) {
        this.partialEvaluator = partialEvaluator;
        this.traceMethodExpansion = traceMethodExpansion;
        this.traceNodeExpansion = traceNodeExpansion;
        for (TruffleCompilerOptions.CompilationTier tier : methodExpansionStatistics) {
            this.methodExpansionStatistics.put(tier, new EconomicHashMap<>());
        }
        for (TruffleCompilerOptions.CompilationTier tier : nodeExpansionStatistics) {
            this.nodeExpansionStatistics.put(tier, new EconomicHashMap<>());
        }
        this.enabledStages.addAll(traceMethodExpansion);
        this.enabledStages.addAll(traceNodeExpansion);
        this.enabledStages.addAll(methodExpansionStatistics);
        this.enabledStages.addAll(nodeExpansionStatistics);
    }

    static ExpansionStatistics create(PartialEvaluator partialEvaluator, OptionValues options) {
        if (!isEnabled(options)) {
            return null;
        }
        Set<TruffleCompilerOptions.CompilationTier> traceMethodExpansion = TruffleCompilerOptions.TraceMethodExpansion.getValue(options).tiers();
        Set<TruffleCompilerOptions.CompilationTier> traceNodeExpansion = TruffleCompilerOptions.TraceNodeExpansion.getValue(options).tiers();
        Set<TruffleCompilerOptions.CompilationTier> methodExpansionStatistics = TruffleCompilerOptions.MethodExpansionStatistics.getValue(options).tiers();
        Set<TruffleCompilerOptions.CompilationTier> nodeExpansionStatistics = TruffleCompilerOptions.NodeExpansionStatistics.getValue(options).tiers();

        return new ExpansionStatistics(partialEvaluator, traceMethodExpansion, traceNodeExpansion, methodExpansionStatistics, nodeExpansionStatistics);
    }

    static boolean isEnabled(OptionValues options) {
        Set<TruffleCompilerOptions.CompilationTier> traceMethodExpansion = TruffleCompilerOptions.TraceMethodExpansion.getValue(options).tiers();
        Set<TruffleCompilerOptions.CompilationTier> traceNodeExpansion = TruffleCompilerOptions.TraceNodeExpansion.getValue(options).tiers();
        Set<TruffleCompilerOptions.CompilationTier> methodExpansionStatistics = TruffleCompilerOptions.MethodExpansionStatistics.getValue(options).tiers();
        Set<TruffleCompilerOptions.CompilationTier> nodeExpansionStatistics = TruffleCompilerOptions.NodeExpansionStatistics.getValue(options).tiers();

        if (!traceMethodExpansion.isEmpty() ||
                        !traceNodeExpansion.isEmpty() ||
                        !methodExpansionStatistics.isEmpty() ||
                        !nodeExpansionStatistics.isEmpty()) {
            return true;
        }
        return false;
    }

    void afterPartialEvaluation(TruffleCompilable compilable, StructuredGraph graph) {
        this.previousCompilation = compilable;
        handleStage(compilable, graph, TruffleCompilerOptions.CompilationTier.peTier);
    }

    void afterTruffleTier(TruffleCompilable compilable, StructuredGraph graph) {
        handleStage(compilable, graph, TruffleCompilerOptions.CompilationTier.truffleTier);
    }

    void afterLowTier(TruffleCompilable compilable, StructuredGraph graph) {
        handleStage(compilable, graph, TruffleCompilerOptions.CompilationTier.lowTier);
    }

    private void handleStage(TruffleCompilable compilable, StructuredGraph graph, TruffleCompilerOptions.CompilationTier tier) {
        boolean methodExpansion = this.traceMethodExpansion.contains(tier);
        boolean nodeExpansion = this.traceNodeExpansion.contains(tier);
        boolean methodExpansionStat = this.methodExpansionStatistics.containsKey(tier);
        boolean nodeExpansionStat = this.nodeExpansionStatistics.containsKey(tier);

        TreeNode methodTree = null;
        TreeNode nodeTree = null;
        if (methodExpansion) {
            if (methodTree == null) {
                methodTree = buildMethodTree(graph);
            }
            printExpansionTree(compilable, methodTree, tier);
        }

        if (nodeExpansion) {
            if (methodTree == null) {
                methodTree = buildMethodTree(graph);
            }
            nodeTree = methodTree.groupByNode();
            printExpansionTree(compilable, nodeTree, tier);
        }

        if (methodExpansionStat) {
            if (methodTree == null) {
                methodTree = buildMethodTree(graph);
            }

            Map<ResolvedJavaMethod, Stats> sums = new EconomicHashMap<>();
            methodTree.acceptStats(sums, (tree) -> tree.position == null ? null : tree.position.getMethod(), compilable.getName());

            combineExpansionStatistics(tier, this.methodExpansionStatistics, sums);
        }

        if (nodeExpansionStat) {
            if (methodTree == null) {
                methodTree = buildMethodTree(graph);
            }
            if (nodeTree == null) {
                nodeTree = methodTree.groupByNode();
            }

            Map<NodeClassKey, Stats> classSums = new EconomicHashMap<>();
            nodeTree.acceptStats(classSums, (tree) -> new NodeClassKey(tree), compilable.getName());

            combineExpansionStatistics(tier, this.nodeExpansionStatistics, classSums);

            Map<NodeSpecializationKey, Stats> specializationSums = new EconomicHashMap<>();
            nodeTree.acceptStats(specializationSums, (tree) -> new NodeSpecializationKey(tree), compilable.getName());

            combineExpansionStatistics(tier, this.specializationExpansionStatistics, specializationSums);
        }
    }

    void onShutdown() {
        TruffleCompilable ast = this.previousCompilation;
        if (ast == null) {
            // cannot print without any compilations
            return;
        }
        for (Entry<TruffleCompilerOptions.CompilationTier, Map<ResolvedJavaMethod, Stats>> statsEntry : this.methodExpansionStatistics.entrySet()) {
            printHistogram(ast, statsEntry.getKey(), statsEntry.getValue(), ExpansionStatistics::formatQualifiedMethod, null, null, null, "Method");
        }
        for (Entry<TruffleCompilerOptions.CompilationTier, Map<NodeClassKey, Stats>> statsEntry : this.nodeExpansionStatistics.entrySet()) {
            printHistogram(ast, statsEntry.getKey(), statsEntry.getValue(), (info) -> info == null ? "no info" : info.getLabel(),
                            this.specializationExpansionStatistics.get(statsEntry.getKey()),
                            (s) -> s.getLabel(),
                            (s) -> s.classKey, "Node");
        }
    }

    private <T, S> void printHistogram(TruffleCompilable ast, TruffleCompilerOptions.CompilationTier tier,
                    Map<T, Stats> statsMap, Function<T, String> labelFunction,
                    Map<S, Stats> subGroupMap, Function<S, String> subGroupLabelFunction,
                    Function<S, T> subGroupToGroup, String kind) {
        StringWriter writer = new StringWriter();
        try (PrintWriter w = new PrintWriter(writer)) {
            List<Entry<T, Stats>> entries = statsMap.entrySet().stream().sorted(ExpansionStatistics::orderBySumDesc).collect(Collectors.toList());
            Map<T, List<Entry<S, Stats>>> subGroups = null;
            if (subGroupMap != null) {
                List<Entry<S, Stats>> subEntries = subGroupMap.entrySet().stream().sorted(ExpansionStatistics::orderBySumDesc).collect(Collectors.toList());
                subGroups = new EconomicHashMap<>();
                for (Entry<S, Stats> entry : subEntries) {
                    List<Entry<S, Stats>> subGroup = subGroups.computeIfAbsent(subGroupToGroup.apply(entry.getKey()), (k) -> new ArrayList<>());
                    subGroup.add(entry);
                }
            }

            String indent = "  ";
            int maxLabelLength = 50;
            for (Entry<T, Stats> entry : entries) {
                String label = labelFunction.apply(entry.getKey());
                int labelLength = label.length();
                if (subGroups != null) {
                    List<Entry<S, Stats>> subGroup = subGroups.get(entry.getKey());
                    for (Entry<S, Stats> sub : subGroup) {
                        maxLabelLength = Math.max(subGroupLabelFunction.apply(sub.getKey()).length(), maxLabelLength);
                    }
                }
                maxLabelLength = Math.max(labelLength, maxLabelLength);
            }
            maxLabelLength += indent.length();

            w.printf("%-" + (maxLabelLength) +
                            "s    Count IR Nodes (min avg max)        Size (min avg max)      Cycles (min avg max)       Ifs  Loops Invokes Allocs | Max IRNode ASTNode Unit:Lang:File:Line:Chars%n",
                            "Name");

            for (Entry<T, Stats> entry : entries) {
                String label = labelFunction.apply(entry.getKey());
                Stats stats = entry.getValue();

                printHistogramStats(w, indent, maxLabelLength, label, stats);

                if (subGroups != null) {
                    List<Entry<S, Stats>> subGroup = subGroups.get(entry.getKey());
                    if (subGroup != null && subGroup.size() > 0) {
                        for (Entry<S, Stats> subEntry : subGroup) {
                            String subLabel = subGroupLabelFunction.apply(subEntry.getKey());
                            if (subGroup.size() == 1 && subLabel.equals("<unknown>")) {
                                // no need to print if only unknown specializations
                                break;
                            }
                            printHistogramStats(w, indent + "  ", maxLabelLength, subLabel, subEntry.getValue());
                        }
                    }
                }

            }
        }
        partialEvaluator.config.runtime().log(ast, String.format("%s expansion statistics after %s:%n%s", kind, tier.toString(), writer.toString()));
    }

    private static void printHistogramStats(PrintWriter w, String indent, int maxLabelLength, String label, Stats stats) {
        String sourceString = formatSourceAndCompilation(stats.maxCompilation, stats.maxSourcePosition);
        int useWidth = Math.max(maxLabelLength - indent.length(), 10);
        w.printf("%s%-" + (useWidth) + "s %8d %8d %-16s %8d %-16s %8d %-16s %6d %6d %7d %6d | %10s %7s %s%n",
                        indent,
                        label,
                        stats.count.getCount(),
                        stats.count.getSum(),
                        String.format("(%d %.1f %d)", stats.count.getMin(), stats.count.getAverage(), stats.count.getMax()),
                        stats.size.getSum(),
                        String.format("(%d %.1f %d)", stats.size.getMin(), stats.size.getAverage(), stats.size.getMax()),
                        stats.cycles.getSum(),
                        String.format("(%d %.1f %d)", stats.cycles.getMin(), stats.cycles.getAverage(), stats.cycles.getMax()),
                        stats.conditions.getSum(),
                        stats.loops.getSum(),
                        stats.invokes.getSum(),
                        stats.allocs.getSum(),
                        stats.maxGraalNodeId == -1 ? "" : stats.maxGraalNodeId,
                        getTruffleNodeId(stats.maxSourcePosition) == -1 ? "" : getTruffleNodeId(stats.maxSourcePosition),
                        sourceString);
    }

    private static <T> int orderBySumDesc(Entry<T, Stats> e0, Entry<T, Stats> e1) {
        return Long.compare(e1.getValue().count.getSum(), e0.getValue().count.getSum());
    }

    private TreeNode buildMethodTree(StructuredGraph graph) {
        TreeNode root = new TreeNode(null, null, ExpansionStatistics::buildMethodTreeLabel);
        SchedulePhase.runWithoutContextOptimizations(graph, SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        for (Node node : graph.getNodes()) {
            NodeSourcePosition nodeSourcePosition = node.getNodeSourcePosition();
            TreeNode tree = resolveMethodTree(root, nodeSourcePosition);
            HIRBlock block = schedule.blockFor(node);
            double frequency;
            if (block != null) {
                frequency = block.getRelativeFrequency();
                assert frequency != TreeNode.UNSET_FREQUENCY;
            } else {
                frequency = TreeNode.UNSET_FREQUENCY;
            }

            tree.frequency = Math.max(tree.frequency, frequency);
            tree.graalNodes.add(node);
        }

        return root;
    }

    private static String buildMethodTreeLabel(NodeSourcePosition pos) {
        if (pos == null || pos.getMethod() == null) {
            return "<root>";
        }
        return formatQualifiedMethod(pos.getMethod());
    }

    private TreeNode resolveMethodTree(TreeNode root, NodeSourcePosition pos) {
        if (pos == null) {
            return root;
        }
        TreeNode parent = resolveMethodTree(root, pos.getCaller());
        return parent.children.computeIfAbsent(new MethodKey(pos), (p) -> new TreeNode(parent, pos, root.label));
    }

    @SuppressWarnings("static-method")
    private synchronized <T> void combineExpansionStatistics(TruffleCompilerOptions.CompilationTier tier, Map<TruffleCompilerOptions.CompilationTier, Map<T, Stats>> stats,
                    Map<T, Stats> newStats) {
        Map<T, Stats> methodStats = stats.get(tier);
        if (methodStats == null) {
            methodStats = new EconomicHashMap<>();
            stats.put(tier, methodStats);
        }
        for (Entry<T, Stats> entry : newStats.entrySet()) {
            Stats s = entry.getValue();
            methodStats.computeIfAbsent(entry.getKey(), (m) -> new Stats()).combine(s);
        }
    }

    private void printExpansionTree(TruffleCompilable compilable, TreeNode tree, TruffleCompilerOptions.CompilationTier tier) {
        StringWriter writer = new StringWriter();
        try (PrintWriter w = new PrintWriter(writer)) {
            tree.print(w);
        }
        partialEvaluator.config.runtime().log(compilable, String.format("Expansion tree for %s after %s:%n%s", compilable.getName(), tier.toString(), writer.toString()));
    }

    private static String formatQualifiedMethod(ResolvedJavaMethod method) {
        if (method == null) {
            return "<no-source-position>";
        }
        String className = method.getDeclaringClass().getUnqualifiedName();
        return className + "." + formatMethod(method, 60);
    }

    private static String formatMethod(ResolvedJavaMethod method, int maxSignature) {
        if (method == null) {
            return "<no-source-position>";
        }
        StringBuilder signatureBuilder = new StringBuilder();
        ResolvedJavaType declaringType = method.getDeclaringClass();
        Signature signature = method.getSignature();
        String sep = "";
        for (int i = 0; i < signature.getParameterCount(false); i++) {
            JavaType type = signature.getParameterType(i, declaringType);
            signatureBuilder.append(sep);
            signatureBuilder.append(type.getUnqualifiedName());
            sep = ", ";
        }

        if (method.getName().length() + signatureBuilder.length() > maxSignature) {
            return method.getName() + "(..." + signature.getParameterCount(false) + ")";
        } else {
            return method.getName() + "(" + signatureBuilder.toString() + ")";
        }
    }

    private static int getTruffleNodeId(NodeSourcePosition position) {
        return position != null && position.getSourceLanguage() != null ? position.getSourceLanguage().getNodeId() : -1;
    }

    private static String formatSourceAndCompilation(String compilation, NodeSourcePosition pos) {
        if (pos == null || pos.getSourceLanguage() == null) {
            return compilation;
        }
        return compilation + ":" + formatSource(pos);
    }

    private static String formatSource(NodeSourcePosition pos) {
        if (pos == null) {
            return "";
        }
        SourceLanguagePosition source = pos.getSourceLanguage();
        if (source == null) {
            return "";
        }

        String sourceString;
        StringBuilder b = new StringBuilder();
        b.append(source.getLanguage());

        // fetch relative path
        String p = source.getURI() != null ? source.getURI().getPath() : null;
        if (p != null) {
            int lastIndex = p.lastIndexOf('/');
            if (lastIndex != -1) {
                p = p.substring(lastIndex + 1, p.length());
            }
            b.append(":").append(p);
        }
        b.append(":").append(source.getLineNumber());
        b.append(":").append(source.getOffsetStart());
        b.append("-").append(source.getOffsetEnd());
        sourceString = b.toString();
        return sourceString;
    }

    private static String formatClassName(String qualifiedName) {
        String[] chunks = qualifiedName.split("\\.");
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (int i = chunks.length - 1; i >= 0; i--) {
            String chunk = chunks[i];
            if (chunk.isEmpty()) {
                break;
            }
            if (Character.isUpperCase(chunk.charAt(0))) {
                b.append(sep);
                b.append(chunk);
                sep = ".";
            } else {
                break;
            }
        }
        if (b.length() == 0) {
            return qualifiedName;
        } else {
            return b.toString();
        }
    }

    /**
     * Method key that ignores the bci of the top-most method of the node source position.
     */
    private static class MethodKey implements Comparable<MethodKey> {

        final NodeSourcePosition position;

        MethodKey(NodeSourcePosition position) {
            this.position = position;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MethodKey)) {
                return false;
            }
            NodeSourcePosition other = ((MethodKey) obj).position;
            if (!Objects.equals(position.getCaller(), other.getCaller())) {
                return false;
            }
            if (!Objects.equals(position.getMethod(), other.getMethod()) || !Objects.equals(position.getSourceLanguage(), other.getSourceLanguage())) {
                return false;
            }
            return true;
        }

        /*
         * Order by parent bci.
         */
        @Override
        public int compareTo(MethodKey o) {
            if (this.equals(o)) {
                return 0;
            }
            NodeSourcePosition thisParent = position.getCaller();
            NodeSourcePosition otherParent = o.position.getCaller();
            if (Objects.equals(thisParent, otherParent) && thisParent != null) {
                return Integer.compare(thisParent.getBCI(), otherParent.getBCI());
            } else {
                // we don't really care about order, but should not be equal
                return 1;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(position.getCaller(), position.getMethod(), position.getSourceLanguage());
        }

    }

    private static class NodeSpecializationKey {

        private final NodeClassKey classKey;
        private final Set<ResolvedJavaMethod> specializations;

        NodeSpecializationKey(TreeNode tree) {
            this.classKey = new NodeClassKey(tree);
            this.specializations = tree.findSpecializationMethods();
        }

        @Override
        public int hashCode() {
            return Objects.hash(classKey, specializations);
        }

        String getLabel() {
            if (specializations.isEmpty()) {
                return "<unknown>";
            }
            StringBuilder b = new StringBuilder("[");
            String sep = "";
            for (ResolvedJavaMethod method : specializations) {
                b.append(sep);
                b.append(formatMethod(method, 40));
                sep = ", ";
            }
            b.append("]");
            return b.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NodeSpecializationKey)) {
                return false;
            }
            NodeSpecializationKey other = (NodeSpecializationKey) obj;
            return Objects.equals(classKey, other.classKey) && Objects.equals(specializations, other.specializations);
        }

    }

    private static class NodeClassKey {

        private final String name;

        NodeClassKey(TreeNode tree) {
            NodeSourcePosition pos = tree.position;
            if (pos != null && pos.getSourceLanguage() != null) {
                SourceLanguagePosition source = pos.getSourceLanguage();
                this.name = source.getNodeClassName();
            } else {
                this.name = null;
            }
        }

        String getLabel() {
            if (name == null) {
                return "<call-root>";
            }
            return formatClassName(name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NodeClassKey)) {
                return false;
            }
            NodeClassKey other = (NodeClassKey) obj;
            return Objects.equals(this.name, other.name);
        }

    }

    static final class Stats {

        final IntSummaryStatistics count = new IntSummaryStatistics();
        final IntSummaryStatistics size = new IntSummaryStatistics();
        final IntSummaryStatistics cycles = new IntSummaryStatistics();
        final IntSummaryStatistics conditions = new IntSummaryStatistics();
        final IntSummaryStatistics loops = new IntSummaryStatistics();
        final IntSummaryStatistics invokes = new IntSummaryStatistics();
        final IntSummaryStatistics allocs = new IntSummaryStatistics();

        NodeSourcePosition maxSourcePosition;
        int maxGraalNodeId = -1;
        String maxCompilation;

        @SuppressWarnings("deprecation")
        void accept(Sums sums, NodeSourcePosition sourcePosition, String compilation, int graalNodeId) {
            if (sums.count > count.getMax()) {
                this.maxGraalNodeId = graalNodeId;
                this.maxCompilation = compilation;
                this.maxSourcePosition = sourcePosition;
            }
            count.accept(sums.count);
            size.accept(sums.size);
            cycles.accept(sums.cycles);
            conditions.accept(sums.conditions);
            loops.accept(sums.loops);
            invokes.accept(sums.invokes);
            allocs.accept(sums.allocs);
        }

        void combine(Stats s) {
            if (s.count.getMax() > count.getMax()) {
                this.maxGraalNodeId = s.maxGraalNodeId;
                this.maxCompilation = s.maxCompilation;
                this.maxSourcePosition = s.maxSourcePosition;
            }
            count.combine(s.count);
            size.combine(s.size);
            cycles.combine(s.cycles);
            conditions.combine(s.conditions);
            loops.combine(s.loops);
            invokes.combine(s.invokes);
            allocs.combine(s.allocs);
        }

    }

    static class Sums {

        final NodeSourcePosition nodeSourcePosition;
        final int graalNodeId;
        final int count;
        final int size;
        final int cycles;
        final int conditions;
        final int loops;
        final int invokes;
        final int allocs;

        Sums(List<Node> nodes, NodeSourcePosition position, int graalNodeId) {
            this.count = nodes.size();
            this.size = computeSize(nodes);
            this.cycles = computeCycles(nodes);

            int invokeSum = 0;
            int loopSum = 0;
            int conditionSum = 0;
            int allocationSum = 0;
            for (Node node : nodes) {
                if (node instanceof InvokeNode) {
                    invokeSum++;
                }
                if (node instanceof LoopBeginNode) {
                    loopSum++;
                }
                if (node instanceof IfNode) {
                    conditionSum++;
                }
                if (node instanceof VirtualizableAllocation) {
                    allocationSum++;
                }
            }
            this.conditions = conditionSum;
            this.loops = loopSum;
            this.invokes = invokeSum;
            this.allocs = allocationSum;
            this.nodeSourcePosition = position;
            this.graalNodeId = graalNodeId;
        }

        Sums(int count, int size, int cycles, int conditions, int loops, int invokes, int allocs, NodeSourcePosition position, int graalNodeId) {
            this.count = count;
            this.size = size;
            this.cycles = cycles;
            this.conditions = conditions;
            this.loops = loops;
            this.invokes = invokes;
            this.allocs = allocs;
            this.nodeSourcePosition = position;
            this.graalNodeId = graalNodeId;
        }

        Sums add(Sums stats) {
            return new Sums(count + stats.count,
                            size + stats.size,
                            cycles + stats.cycles,
                            conditions + stats.conditions,
                            loops + stats.loops,
                            invokes + stats.invokes,
                            allocs + stats.allocs,
                            nodeSourcePosition, graalNodeId);
        }

        private static int computeSize(List<Node> nodes) {
            int sum = 0;
            for (Node node : nodes) {
                sum += node.estimatedNodeSize().value;
            }
            return sum;
        }

        private static int computeCycles(List<Node> nodes) {
            int sum = 0;
            for (Node node : nodes) {
                sum += node.estimatedNodeCycles().value;
            }
            return sum;
        }

    }

    final class TreeNode {

        static final double UNSET_FREQUENCY = -0.0d;

        final TreeNode parent;
        final Map<MethodKey, TreeNode> children = new TreeMap<>();

        final Function<NodeSourcePosition, String> label;
        NodeSourcePosition position;
        final List<Node> graalNodes = new ArrayList<>();
        // MethodKey sorted by parent bci
        double frequency = UNSET_FREQUENCY;

        TreeNode(TreeNode parent, NodeSourcePosition position, Function<NodeSourcePosition, String> label) {
            this.parent = parent;
            this.position = position;
            this.label = label;
        }

        Set<ResolvedJavaMethod> findSpecializationMethods() {
            Set<ResolvedJavaMethod> specializations = new EconomicHashSet<>();
            int nodeId = getTruffleNodeId(position);

            for (Node node : graalNodes) {
                NodeSourcePosition currentPos = node.getNodeSourcePosition();
                while (currentPos != null) {
                    SourceLanguagePosition sourceLang = currentPos.getSourceLanguage();
                    if (sourceLang != null && sourceLang.getNodeId() != nodeId) {
                        // new parent node started can no longer be accounted
                        break;
                    }
                    ResolvedJavaMethod method = currentPos.getMethod();
                    if (method != null && isSpecializationMethod(method)) {
                        specializations.add(currentPos.getMethod());
                    }
                    currentPos = currentPos.getCaller();
                }
            }
            findSpecializationMethodsWithNodeId(specializations, nodeId);

            return specializations;
        }

        private boolean isSpecializationMethod(ResolvedJavaMethod method) {
            return partialEvaluator.getMethodInfo(method).isSpecializationMethod();
        }

        private void findSpecializationMethodsWithNodeId(Set<ResolvedJavaMethod> specializations, int nodeId) {
            for (Node node : graalNodes) {
                NodeSourcePosition currentPos = node.getNodeSourcePosition();
                while (currentPos != null) {
                    SourceLanguagePosition sourceLang = currentPos.getSourceLanguage();
                    if (sourceLang != null && sourceLang.getNodeId() == nodeId) {
                        ResolvedJavaMethod method = currentPos.getMethod();
                        if (method != null && isSpecializationMethod(method)) {
                            specializations.add(currentPos.getMethod());
                        }
                    }
                    currentPos = currentPos.getCaller();
                }
            }
            for (TreeNode child : children.values()) {
                child.findSpecializationMethodsWithNodeId(specializations, nodeId);
            }
        }

        <T> void acceptStats(Map<T, Stats> stats, Function<TreeNode, T> keyFactory, String compilation) {
            T key = keyFactory.apply(this);
            Sums newSum = createSums();
            Stats stat = stats.get(key);
            if (stat == null) {
                stat = new Stats();
                stats.put(key, stat);
            }
            stat.accept(newSum, position, compilation, getGraalNodeId());
            for (TreeNode tree : children.values()) {
                tree.acceptStats(stats, keyFactory, compilation);
            }
        }

        @SuppressWarnings("deprecation")
        int getGraalNodeId() {
            if (graalNodes.isEmpty()) {
                return -1;
            }
            return graalNodes.get(0).getId();
        }

        TreeNode groupByNode() {
            return newGroup(new TreeNode(null, this.position, (n) -> {
                if (n != null && n.getSourceLanguage() != null) {
                    return formatClassName(n.getSourceLanguage().getNodeClassName());
                } else {
                    return "<call-root>";
                }
            }), (t0, t1) -> {
                int id0 = getTruffleNodeId(t0.position);
                int id1 = getTruffleNodeId(t1.position);
                if (id0 == id1 || (id0 != -1 && id1 == -1)) {
                    return 0;
                } else {
                    return 1;
                }
            });
        }

        TreeNode newGroup(TreeNode newNode, Comparator<TreeNode> comparison) {
            newNode.merge(this);
            for (Entry<MethodKey, TreeNode> child : children.entrySet()) {
                newNode.tryMerge(child.getKey(), child.getValue(), comparison);
            }
            return newNode;
        }

        private void tryMerge(MethodKey method, TreeNode tree, Comparator<TreeNode> groupCriteria) {
            if (groupCriteria.compare(this, tree) == 0) {
                // merge
                this.merge(tree);
                if (this.position == null) {
                    this.position = tree.position;
                }
                // merge children into parent
                for (Entry<MethodKey, TreeNode> child : tree.children.entrySet()) {
                    this.tryMerge(child.getKey(), child.getValue(), groupCriteria);
                }

                // merge empty silbling children with each other
                Set<MethodKey> toRemove = new EconomicHashSet<>();
                Map<Integer, TreeNode> mergeChildren = new EconomicHashMap<>();
                for (Entry<MethodKey, TreeNode> entry : new ArrayList<>(children.entrySet())) {
                    TreeNode mergeTarget = mergeChildren.get(getTruffleNodeId(entry.getValue().position));
                    if (mergeTarget != null) {
                        mergeTarget.merge(entry.getValue());
                        mergeTarget.children.putAll(entry.getValue().children);
                        toRemove.add(entry.getKey());
                    } else {
                        mergeChildren.put(getTruffleNodeId(entry.getValue().position), entry.getValue());
                    }
                }
                children.keySet().removeAll(toRemove);
            } else {
                TreeNode newGroup = this.children.get(method);
                if (newGroup == null) {
                    newGroup = new TreeNode(this, tree.position, this.label);
                } else {
                    assert newGroup.parent == this : Assertions.errorMessage(newGroup, newGroup.parent, this);
                }
                this.children.put(method, tree.newGroup(newGroup, groupCriteria));
            }
        }

        private void merge(TreeNode input) {
            if (input == null) {
                return;
            }
            this.graalNodes.addAll(input.graalNodes);
            this.frequency = Math.max(this.frequency, input.frequency);
        }

        void print(PrintWriter writer) {
            int width = Math.min(maxLabelLength() + 5, 150);
            printHeader(writer, width);
            printRec(writer, width, "");
        }

        private int maxLabelLength() {
            int maxLength = 0;
            for (TreeNode tree : children.values()) {
                maxLength = Math.max(tree.maxLabelLength(), maxLength);
            }
            return Math.max(depth() + getLabel().length() + 1, maxLength);
        }

        private int depth() {
            int maxDepth = 0;
            for (TreeNode tree : children.values()) {
                maxDepth = Math.max(tree.depth(), maxDepth);
            }
            return maxDepth + 1;
        }

        private Sums createRecursiveSum() {
            Sums stats = createSums();
            for (TreeNode child : children.values()) {
                stats = stats.add(child.createRecursiveSum());
            }
            return stats;
        }

        private Sums createSums() {
            return new Sums(graalNodes, position, getGraalNodeId());
        }

        private static void printHeader(PrintWriter writer, int width) {
            writer.printf("%-" + (width) +
                            "sFrequency | Count    Size  Cycles   Ifs Loops Invokes Allocs | Self Count  Size Cycles   Ifs Loops Invokes Allocs | IRNode ASTNode Lang:File:Line:Chars %n", "Name");
        }

        private void printRec(PrintWriter writer, int width, String sep) {
            Sums shallowStats = createSums();
            Sums deepStats = createRecursiveSum();
            // useWidth must not become 0 or smaller.
            int useWidth = Math.max(width - sep.length(), 10);
            writer.printf("%s%-" + (useWidth) + "s %8.2f | %5d %7d %7d %5d %5d %7d %6d | %10d %5d %6d %5d %5d %7d %6d | %s %n", sep,
                            getLabel(),
                            getFrequency(),
                            deepStats.count, deepStats.size, deepStats.cycles,
                            deepStats.conditions, deepStats.loops, deepStats.invokes, deepStats.allocs,
                            shallowStats.count, shallowStats.size, shallowStats.cycles,
                            shallowStats.conditions, shallowStats.loops, shallowStats.invokes, shallowStats.allocs,
                            getSourceString());
            String newSep = sep + " ";
            for (TreeNode tree : children.values()) {
                tree.printRec(writer, width, newSep);
            }
        }

        private double getFrequency() {
            if (parent == null) {
                /*
                 * The root always has frequency 1.0 Sometimes the root has constants assigned to it
                 * that have difference frequencies. But it does not make sense to show that.
                 */
                return 1.0;
            }
            if (frequency == UNSET_FREQUENCY) {
                return parent.getFrequency();
            } else {
                return frequency;
            }
        }

        @SuppressWarnings("deprecation")
        private String getSourceString() {
            if (position == null) {
                return " - ";
            }
            SourceLanguagePosition source = position.getSourceLanguage();
            String irNode = "";
            if (!graalNodes.isEmpty()) {
                irNode = String.valueOf(graalNodes.iterator().next().getId());
            }

            String astNode = "";
            String sourceString = "";
            if (source != null) {
                if (source.getNodeId() != -1) {
                    astNode = String.valueOf(source.getNodeId());
                }
                sourceString = formatSource(position);
            }
            return String.format("%6s %7s %6s", irNode, astNode, sourceString);
        }

        private String getLabel() {
            return label.apply(position);
        }

    }

}
