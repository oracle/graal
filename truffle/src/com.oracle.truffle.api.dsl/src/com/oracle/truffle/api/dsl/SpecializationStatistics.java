/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a specialization statistics utiltiy that can be {@link #enter() entered} to collect
 * additional statistics about Truffle DSL nodes. In order for the statistics to be useful the nodes
 * need to be regenerated using the <code>-Atruffle.dsl.GenerateSpecializationStatistics=true</code>
 * flag or using the {@link AlwaysEnabled} annotation.
 * <p>
 * The easiest way to use this utility is to enable the
 * <code>--engine.SpecializationStatistics</code> polyglot option. This should print the histogram
 * when the engine is closed.
 * <p>
 * See also the <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/SpecializationHistogram.md">usage
 * tutorial</a> on the website.
 *
 * @since 20.3
 */
public final class SpecializationStatistics {

    private static final ThreadLocal<SpecializationStatistics> STATISTICS = new ThreadLocal<>();
    private final Map<Class<?>, NodeClassStatistics> classStatistics = new HashMap<>();
    private final Map<Node, EnabledNodeStatistics> uncachedStatistics = new HashMap<>();

    SpecializationStatistics() {
    }

    /**
     * Returns <code>true</code> if the statistics did collect any data, else <code>false</code>.
     *
     * @since 20.3
     */
    public synchronized boolean hasData() {
        for (NodeClassStatistics classStatistic : classStatistics.values()) {
            if (classStatistic.createHistogram().getNodeStat().getSum() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prints the specialization histogram to the provided writer. Does not print anything if no
     * {@link #hasData() data} was collected.
     *
     * @see #printHistogram(PrintWriter)
     * @since 20.3
     */
    public synchronized void printHistogram(PrintWriter writer) {
        List<NodeClassHistogram> histograms = new ArrayList<>();
        long parentSum = 0;
        long parentCount = 0;
        for (NodeClassStatistics classStatistic : classStatistics.values()) {
            NodeClassHistogram histogram = classStatistic.createHistogram();
            histograms.add(histogram);
            parentSum += histogram.getNodeStat().getSum();
            parentCount += histogram.getNodeStat().getCount();
        }

        Collections.sort(histograms, new Comparator<NodeClassHistogram>() {
            public int compare(NodeClassHistogram o1, NodeClassHistogram o2) {
                return Long.compare(o1.getNodeStat().getSum(), o2.getNodeStat().getSum());
            }
        });

        int width = 0;
        for (NodeClassHistogram histogram : histograms) {
            if (histogram.getNodeStat().getSum() == 0) {
                continue;
            }
            width = Math.max(histogram.getLabelWidth(), width);
        }
        width = Math.min(width, 80);

        NodeClassHistogram.printLine(writer, " ", width);
        for (NodeClassHistogram histogram : histograms) {
            if (histogram.getNodeStat().getSum() == 0) {
                continue;
            }
            histogram.print(writer, width, parentCount, parentSum);
        }
    }

    /**
     * Prints the specialization histogram to the provided stream. Does not print anything if no
     * {@link #hasData() data} was collected.
     *
     * @see #printHistogram(PrintWriter)
     * @since 20.3
     */
    public synchronized void printHistogram(PrintStream stream) {
        printHistogram(new PrintWriter(stream));
    }

    /**
     * Creates a new specialization statistics instance. Note specialization statistics need to be
     * {@link #enter() entered} to collect data on a thread.
     *
     * @since 20.3
     */
    public static SpecializationStatistics create() {
        return new SpecializationStatistics();
    }

    private synchronized NodeStatistics createCachedNodeStatistic(Node node, String[] specializations) {
        NodeClassStatistics classStatistic = getClassStatistics(node.getClass(), specializations);
        EnabledNodeStatistics stat = new EnabledNodeStatistics(node, classStatistic);
        classStatistic.statistics.add(stat);
        if (classStatistic.nodeCounter++ % 1024 == 0) {
            /*
             * In order to not crash for code load benchmarks we need to process collected nodes
             * from time to time to clean them up.
             */
            classStatistic.processCollectedStatistics();
        }
        return stat;
    }

    private NodeClassStatistics getClassStatistics(Class<?> nodeClass, String[] specializations) {
        assert Thread.holdsLock(this);
        return this.classStatistics.computeIfAbsent(nodeClass, (c) -> new NodeClassStatistics(c, specializations));
    }

    private static NodeStatistics createUncachedNodeStatistic(Node node, String[] specializations) {
        return new UncachedNodeStatistics(node, specializations);
    }

    /**
     * Enters this specialization instance object on the current thread. After entering a
     * specialization statistics instance will gather statistics for all nodes with
     * {@link Specialization specializations} that were created on this entered thread. Multiple
     * threads may be entered at the same time. The caller must make sure to
     * {@link #leave(SpecializationStatistics)} the current statistics after entering in all cases.
     *
     * @since 20.3
     */
    @TruffleBoundary
    public SpecializationStatistics enter() {
        SpecializationStatistics prev = STATISTICS.get();
        STATISTICS.set(this);
        return prev;
    }

    /**
     * Leaves the currently {@link #enter() entered} entered statistics. It is required to leave a
     * statistics block after it was entered. It is recommended to use a finally block for this
     * purpose.
     *
     * @since 20.3
     */
    @SuppressWarnings("static-method")
    @TruffleBoundary
    public void leave(SpecializationStatistics prev) {
        STATISTICS.set(prev);
    }

    /**
     * Used on nodes to always enable specialization statistics. The Truffle DSL processor will not
     * generate statistics code unless the
     * <code>-J-Dtruffle.dsl.GenerateSpecializationStatistics=true</code> javac system property is
     * set. This annotation can be used to annotate node types that want to force enable the
     * statistics independent of the system property. This annotation is inherited by sub classes.
     *
     * @since 20.3
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE})
    public @interface AlwaysEnabled {

    }

    static final class NodeClassStatistics {

        private List<EnabledNodeStatistics> statistics = new ArrayList<>();
        /*
         * Combines data from all collected nodes.
         */
        private final NodeClassHistogram collectedHistogram;
        private int nodeCounter;

        NodeClassStatistics(Class<?> nodeClass, String[] specializations) {
            this.collectedHistogram = new NodeClassHistogram(nodeClass, specializations);
        }

        private void processCollectedStatistics() {
            boolean found = false;
            /*
             * Most calls to processStatistics don't actually need to remove anything. But if
             * something is removed it is typically more than one entry, so we do a first pass over
             * the references to find out whether there is a removed node and then recreate the
             * entire list.
             */
            for (EnabledNodeStatistics statistic : this.statistics) {
                if (statistic.isCollected()) {
                    found = true;
                    break;
                }
            }
            if (found) {
                List<EnabledNodeStatistics> newStatistics = new ArrayList<>();
                for (EnabledNodeStatistics statistic : this.statistics) {
                    if (statistic.isCollected()) {
                        collectedHistogram.accept(statistic);
                    } else {
                        newStatistics.add(statistic);
                    }
                }
                statistics = newStatistics;
            }
        }

        public NodeClassHistogram createHistogram() {
            NodeClassHistogram h = new NodeClassHistogram(collectedHistogram.getNodeClass(), collectedHistogram.getSpecializationNames());
            h.combine(this.collectedHistogram);
            for (EnabledNodeStatistics stat : statistics) {
                h.accept(stat);
            }
            return h;
        }

    }

    static final class IntStatistics extends IntSummaryStatistics {

        private SourceSection maxSourceSection;

        @Override
        @Deprecated(since = "20.3")
        public void accept(int value) {
            throw new UnsupportedOperationException();
        }

        public void accept(int value, SourceSection sourceSection) {
            if (value > getMax()) {
                this.maxSourceSection = sourceSection;
            }
            super.accept(value);
        }

        public void combine(IntStatistics other) {
            if (other.getMax() > this.getMax()) {
                this.maxSourceSection = other.maxSourceSection;
            }
            super.combine(other);
        }

        @Override
        @Deprecated(since = "20.3")
        public void combine(IntSummaryStatistics other) {
            throw new UnsupportedOperationException();
        }

    }

    static final class NodeClassHistogram {

        private final Class<?> nodeClass;
        private final String[] specializationNames;
        private final IntStatistics nodeStat;
        private final IntStatistics[] specializationStat;
        private final List<Map<TypeCombination, IntStatistics>> typeCombinationStat;
        private final Map<BitSet, IntStatistics[]> specializationCombinationStat;
        private final Map<BitSet, IntStatistics> specializationCombinationSumStat;

        @SuppressWarnings("unchecked")
        NodeClassHistogram(Class<?> nodeClass, String[] specializationNames) {
            this.nodeClass = nodeClass;
            this.specializationNames = specializationNames;
            this.typeCombinationStat = new ArrayList<>(specializationNames.length);
            this.specializationStat = new IntStatistics[specializationNames.length];
            this.nodeStat = new IntStatistics();
            for (int i = 0; i < specializationNames.length; i++) {
                typeCombinationStat.add(new LinkedHashMap<>());
                specializationStat[i] = new IntStatistics();
            }
            this.specializationCombinationStat = new HashMap<>();
            this.specializationCombinationSumStat = new HashMap<>();
        }

        Class<?> getNodeClass() {
            return nodeClass;
        }

        String[] getSpecializationNames() {
            return specializationNames;
        }

        IntStatistics getNodeStat() {
            return nodeStat;
        }

        void accept(EnabledNodeStatistics statistics) {
            int nodeSum = 0;
            SourceSection sourceSection = statistics.getSourceSection();
            BitSet enabledBitSet = new BitSet();

            for (int i = 0; i < statistics.specializations.length; i++) {
                TypeCombination combination = statistics.specializations[i];
                int specializationSum = 0;
                while (combination != null) {
                    int count = combination.executionCount;
                    IntStatistics typeCombination = this.typeCombinationStat.get(i).computeIfAbsent(combination, (c) -> new IntStatistics());
                    typeCombination.accept(count, sourceSection);
                    combination = combination.next;
                    specializationSum += count;
                }
                nodeSum += specializationSum;
                if (specializationSum != 0) {
                    enabledBitSet.set(i);
                    specializationStat[i].accept(specializationSum, sourceSection);
                }
            }
            if (nodeSum == 0) {
                // not actually executed
                return;
            }
            IntStatistics combinationSumStat = specializationCombinationSumStat.computeIfAbsent(enabledBitSet, (b) -> new IntStatistics());
            IntStatistics[] combinationSpecializations = specializationCombinationStat.computeIfAbsent(enabledBitSet, (b) -> new IntStatistics[specializationNames.length]);
            int combinationSum = 0;
            for (int i = 0; i < statistics.specializations.length; i++) {
                TypeCombination combination = statistics.specializations[i];
                int specializationSum = 0;
                while (combination != null) {
                    specializationSum += combination.executionCount;
                    combination = combination.next;
                }
                if (specializationSum != 0) {
                    combinationSum += specializationSum;
                    if (combinationSpecializations[i] == null) {
                        combinationSpecializations[i] = new IntStatistics();
                    }
                    combinationSpecializations[i].accept(specializationSum, sourceSection);
                }
            }
            combinationSumStat.accept(combinationSum, sourceSection);

            if (nodeSum != 0) {
                nodeStat.accept(nodeSum, sourceSection);
            }
        }

        void combine(NodeClassHistogram nodeClassStatistics) {
            for (int i = 0; i < typeCombinationStat.size(); i++) {
                Map<TypeCombination, IntStatistics> statistics = nodeClassStatistics.typeCombinationStat.get(i);
                for (Entry<TypeCombination, IntStatistics> executionStat : statistics.entrySet()) {
                    this.typeCombinationStat.get(i).computeIfAbsent(executionStat.getKey(), (c) -> new IntStatistics()).combine(executionStat.getValue());
                }
                for (int j = 0; j < specializationStat.length; j++) {
                    specializationStat[j].combine(nodeClassStatistics.specializationStat[i]);
                }
                nodeStat.combine(nodeClassStatistics.nodeStat);
            }
        }

        void print(PrintWriter stream, int width, long parentCount, long parentSum) {
            // we need 6 more characters to fit the maximum indent
            if (nodeStat.getCount() == 0) {
                return;
            }
            stream.printf("| %-" + width + "s         Instances          Executions     Executions per instance %n", "Name");
            printLine(stream, " ", width);

            String className = getDisplayName();

            printStats(stream, "| ", className, width, nodeStat, parentCount, parentSum);
            for (int i = 0; i < specializationNames.length; i++) {
                int size = typeCombinationStat.get(i).size();
                String specializationLabel = specializationNames[i];
                if (size == 1) {
                    specializationLabel += " " + typeCombinationStat.get(i).keySet().iterator().next().getDisplayName();
                }
                printStats(stream, "|   ", specializationLabel, width, specializationStat[i], nodeStat.getCount(), nodeStat.getSum());
                if (size > 1) {
                    for (Entry<TypeCombination, IntStatistics> entry : typeCombinationStat.get(i).entrySet()) {
                        printStats(stream, "|     ", entry.getKey().getDisplayName(), width, entry.getValue(), specializationStat[i].getCount(), specializationStat[i].getSum());
                    }
                }
            }

            printLine(stream, "|   ", width);

            Set<BitSet> printedCombinations = new HashSet<>();
            for (int specialization = 0; specialization < specializationNames.length; specialization++) {
                for (BitSet specializations : specializationCombinationStat.keySet()) {
                    if (printedCombinations.contains(specializations)) {
                        continue;
                    }
                    // trying to order them by index. First print all combinations with the first
                    // specialization then all with the second and so on.
                    if (!specializations.get(specialization)) {
                        continue;
                    }
                    IntStatistics statistics = specializationCombinationSumStat.get(specializations);
                    IntStatistics[] specializationStatistics = specializationCombinationStat.get(specializations);
                    int specializationIndex = 0;
                    StringBuilder label = new StringBuilder("[");
                    String sep = "";
                    int bits = 0;
                    while ((specializationIndex = specializations.nextSetBit(specializationIndex)) != -1) {
                        label.append(sep);
                        label.append(specializationNames[specializationIndex]);
                        sep = ", ";
                        specializationIndex++; // exclude previous bit.
                        bits++;
                    }
                    label.append("]");
                    printStats(stream, "|   ", label.toString(), width, statistics, nodeStat.getCount(), nodeStat.getSum());

                    if (bits > 1) {
                        specializationIndex = 0;
                        while ((specializationIndex = specializations.nextSetBit(specializationIndex)) != -1) {
                            printStats(stream, "|     ", specializationNames[specializationIndex], width, specializationStatistics[specializationIndex], statistics.getCount(), statistics.getSum());
                            specializationIndex++; // exclude previous bit.
                        }
                    }

                    printedCombinations.add(specializations);
                }
            }

            printLine(stream, " ", width);
        }

        static void printLine(PrintWriter stream, String indent, int width) {
            stream.print(indent);
            for (int i = 0; i < width + 100 - indent.length(); i++) {
                stream.print('-');
            }
            stream.print(System.lineSeparator());
        }

        private String getDisplayName() {
            String className = nodeClass.getSimpleName();
            if (className.equals("Uncached")) {
                Class<?> enclosing = nodeClass.getEnclosingClass();
                if (enclosing != null) {
                    className = enclosing.getSimpleName() + "." + className;
                }
            }
            return className;
        }

        private int getLabelWidth() {
            int width = 0;
            width = Math.max(getDisplayName().length(), width);
            for (String name : specializationNames) {
                width = Math.max(name.length(), width);
            }
            for (Map<TypeCombination, IntStatistics> executionStat : typeCombinationStat) {
                for (TypeCombination combination : executionStat.keySet()) {
                    width = Math.max(combination.getDisplayName().length(), width);
                }
            }
            return width;
        }

        private static void printStats(PrintWriter stream, String indent, String label, int labelWidth, IntStatistics nodeStats, long parentCount, long parentSum) {
            String countPercent = String.format("(%.0f%%)", ((double) nodeStats.getCount() / (double) parentCount) * 100);
            String sumPercent = String.format("(%.0f%%)", ((double) nodeStats.getSum() / (double) parentSum) * 100);
            stream.printf("%s%-" + labelWidth + "s  %8d %-6s %12d %-6s       Min=%10d Avg=%12.2f Max= %10d  MaxNode= %s %n",
                            indent, label,
                            nodeStats.getCount(), countPercent,
                            nodeStats.getSum(), sumPercent,
                            nodeStats.getMin() == Integer.MAX_VALUE ? 0 : nodeStats.getMin(),
                            nodeStats.getAverage(), nodeStats.getMax() == Integer.MIN_VALUE ? 0 : nodeStats.getMax(),
                            formatSourceSection(nodeStats, nodeStats.maxSourceSection));
        }

        // custom version of SourceSection#getShortDescription
        private static String formatSourceSection(IntStatistics stats, SourceSection s) {
            if (s == null) {
                if (stats.getCount() > 0) {
                    return "N/A";
                } else {
                    return " - ";
                }
            }
            StringBuilder b = new StringBuilder();
            if (s.getSource().getPath() == null) {
                b.append(s.getSource().getName());
            } else {
                Path pathAbsolute = Paths.get(s.getSource().getPath());
                Path pathBase = new File("").getAbsoluteFile().toPath();
                try {
                    Path pathRelative = pathBase.relativize(pathAbsolute);
                    b.append(pathRelative.toFile());
                } catch (IllegalArgumentException e) {
                    b.append(s.getSource().getName());
                }
            }
            b.append("~");
            formatIndices(b, s);
            return b.toString();
        }

        private static void formatIndices(StringBuilder b, SourceSection s) {
            boolean singleLine = s.getStartLine() == s.getEndLine();
            if (singleLine) {
                b.append(s.getStartLine());
            } else {
                b.append(s.getStartLine()).append("-").append(s.getEndLine());
            }
            b.append(":");
            if (s.getCharLength() <= 1) {
                b.append(s.getCharIndex());
            } else {
                b.append(s.getCharIndex()).append("-").append(s.getCharIndex() + s.getCharLength() - 1);
            }
        }

    }

    static final class TypeCombination {

        final TypeCombination next;
        final Class<?>[] types;
        int executionCount;

        TypeCombination(TypeCombination next, Class<?>[] types) {
            this.next = next;
            this.types = types;
        }

        String getDisplayName() {
            if (types.length == 0) {
                return "<no-args>";
            }
            StringBuilder b = new StringBuilder();
            b.append("<");
            String sep = "";
            for (int i = 0; i < types.length; i++) {
                b.append(sep);
                b.append(types[i].getSimpleName());
                sep = " ";
            }
            b.append(">");
            return b.toString();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(types);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TypeCombination)) {
                return false;
            }
            return Arrays.equals(types, ((TypeCombination) obj).types);
        }

    }

    static final class DisabledNodeStatistics extends NodeStatistics {

        static final DisabledNodeStatistics INSTANCE = new DisabledNodeStatistics();

        @Override
        public void acceptExecute(int specializationIndex, Class<?> arg0) {
        }

        @Override
        public void acceptExecute(int specializationIndex, Class<?> arg0, Class<?> arg1) {
        }

        @Override
        public void acceptExecute(int specializationIndex, Class<?>... args) {
        }

        @Override
        public Class<?> resolveValueClass(Object value) {
            return null;
        }

    }

    static final class UncachedNodeStatistics extends NodeStatistics {

        final Node node;
        final String[] specializationNames;

        UncachedNodeStatistics(Node node, String[] specializations) {
            this.node = node;
            this.specializationNames = specializations;
        }

        @Override
        @TruffleBoundary
        public void acceptExecute(int specializationIndex, Class<?> arg0) {
            lookup().acceptExecute(specializationIndex, arg0);
        }

        @Override
        @TruffleBoundary
        public void acceptExecute(int specializationIndex, Class<?> arg0, Class<?> arg1) {
            lookup().acceptExecute(specializationIndex, arg0, arg1);
        }

        @Override
        @TruffleBoundary
        public void acceptExecute(int specializationIndex, Class<?>... args) {
            lookup().acceptExecute(specializationIndex, args);
        }

        @Override
        public Class<?> resolveValueClass(Object value) {
            if (value == null) {
                return void.class;
            } else {
                return value.getClass();
            }
        }

        private NodeStatistics lookup() {
            SpecializationStatistics statistics = STATISTICS.get();
            if (statistics != null) {
                synchronized (statistics) {
                    return statistics.uncachedStatistics.computeIfAbsent(node, (n) -> createUncachedStatistic(statistics, n));
                }
            } else {
                return DisabledNodeStatistics.INSTANCE;
            }
        }

        private EnabledNodeStatistics createUncachedStatistic(SpecializationStatistics statistics, Node n) {
            NodeClassStatistics classStat = statistics.getClassStatistics(this.node.getClass(), specializationNames);
            EnabledNodeStatistics nodeStatistic = new EnabledNodeStatistics(n, classStat);
            classStat.statistics.add(nodeStatistic);
            return nodeStatistic;
        }

    }

    static final class EnabledNodeStatistics extends NodeStatistics {

        private static final Object UNDEFINED_SOURCE_SECTION = new Object();
        @CompilationFinal(dimensions = 1) final TypeCombination[] specializations;
        final WeakReference<Node> nodeRef;
        private Object sourceSection = UNDEFINED_SOURCE_SECTION;

        EnabledNodeStatistics(Node node, NodeClassStatistics statistics) {
            this.nodeRef = new WeakReference<>(node);
            this.specializations = new TypeCombination[statistics.collectedHistogram.getSpecializationNames().length];
        }

        SourceSection getSourceSection() {
            if (sourceSection == UNDEFINED_SOURCE_SECTION) {
                return null;
            }
            return (SourceSection) sourceSection;
        }

        boolean isCollected() {
            return nodeRef.get() == null;
        }

        @Override
        @ExplodeLoop
        public void acceptExecute(int specializationIndex, Class<?> arg0) {
            CompilerAsserts.partialEvaluationConstant(this);
            TypeCombination combination = specializations[specializationIndex];
            while (combination != null) {
                if (combination.types.length == 1) {
                    if (combination.types[0] == arg0) {
                        combination.executionCount++;
                        return;
                    }
                }
                combination = combination.next;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            insertCombination(specializationIndex, arg0).executionCount++;
        }

        @Override
        @ExplodeLoop
        public void acceptExecute(int specializationIndex, Class<?> arg0, Class<?> arg1) {
            CompilerAsserts.partialEvaluationConstant(this);
            TypeCombination combination = specializations[specializationIndex];
            while (combination != null) {
                if (combination.types.length == 2) {
                    if (combination.types[0] == arg0 && combination.types[1] == arg1) {
                        combination.executionCount++;
                        return;
                    }
                }
                combination = combination.next;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            insertCombination(specializationIndex, arg0, arg1).executionCount++;
        }

        @Override
        @ExplodeLoop
        public void acceptExecute(int specializationIndex, Class<?>... args) {
            CompilerAsserts.partialEvaluationConstant(this);
            TypeCombination combination = findCombination(specializationIndex, args);
            if (combination != null) {
                combination.executionCount++;
                return;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            insertCombination(specializationIndex, args).executionCount++;
        }

        @Override
        @SuppressWarnings("static-method")
        public Class<?> resolveValueClass(Object value) {
            if (value == null) {
                return void.class;
            } else {
                return value.getClass();
            }
        }

        @ExplodeLoop
        private TypeCombination findCombination(int specializationIndex, Class<?>... args) {
            TypeCombination combination = specializations[specializationIndex];
            while (combination != null) {
                if (combination.types.length == args.length) {
                    boolean valid = true;
                    for (int i = 0; i < combination.types.length; i++) {
                        if (combination.types[i] != args[i]) {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        return combination;
                    }
                }
                combination = combination.next;
            }
            return null;
        }

        private synchronized TypeCombination insertCombination(int specializationIndex, Class<?>... args) {
            if (this.sourceSection == UNDEFINED_SOURCE_SECTION) {
                Node node = nodeRef.get();
                if (node != null) {
                    this.sourceSection = node.getEncapsulatingSourceSection();
                } else {
                    // this should not happen, but there is no guarantee
                    this.sourceSection = null;
                }
            }
            TypeCombination combination = findCombination(specializationIndex, args);
            if (combination != null) {
                return combination;
            }
            specializations[specializationIndex] = combination = new TypeCombination(specializations[specializationIndex], args);
            return combination;
        }

    }

    /**
     * Class to collect statistics information per node. This class is intended to be used by
     * Truffle DSL generated code only. Do not use directly.
     *
     * @since 20.3
     */
    public abstract static class NodeStatistics {

        NodeStatistics() {
        }

        /**
         * Called when a node specialization was executed. This method is intended to be used by
         * Truffle DSL generated code only. Do not use directly.
         *
         * @since 20.3
         */
        public abstract void acceptExecute(int specializationIndex, Class<?> arg0);

        /**
         * Called when a node specialization was executed. This method is intended to be used by
         * Truffle DSL generated code only. Do not use directly.
         *
         * @since 20.3
         */
        public abstract void acceptExecute(int specializationIndex, Class<?> arg0, Class<?> arg1);

        /**
         * Called when a node specialization was executed. This method is intended to be used by
         * Truffle DSL generated code only. Do not use directly.
         *
         * @since 20.3
         */
        public abstract void acceptExecute(int specializationIndex, Class<?>... args);

        /**
         * Called to resolve the class of a value provided in {@link #acceptExecute(int, Class)}.
         * This method is intended to be used by Truffle DSL generated code only. Do not use
         * directly.
         *
         * @since 20.3
         */
        public abstract Class<?> resolveValueClass(Object value);

        /**
         * Called when a new node statistics object is created. This method is intended to be used
         * by Truffle DSL generated code only. Do not use directly.
         *
         * @since 20.3
         */
        public static NodeStatistics create(Node node, String[] specializations) {
            if (node.isAdoptable()) {
                SpecializationStatistics stat = STATISTICS.get();
                if (stat == null) {
                    return DisabledNodeStatistics.INSTANCE;
                }
                return stat.createCachedNodeStatistic(node, specializations);
            } else {
                return SpecializationStatistics.createUncachedNodeStatistic(node, specializations);
            }
        }
    }
}
