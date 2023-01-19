/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation.tracing;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;

// per-context singleton
public class OperationsStatistics {
    private final Map<Class<?>, GlobalOperationStatistics> statisticsMap = new HashMap<>();
    static final ThreadLocal<OperationsStatistics> STATISTICS = new ThreadLocal<>();
    private Path statePath;
    private FileChannel stateFile;
    private FileLock fileLock;

    OperationsStatistics(String statePath) {
        this.statePath = Path.of(statePath);
        read();
    }

    public static OperationsStatistics create(String statePath) {
        return new OperationsStatistics(statePath);
    }

    public OperationsStatistics enter() {
        OperationsStatistics prev = STATISTICS.get();
        STATISTICS.set(this);
        return prev;
    }

    public void exit(OperationsStatistics prev) {
        STATISTICS.set(prev);
    }

    private void read() {
        try {
            stateFile = FileChannel.open(statePath, StandardOpenOption.READ, StandardOpenOption.WRITE);
            fileLock = stateFile.lock();

            JSONTokener tok = new JSONTokener(Channels.newInputStream(stateFile));
            if (!tok.more()) {
                return;
            }

            JSONArray o = new JSONArray(tok);

            for (int i = 0; i < o.length(); i++) {
                JSONObject data = o.getJSONObject(i);
                GlobalOperationStatistics value = GlobalOperationStatistics.deserialize(this, data);
                this.statisticsMap.put(value.opsClass, value);
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(PrintWriter dumpWriter) {
        try {
            try {
                JSONArray result = new JSONArray();
                this.statisticsMap.forEach((k, v) -> {
                    result.put(v.serialize());
                });

                stateFile.position(0);
                stateFile.write(ByteBuffer.wrap(result.toString().getBytes(StandardCharsets.UTF_8)));
                stateFile.truncate(stateFile.position());

                fileLock.release();
            } finally {
                stateFile.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.statisticsMap.forEach((k, v) -> {
            try {
                v.writeDecisions(dumpWriter);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    synchronized GlobalOperationStatistics getStatsistics(Class<?> opsClass) {
        return statisticsMap.computeIfAbsent(opsClass, (c) -> new GlobalOperationStatistics(opsClass));
    }

    // per-context per-ops
    static final class GlobalOperationStatistics {
        private final List<EnabledExecutionTracer> allTracers = new ArrayList<>();
        private final ThreadLocal<EnabledExecutionTracer> currentTracer = new ThreadLocal<>();

        Class<?> opsClass;
        String decisionsFile;
        String[] instrNames;
        String[][] specNames;

        GlobalOperationStatistics(Class<?> opsClass) {
            this.opsClass = opsClass;
            setNames();
        }

        public void writeDecisions(PrintWriter dumpWriter) throws IOException {
            setNames();
            EnabledExecutionTracer tracer = collect();
            try (FileChannel ch = FileChannel.open(Path.of(decisionsFile), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                JSONArray result = tracer.serializeDecisions(this, dumpWriter);
                ch.truncate(0);
                ch.write(ByteBuffer.wrap(result.toString(4).getBytes(StandardCharsets.UTF_8)));
            }
        }

        private static GlobalOperationStatistics deserialize(OperationsStatistics parent, JSONObject data) throws ClassNotFoundException {
            Class<?> key = Class.forName(data.getString("key"));
            GlobalOperationStatistics result = parent.getStatsistics(key);
            result.allTracers.add(EnabledExecutionTracer.deserialize(result, data));
            return result;
        }

        private EnabledExecutionTracer collect() {
            EnabledExecutionTracer tracer = new EnabledExecutionTracer(this);
            for (EnabledExecutionTracer other : allTracers) {
                tracer.merge(other);
            }

            return tracer;
        }

        private JSONObject serialize() {
            setNames();
            JSONObject result = collect().serialize(this);
            result.put("key", opsClass.getName());
            return result;
        }

        private void setNames() {
            if (this.decisionsFile != null) {
                return;
            }

            this.decisionsFile = ExecutionTracer.DECISIONS_FILE_MAP.get(opsClass);
            this.instrNames = ExecutionTracer.INSTR_NAMES_MAP.get(opsClass);
            this.specNames = ExecutionTracer.SPECIALIZATION_NAMES_MAP.get(opsClass);

            if (decisionsFile == null || instrNames == null || specNames == null) {
                throw new AssertionError();
            }
        }

        public EnabledExecutionTracer getTracer() {
            if (currentTracer.get() == null) {
                return createTracer();
            } else {
                return currentTracer.get();
            }
        }

        private EnabledExecutionTracer createTracer() {
            assert currentTracer.get() == null;
            EnabledExecutionTracer tracer = new EnabledExecutionTracer(this);
            currentTracer.set(tracer);
            allTracers.add(tracer);
            return tracer;
        }

    }

    static class DisabledExecutionTracer extends ExecutionTracer {
        static final DisabledExecutionTracer INSTANCE = new DisabledExecutionTracer();

        @Override
        public void startFunction(Node node) {
        }

        @Override
        public void endFunction(Node node) {
        }

        @Override
        public void traceInstruction(int bci, int id, int... arguments) {
        }

        @Override
        public void traceActiveSpecializations(int bci, int id, boolean[] activeSpecializations) {
        }

        @Override
        public void traceSpecialization(int bci, int id, int specializationId, Object... values) {
        }

        @Override
        public void traceStartBasicBlock(int bci) {
        }
    }

    private static final class EnabledExecutionTracer extends ExecutionTracer {

        private final GlobalOperationStatistics stats;

        private static class PseudoInstruction {
            private String name;
            private boolean isRegular;

            private PseudoInstruction(String name, boolean isRegular) {
                this.name = name;
                this.isRegular = isRegular;
            }

            String name(GlobalOperationStatistics stats) {
                return this.name;
            }

            boolean isRegular() {
                return isRegular;
            }

            public static PseudoInstruction parse(String s) {
                return new PseudoInstruction(s.substring(1), s.charAt(0) == 'R');
            }

            String serialize(GlobalOperationStatistics stats) {
                return (isRegular() ? 'R' : 'g') + name(stats);
            }

            @Override
            public int hashCode() {
                return name.hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof PseudoInstruction && ((PseudoInstruction) obj).name.equals(name);
            }

        }

        private static class RegularInstruction extends PseudoInstruction {
            private final int instruction;

            RegularInstruction(GlobalOperationStatistics stats, int instruction) {
                super(stats.instrNames[instruction], true);
                this.instruction = instruction;
            }
        }

        private static class SpecializationKey extends PseudoInstruction {
            final int instructionId;
            @CompilationFinal(dimensions = 1) final boolean[] activeSpecializations;
            int countActive = -1;

            SpecializationKey(GlobalOperationStatistics stats, int instructionId, boolean[] activeSpecializations) {
                super(makeName(stats, instructionId, activeSpecializations), false);
                this.instructionId = instructionId;
                this.activeSpecializations = activeSpecializations;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + Arrays.hashCode(activeSpecializations);
                result = prime * result + Objects.hash(instructionId);
                return result;
            }

            public int getCountActive() {
                if (countActive == -1) {
                    int c = 0;
                    for (int i = 0; i < activeSpecializations.length; i++) {
                        if (activeSpecializations[i]) {
                            c++;
                        }
                    }
                    countActive = c;
                }

                return countActive;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                SpecializationKey other = (SpecializationKey) obj;
                return Arrays.equals(activeSpecializations, other.activeSpecializations) && instructionId == other.instructionId;
            }

            private int[] specializationIds() {
                int[] result = new int[getCountActive()];
                int idx = 0;
                for (int i = 0; i < activeSpecializations.length; i++) {
                    if (activeSpecializations[i]) {
                        result[idx++] = i;
                    }
                }

                return result;
            }

            private JSONObject serialize() {
                JSONObject result = new JSONObject();
                result.put("i", instructionId);
                result.put("n", activeSpecializations.length);

                JSONArray activeSpecsData = new JSONArray();
                for (int i = 0; i < activeSpecializations.length; i++) {
                    if (activeSpecializations[i]) {
                        activeSpecsData.put(i);
                    }
                }

                result.put("a", activeSpecsData);

                return result;
            }

            private static SpecializationKey deserialize(GlobalOperationStatistics stats, JSONObject obj) {
                int id = obj.getInt("i");
                int count = obj.getInt("n");
                boolean[] activeSpecializations = new boolean[count];
                JSONArray activeSpecsData = obj.getJSONArray("a");
                for (int i = 0; i < activeSpecsData.length(); i++) {
                    activeSpecializations[activeSpecsData.getInt(i)] = true;
                }

                return new SpecializationKey(stats, id, activeSpecializations);
            }

            @Override
            public String toString() {
                return "SpecializationKey [" + instructionId + ", " + Arrays.toString(activeSpecializations) + "]";
            }

            private static String makeName(GlobalOperationStatistics stats, int instructionId, boolean[] activeSpecializations) {
                String s = stats.instrNames[instructionId] + ".q";

                for (int i = 0; i < activeSpecializations.length; i++) {
                    if (activeSpecializations[i]) {
                        s += "." + stats.specNames[instructionId][i];
                    }
                }

                return s;
            }
        }

        private static class InstructionSequenceKey extends PseudoInstruction {
            final int[] instructions;

            InstructionSequenceKey(GlobalOperationStatistics stats, int[] instructions) {
                super(makeName(stats, instructions), false);
                this.instructions = instructions;
            }

            @Override
            public boolean equals(Object obj) {
                return obj.getClass() == InstructionSequenceKey.class && Arrays.equals(((InstructionSequenceKey) obj).instructions, instructions);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(instructions);
            }

            @Override
            public String toString() {
                return "InstructionSequenceKey " + Arrays.toString(instructions);
            }

            public String toKey() {
                return String.join(",", Arrays.stream(instructions).mapToObj(Integer::toString).toArray(String[]::new));
            }

            public static InstructionSequenceKey fromKey(GlobalOperationStatistics stats, String key) {
                int[] instructions = Arrays.stream(key.split(",")).mapToInt(Integer::parseInt).toArray();
                return new InstructionSequenceKey(stats, instructions);
            }

            public Object toString(String[] instrNames) {
                return String.join(",", Arrays.stream(instructions).mapToObj(x -> instrNames[x]).toArray(String[]::new));
            }

            static String makeName(GlobalOperationStatistics stats, int[] instructions) {
                String s = "si";

                for (int i = 0; i < instructions.length; i++) {
                    s += "." + stats.instrNames[instructions[i]];
                }

                return s;
            }
        }

        // quickening
        private Map<SpecializationKey, Long> activeSpecializationsMap = new HashMap<>();

        // superinstructions
        private Map<InstructionSequenceKey, Long> instructionSequencesMap = new HashMap<>();

        // common / uncommon
        private Map<PseudoInstruction, Long> numNodesWithInstruction = new HashMap<>();
        private Map<PseudoInstruction, Set<Node>> nodesWithInstruction = new HashMap<>();
        private Map<Node, Long> hitCount = new HashMap<>();

        private List<Node> nodeStack = new ArrayList<>();
        private Node curNode;

        private static final int MAX_SUPERINSTR_LEN = 8;
        List<int[]> instrHistoryStack = new ArrayList<>();
        List<Integer> instrHistoryLenStack = new ArrayList<>();
        private int[] instrHistory = null;
        private int instrHistoryLen = 0;

        private EnabledExecutionTracer(GlobalOperationStatistics stats) {
            this.stats = stats;
        }

        @Override
        public void startFunction(Node node) {
            if (curNode != null) {
                nodeStack.add(curNode);
            }
            curNode = node;

            if (instrHistory != null) {
                instrHistoryStack.add(instrHistory);
                instrHistoryLenStack.add(instrHistoryLen);
            }

            instrHistory = new int[MAX_SUPERINSTR_LEN];
            instrHistoryLen = 0;
        }

        @Override
        public void endFunction(Node node) {
            if (curNode != node) {
                throw new AssertionError("Tracer start/stop mismatch");
            }

            if (nodeStack.size() > 0) {
                curNode = nodeStack.remove(nodeStack.size() - 1);
            } else {
                curNode = null;
            }

            if (instrHistoryStack.size() > 0) {
                instrHistory = instrHistoryStack.remove(instrHistoryStack.size() - 1);
                instrHistoryLen = instrHistoryLenStack.remove(instrHistoryLenStack.size() - 1);
            } else {
                instrHistory = null;
            }
        }

        private void encounterPseudoInstruction(PseudoInstruction instr) {
            nodesWithInstruction.computeIfAbsent(instr, k -> new HashSet<>()).add(curNode);
        }

        @Override
        public void traceInstruction(int bci, int id, int... arguments) {
            hitCount.merge(curNode, 1L, EnabledExecutionTracer::saturatingAdd);

            encounterPseudoInstruction(new RegularInstruction(stats, id));

            boolean isBranch = arguments[0] != 0;
            boolean isVariadic = arguments[1] != 0;

            // SI finding
            if (isVariadic) {
                // we don't support variadic
                instrHistoryLen = 0;
            } else {
                if (instrHistoryLen == MAX_SUPERINSTR_LEN) {
                    System.arraycopy(instrHistory, 1, instrHistory, 0, MAX_SUPERINSTR_LEN - 1);
                    instrHistory[MAX_SUPERINSTR_LEN - 1] = id;
                } else {
                    instrHistory[instrHistoryLen++] = id;
                }

                for (int i = 0; i < instrHistoryLen - 1; i++) {
                    int[] curHistory = Arrays.copyOfRange(instrHistory, i, instrHistoryLen);
                    InstructionSequenceKey key = new InstructionSequenceKey(stats, curHistory);
                    instructionSequencesMap.merge(key, 1L, EnabledExecutionTracer::saturatingAdd);
                    encounterPseudoInstruction(key);
                }
            }

        }

        private static long saturatingAdd(long x, long y) {
            try {
                return Math.addExact(x, y);
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE;
            }
        }

        private static int saturatingAdd(int x, int y) {
            try {
                return Math.addExact(x, y);
            } catch (ArithmeticException e) {
                return Integer.MAX_VALUE;
            }
        }

        @Override
        public void traceActiveSpecializations(int bci, int id, boolean[] activeSpecializations) {
            if (activeSpecializations.length < 2) {
                // we do not care for single specialisation instructions
                return;
            }

            boolean anyActive = false;
            for (int i = 0; i < activeSpecializations.length; i++) {
                if (activeSpecializations[i]) {
                    anyActive = true;
                    break;
                }
            }

            if (!anyActive) {
                return;
            }

            SpecializationKey key = new SpecializationKey(stats, id, activeSpecializations);
            Long l = activeSpecializationsMap.get(key);
            encounterPseudoInstruction(key);
            if (l == null) {
                activeSpecializationsMap.put(key, 1L);
            } else if (l != Long.MAX_VALUE) {
                activeSpecializationsMap.put(key, l + 1);
            }
        }

        @Override
        public void traceSpecialization(int bci, int id, int specializationId, Object... values) {
        }

        @Override
        public void traceStartBasicBlock(int bci) {
            instrHistoryLen = 0;
        }

        private void merge(EnabledExecutionTracer other) {
            other.activeSpecializationsMap.forEach((k, v) -> {
                Long existing = this.activeSpecializationsMap.get(k);
                if (existing == null) {
                    this.activeSpecializationsMap.put(k, v);
                } else if (Long.MAX_VALUE - existing > v) {
                    this.activeSpecializationsMap.put(k, existing + v);
                } else {
                    this.activeSpecializationsMap.put(k, Long.MAX_VALUE);
                }
            });

            other.nodesWithInstruction.forEach((k, v) -> {
                nodesWithInstruction.computeIfAbsent(k, k2 -> new HashSet<>()).addAll(v);
            });
            other.numNodesWithInstruction.forEach((k, v) -> {
                numNodesWithInstruction.merge(k, v, EnabledExecutionTracer::saturatingAdd);
            });
            other.instructionSequencesMap.forEach((k, v) -> {
                instructionSequencesMap.merge(k, v, EnabledExecutionTracer::saturatingAdd);
            });
            other.hitCount.forEach((k, v) -> {
                hitCount.merge(k, v, EnabledExecutionTracer::saturatingAdd);
            });
        }

        private void calcNumNodesWithInstruction(GlobalOperationStatistics stats) {
            nodesWithInstruction.forEach((k, v) -> {
                long totalCount = v.stream().map(hitCount::get).filter(x -> x != null).reduce(0L, EnabledExecutionTracer::saturatingAdd);
                numNodesWithInstruction.merge(k, totalCount, EnabledExecutionTracer::saturatingAdd);
            });
            nodesWithInstruction.clear();
        }

        private JSONObject serialize(GlobalOperationStatistics stats) {
            JSONObject result = new JSONObject();

            JSONArray activeSpecializationsData = new JSONArray();
            activeSpecializationsMap.forEach((k, v) -> {
                JSONObject activeSpecData = k.serialize();
                activeSpecData.put("c", v);
                activeSpecializationsData.put(activeSpecData);
            });
            result.put("as", activeSpecializationsData);

            JSONObject ni = new JSONObject();
            calcNumNodesWithInstruction(stats);
            numNodesWithInstruction.forEach((k, v) -> {
                ni.put(k.serialize(stats), v);
            });
            result.put("ni", ni);

            JSONObject instructionSequences = new JSONObject();
            instructionSequencesMap.forEach((k, v) -> {
                instructionSequences.put(k.toKey(), v);
            });
            result.put("is", instructionSequences);

            return result;
        }

        private static EnabledExecutionTracer deserialize(GlobalOperationStatistics stats, JSONObject obj) {
            EnabledExecutionTracer inst = new EnabledExecutionTracer(stats);
            JSONArray activeSpecializationsData = obj.getJSONArray("as");

            for (int i = 0; i < activeSpecializationsData.length(); i++) {
                JSONObject activeSpecData = activeSpecializationsData.getJSONObject(i);
                long count = activeSpecData.getLong("c");
                SpecializationKey key = SpecializationKey.deserialize(stats, activeSpecData);
                inst.activeSpecializationsMap.put(key, count);
            }

            JSONObject ni = obj.getJSONObject("ni");
            for (String key : ni.keySet()) {
                inst.numNodesWithInstruction.put(PseudoInstruction.parse(key), ni.getLong(key));
            }

            JSONObject instructionSequences = obj.getJSONObject("is");
            for (String key : instructionSequences.keySet()) {
                inst.instructionSequencesMap.put(InstructionSequenceKey.fromKey(stats, key), instructionSequences.getLong(key));
            }

            return inst;
        }

        private static void orderDecisions(List<Decision> output, List<Decision> input, int expectedCount, GlobalOperationStatistics stats) {
            int outputCount = input.size() < expectedCount ? input.size() : expectedCount;

            for (int i = 0; i < outputCount; i++) {
                Decision next = input.get(i);
                output.add(next);

                for (int j = i + 1; j < input.size(); j++) {
                    input.get(j).acceptedBefore(next, stats);
                }
                input.subList(i, input.size()).sort(Decision.COMPARATOR);
            }
        }

        private static final int NUM_DECISIONS = 30;

        public JSONArray serializeDecisions(GlobalOperationStatistics stats, PrintWriter dumpWriter) {
            JSONArray result = new JSONArray();
            result.put("This file is autogenerated by the Operations DSL.");
            result.put("Do not modify, as it will be overwritten when running with tracing support.");
            result.put("Use the overrides file to alter the optimisation decisions.");

            calcNumNodesWithInstruction(stats);

            List<Decision> decisions = new ArrayList<>();
            activeSpecializationsMap.entrySet().forEach(e -> {
                decisions.add(new Decision.Quicken(e.getKey().instructionId, e.getKey().specializationIds(), e.getValue()));
            });
            instructionSequencesMap.entrySet().forEach(e -> {
                decisions.add(new Decision.SuperInstruction(e.getKey().instructions, e.getValue()));
            });

            // TODO implement weighting
            // currently weighting execution count
            // divide execution counts by the total number of instructions.

            // sort
            decisions.sort(Decision.COMPARATOR);
            List<Decision> acceptedDecisions = new ArrayList<>();
            orderDecisions(acceptedDecisions, decisions, NUM_DECISIONS, stats);

            // the common / uncommon instructions don't compete with other decisions,
            // so we add them at the end
            numNodesWithInstruction.entrySet().stream().map(e -> {
                Decision d = new Decision.CommonInstruction(e.getKey().name(stats), e.getValue(), e.getKey().isRegular());
                for (Decision pre : acceptedDecisions) {
                    d.acceptedBefore(pre, stats);
                }
                return d;
            }).filter(x -> x.value() > 0).sorted(Decision.COMPARATOR).limit(64).forEach(acceptedDecisions::add);

            if (dumpWriter != null) {
                dumpWriter.println("======================== OPERATION DSL TRACING DECISIONS ======================== ");
                dumpWriter.println("# For " + stats.opsClass.getSimpleName());
                dumpWriter.println();
            }

            double normValue = acceptedDecisions.size() == 0 ? 1.0 : acceptedDecisions.get(0).value();

            // serialize
            for (Decision dec : acceptedDecisions) {
                if (dumpWriter != null) {
                    dumpWriter.println(dec.prettyPrint(stats, normValue));
                }
                result.put(dec.serialize(stats, normValue));
            }

            if (dumpWriter != null) {
                dumpWriter.println("================================================================================= ");
            }

            return result;
        }

        @Override
        public String toString() {
            return "EnabledExecutionTracer [" + activeSpecializationsMap + "]";
        }

    }

}
