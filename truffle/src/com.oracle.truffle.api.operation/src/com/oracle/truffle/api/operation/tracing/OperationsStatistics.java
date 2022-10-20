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

    public void write() {
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
                v.writeDecisions();
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
        }

        public void writeDecisions() throws IOException {
            setNames();
            EnabledExecutionTracer tracer = collect();
            try (FileChannel ch = FileChannel.open(Path.of(decisionsFile), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                JSONArray result = tracer.serializeDecisions(this);
                ch.truncate(0);
                ch.write(ByteBuffer.wrap(result.toString(4).getBytes(StandardCharsets.UTF_8)));
            }
        }

        private static GlobalOperationStatistics deserialize(OperationsStatistics parent, JSONObject data) throws ClassNotFoundException {
            Class<?> key = Class.forName(data.getString("key"));
            GlobalOperationStatistics result = parent.getStatsistics(key);
            result.allTracers.add(EnabledExecutionTracer.deserialize(data));
            return result;
        }

        private EnabledExecutionTracer collect() {
            EnabledExecutionTracer tracer = new EnabledExecutionTracer();
            for (EnabledExecutionTracer other : allTracers) {
                tracer.merge(other);
            }

            return tracer;
        }

        private JSONObject serialize() {
            JSONObject result = collect().serialize();
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
            EnabledExecutionTracer tracer = new EnabledExecutionTracer();
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
        public void traceInstruction(int bci, int id) {
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

    private static class EnabledExecutionTracer extends ExecutionTracer {

        private static class SpecializationKey {
            final int instructionId;
            @CompilationFinal(dimensions = 1) final boolean[] activeSpecializations;
            int countActive = -1;

            SpecializationKey(int instructionId, boolean[] activeSpecializations) {
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

            private static SpecializationKey deserialize(JSONObject obj) {
                int id = obj.getInt("i");
                int count = obj.getInt("n");
                boolean[] activeSpecializations = new boolean[count];
                JSONArray activeSpecsData = obj.getJSONArray("a");
                for (int i = 0; i < activeSpecsData.length(); i++) {
                    activeSpecializations[activeSpecsData.getInt(i)] = true;
                }

                return new SpecializationKey(id, activeSpecializations);
            }

            @Override
            public String toString() {
                return "SpecializationKey [" + instructionId + ", " + Arrays.toString(activeSpecializations) + "]";
            }
        }

        private static class InstructionSequenceKey {
            final int[] instructions;

            InstructionSequenceKey(int[] instructions) {
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

            public static InstructionSequenceKey fromKey(String key) {
                int[] instructions = Arrays.stream(key.split(",")).mapToInt(Integer::parseInt).toArray();
                return new InstructionSequenceKey(instructions);
            }

            public Object toString(String[] instrNames) {
                return String.join(",", Arrays.stream(instructions).mapToObj(x -> instrNames[x]).toArray(String[]::new));
            }
        }

        private Map<SpecializationKey, Long> activeSpecializationsMap = new HashMap<>();
        private Map<InstructionSequenceKey, Long> instructionSequencesMap = new HashMap<>();
        private Map<Integer, Integer> numNodesWithInstruction = new HashMap<>();
        private Map<Integer, Set<Node>> nodesWithInstruction = new HashMap<>();

        private List<Node> nodeStack = new ArrayList<>();
        private Node curNode;

        private static final int MAX_SUPERINSTR_LEN = 8;
        List<int[]> instrHistoryStack = new ArrayList<>();
        List<Integer> instrHistoryLenStack = new ArrayList<>();
        private int[] instrHistory = null;
        private int instrHistoryLen = 0;

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

        @Override
        public void traceInstruction(int bci, int id) {
            nodesWithInstruction.computeIfAbsent(id, k -> new HashSet<>()).add(curNode);

            // SI finding
            if (instrHistoryLen == MAX_SUPERINSTR_LEN) {
                System.arraycopy(instrHistory, 1, instrHistory, 0, MAX_SUPERINSTR_LEN - 1);
                instrHistory[MAX_SUPERINSTR_LEN - 1] = id;
            } else {
                instrHistory[instrHistoryLen++] = id;
            }

            for (int i = 0; i < instrHistoryLen - 1; i++) {
                int[] curHistory = Arrays.copyOfRange(instrHistory, i, instrHistoryLen);
                InstructionSequenceKey key = new InstructionSequenceKey(curHistory);
                instructionSequencesMap.merge(key, 1L, EnabledExecutionTracer::saturatingAdd);
            }

        }

        private static long saturatingAdd(long x, long y) {
            try {
                return Math.addExact(x, y);
            } catch (ArithmeticException e) {
                return x;
            }
        }

        private static int saturatingAdd(int x, int y) {
            try {
                return Math.addExact(x, y);
            } catch (ArithmeticException e) {
                return x;
            }
        }

        @Override
        public void traceActiveSpecializations(int bci, int id, boolean[] activeSpecializations) {
            SpecializationKey key = new SpecializationKey(id, activeSpecializations);
            Long l = activeSpecializationsMap.get(key);
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
        }

        private void calcNumNodesWithInstruction() {
            nodesWithInstruction.forEach((k, v) -> {
                numNodesWithInstruction.put(k, v.size() + numNodesWithInstruction.getOrDefault(k, 0));
            });
            nodesWithInstruction.clear();
        }

        private JSONObject serialize() {
            JSONObject result = new JSONObject();

            JSONArray activeSpecializationsData = new JSONArray();
            activeSpecializationsMap.forEach((k, v) -> {
                JSONObject activeSpecData = k.serialize();
                activeSpecData.put("c", v);
                activeSpecializationsData.put(activeSpecData);
            });
            result.put("as", activeSpecializationsData);

            JSONObject ni = new JSONObject();
            calcNumNodesWithInstruction();
            numNodesWithInstruction.forEach((k, v) -> {
                ni.put(k.toString(), v);
            });
            result.put("ni", ni);

            JSONObject instructionSequences = new JSONObject();
            instructionSequencesMap.forEach((k, v) -> {
                instructionSequences.put(k.toKey(), v);
            });
            result.put("is", instructionSequences);

            return result;
        }

        private static EnabledExecutionTracer deserialize(JSONObject obj) {
            EnabledExecutionTracer inst = new EnabledExecutionTracer();
            JSONArray activeSpecializationsData = obj.getJSONArray("as");

            for (int i = 0; i < activeSpecializationsData.length(); i++) {
                JSONObject activeSpecData = activeSpecializationsData.getJSONObject(i);
                long count = activeSpecData.getLong("c");
                SpecializationKey key = SpecializationKey.deserialize(activeSpecData);
                inst.activeSpecializationsMap.put(key, count);
            }

            JSONObject ni = obj.getJSONObject("ni");
            for (String key : ni.keySet()) {
                inst.numNodesWithInstruction.put(Integer.parseInt(key), ni.getInt(key));
            }

            JSONObject instructionSequences = obj.getJSONObject("is");
            for (String key : instructionSequences.keySet()) {
                inst.instructionSequencesMap.put(InstructionSequenceKey.fromKey(key), instructionSequences.getLong(key));
            }

            return inst;
        }

        private static void orderDecisions(List<Decision> output, List<Decision> input, int expectedCount) {
            int outputCount = input.size() < expectedCount ? input.size() : expectedCount;

            for (int i = 0; i < outputCount; i++) {
                Decision next = input.get(i);
                output.add(next);

                for (int j = i + 1; j < input.size(); j++) {
                    input.get(j).acceptedBefore(next);
                }
                input.subList(i, input.size()).sort(Decision.COMPARATOR);
            }
        }

        private static final int NUM_DECISIONS = 30;

        public JSONArray serializeDecisions(GlobalOperationStatistics stats) {
            JSONArray result = new JSONArray();
            result.put("This file is autogenerated by the Operations DSL.");
            result.put("Do not modify, as it will be overwritten when running with tracing support.");
            result.put("Use the overrides file to alter the optimisation decisions.");

            // create decisions
            calcNumNodesWithInstruction();
            List<Decision> decisions = new ArrayList<>();
            activeSpecializationsMap.entrySet().forEach(e -> {
                decisions.add(new Decision.Quicken(e.getKey().instructionId, e.getKey().specializationIds(), e.getValue()));
            });
            instructionSequencesMap.entrySet().forEach(e -> {
                decisions.add(new Decision.SuperInstruction(e.getKey().instructions, e.getValue()));
            });

            // sort and print
            decisions.sort(Decision.COMPARATOR);
            List<Decision> acceptedDecisions = new ArrayList<>();
            orderDecisions(acceptedDecisions, decisions, NUM_DECISIONS);

            // serialize
            for (Decision dec : acceptedDecisions) {
                result.put(dec.serialize(stats));
            }

            return result;
        }

        @Override
        public String toString() {
            return "EnabledExecutionTracer [" + activeSpecializationsMap + "]";
        }

    }

}
