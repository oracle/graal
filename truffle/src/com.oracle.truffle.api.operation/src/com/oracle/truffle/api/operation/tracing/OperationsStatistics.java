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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;

/**
 * This is a per-context singleton. It retains tracing statistics for each root node and writes them
 * out to the state file on exit. If the state file already has data, new data obtained during
 * tracing will be combined with existing data.
 */
public class OperationsStatistics {
    static final ThreadLocal<OperationsStatistics> STATISTICS = new ThreadLocal<>();
    private final Map<Class<?>, OperationRootNodeStatistics> rootNodeStatistics = new HashMap<>();
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
                OperationRootNodeStatistics value = OperationRootNodeStatistics.deserialize(this, data);
                this.rootNodeStatistics.put(value.operationClass, value);
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(PrintWriter dumpWriter) {
        try {
            try {
                JSONArray result = new JSONArray();
                this.rootNodeStatistics.forEach((k, v) -> {
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

        this.rootNodeStatistics.forEach((k, v) -> {
            try {
                v.writeDecisions(dumpWriter);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    synchronized OperationRootNodeStatistics getStatistics(Class<?> operationsClass) {
        return rootNodeStatistics.computeIfAbsent(operationsClass, (c) -> OperationRootNodeStatistics.createFromClass(operationsClass));
    }

    /**
     * Manages the tracing state for a particular root node.
     */
    static final class OperationRootNodeStatistics {
        private final ThreadLocal<EnabledExecutionTracer> currentTracer = new ThreadLocal<>();
        private final List<EnabledExecutionTracer> allTracers = new ArrayList<>();

        final Class<?> operationClass;
        final String decisionsFile;
        final String[] instructionNames;
        final String[][] specializationNames;

        OperationRootNodeStatistics(Class<?> operationsClass, String decisionsFile, String[] instructionNames, String[][] specializationNames) {
            this.operationClass = operationsClass;
            this.decisionsFile = decisionsFile;
            this.instructionNames = instructionNames;
            this.specializationNames = specializationNames;
        }

        private static OperationRootNodeStatistics createFromClass(Class<?> operationsClass) {
            if (!operationsClass.isAnnotationPresent(TracingMetadata.class)) {
                throw new AssertionError(String.format("Operations class %s does not contain the @%s annotation.", operationsClass.getName(), TracingMetadata.class.getName()));
            }
            TracingMetadata metadata = operationsClass.getAnnotation(TracingMetadata.class);

            String[] instructionNames = metadata.instructionNames();

            // Convert specializationNames to a 2D array indexed first by instruction id.
            String[][] specializationNames = new String[instructionNames.length][];

            Map<String, Integer> instructionNameToId = new HashMap<>();
            for (int i = 0; i < instructionNames.length; i++) {
                instructionNameToId.put(instructionNames[i], i);
            }

            for (TracingMetadata.SpecializationNames entry : metadata.specializationNames()) {
                int instructionId = instructionNameToId.get(entry.instruction());
                specializationNames[instructionId] = entry.specializations();
            }

            return new OperationRootNodeStatistics(operationsClass, metadata.decisionsFile(), instructionNames, specializationNames);
        }

        public void writeDecisions(PrintWriter dumpWriter) throws IOException {
            EnabledExecutionTracer tracer = collect();
            try (FileChannel ch = FileChannel.open(Path.of(decisionsFile), StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                JSONArray result = tracer.serializeDecisions(this, dumpWriter);
                ch.truncate(0);
                ch.write(ByteBuffer.wrap(result.toString(4).getBytes(StandardCharsets.UTF_8)));
            }
        }

        private static OperationRootNodeStatistics deserialize(OperationsStatistics parent, JSONObject data) throws ClassNotFoundException {
            Class<?> key = Class.forName(data.getString("rootNode"));
            OperationRootNodeStatistics result = parent.getStatistics(key);
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
            JSONObject result = collect().serialize();
            result.put("rootNode", operationClass.getName());
            return result;
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
        public void startRoot(RootNode rootNode) {
        }

        @Override
        public void endRoot(RootNode rootNode) {
        }

        @Override
        public void traceInstruction(int bci, int id, boolean isVariadic) {
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

        private final OperationRootNodeStatistics stats;

        private static class PseudoInstruction {
            private String name;
            private boolean isRegular;

            private PseudoInstruction(String name, boolean isRegular) {
                this.name = name;
                this.isRegular = isRegular;
            }

            String getName() {
                return this.name;
            }

            boolean isRegular() {
                return isRegular;
            }

            public static PseudoInstruction parse(String s) {
                return new PseudoInstruction(s.substring(1), s.charAt(0) == 'R');
            }

            String serializedName() {
                return (isRegular() ? 'R' : 'g') + this.name;
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

            RegularInstruction(OperationRootNodeStatistics stats, int instruction) {
                super(stats.instructionNames[instruction], true);
                this.instruction = instruction;
            }
        }

        private static class SpecializationState extends PseudoInstruction {
            final int instructionId;
            @CompilationFinal(dimensions = 1) final boolean[] activeSpecializations;
            int countActive = -1; // lazily computed

            SpecializationState(OperationRootNodeStatistics stats, int instructionId, boolean[] activeSpecializations) {
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
                SpecializationState other = (SpecializationState) obj;
                return Arrays.equals(activeSpecializations, other.activeSpecializations) && instructionId == other.instructionId;
            }

            private int getCountActive() {
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
                result.put("id", instructionId);
                result.put("count", activeSpecializations.length);

                JSONArray activeSpecsData = new JSONArray();
                for (int i = 0; i < activeSpecializations.length; i++) {
                    if (activeSpecializations[i]) {
                        activeSpecsData.put(i);
                    }
                }

                result.put("active", activeSpecsData);

                return result;
            }

            private static SpecializationState deserialize(OperationRootNodeStatistics stats, JSONObject obj) {
                int id = obj.getInt("id");
                int count = obj.getInt("count");
                boolean[] activeSpecializations = new boolean[count];
                JSONArray activeSpecsData = obj.getJSONArray("active");
                for (int i = 0; i < activeSpecsData.length(); i++) {
                    activeSpecializations[activeSpecsData.getInt(i)] = true;
                }

                return new SpecializationState(stats, id, activeSpecializations);
            }

            @Override
            public String toString() {
                return "SpecializationState [" + instructionId + ", " + Arrays.toString(activeSpecializations) + "]";
            }

            private static String makeName(OperationRootNodeStatistics stats, int instructionId, boolean[] activeSpecializations) {
                String s = stats.instructionNames[instructionId] + ".q";

                for (int i = 0; i < activeSpecializations.length; i++) {
                    if (activeSpecializations[i]) {
                        s += "." + stats.specializationNames[instructionId][i];
                    }
                }

                return s;
            }
        }

        private static class InstructionSequence extends PseudoInstruction {
            final int[] instructions;

            InstructionSequence(OperationRootNodeStatistics stats, int[] instructions) {
                super(makeName(stats, instructions), false);
                this.instructions = instructions;
            }

            @Override
            public boolean equals(Object obj) {
                return obj.getClass() == InstructionSequence.class && Arrays.equals(((InstructionSequence) obj).instructions, instructions);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(instructions);
            }

            @Override
            public String toString() {
                return "InstructionSequence " + Arrays.toString(instructions);
            }

            public String toKey() {
                return String.join(",", Arrays.stream(instructions).mapToObj(Integer::toString).toArray(String[]::new));
            }

            public static InstructionSequence fromKey(OperationRootNodeStatistics stats, String key) {
                int[] instructions = Arrays.stream(key.split(",")).mapToInt(Integer::parseInt).toArray();
                return new InstructionSequence(stats, instructions);
            }

            static String makeName(OperationRootNodeStatistics stats, int[] instructions) {
                String s = "si";

                for (int i = 0; i < instructions.length; i++) {
                    s += "." + stats.instructionNames[instructions[i]];
                }

                return s;
            }
        }

        private static final class Counter<T> extends HashMap<T, Long> {
            private static final long serialVersionUID = -3031871917813857590L;

            private void increment(T key) {
                merge(key, 1L, EnabledExecutionTracer::saturatingAdd);
            }
        }

        // Frequent specialization states are used for quickening.
        private Counter<SpecializationState> specializationStatesSeen = new Counter<>();
        // Frequent instruction sequences are used for superinstructions.
        private Counter<InstructionSequence> instructionSequencesSeen = new Counter<>();

        // An instruction frequency heuristic that prioritizes instructions used by "hot" nodes.
        // Each instruction's frequency is calculated as the sum of all instructions executed across
        // all of the root nodes that contain the instruction.
        private Map<PseudoInstruction, Long> weightedInstructionFrequency = new HashMap<>();
        private Counter<RootNode> instructionsExecuted = new Counter<>();
        private Map<PseudoInstruction, Set<RootNode>> instructionUseSites = new HashMap<>();

        // Models current tracing state for a root node. When a root node calls another root node,
        // an instance of this class captures the current tracing state, allowing tracing to resume
        // after the call.
        private static final class TracingStackEntry {
            private final RootNode rootNode;
            private final int[] instrHistory;
            private final int instrHistoryLength;

            private TracingStackEntry(RootNode rootNode, int[] instrHistory, int instrHistoryLength) {
                this.rootNode = rootNode;
                this.instrHistory = instrHistory;
                this.instrHistoryLength = instrHistoryLength;
            }

        }

        private List<TracingStackEntry> stack = new ArrayList<>();
        private static final int MAX_SUPERINSTR_LEN = 8;
        private RootNode currentRootNode;
        private int[] instrHistory = null;
        private int instrHistoryLen = 0;

        private EnabledExecutionTracer(OperationRootNodeStatistics stats) {
            this.stats = stats;
        }

        @Override
        public void startRoot(RootNode rootNode) {
            if (currentRootNode != null) {
                stack.add(new TracingStackEntry(currentRootNode, instrHistory, instrHistoryLen));
            }
            currentRootNode = rootNode;
            instrHistory = new int[MAX_SUPERINSTR_LEN];
            instrHistoryLen = 0;
        }

        @Override
        public void endRoot(RootNode rootNode) {
            if (currentRootNode != rootNode) {
                throw new AssertionError("Tracer start/stop mismatch");
            }

            if (stack.size() > 0) {
                TracingStackEntry entry = stack.remove(stack.size() - 1);
                currentRootNode = entry.rootNode;
                instrHistory = entry.instrHistory;
                instrHistoryLen = entry.instrHistoryLength;
            } else {
                currentRootNode = null;
                instrHistory = null;
                instrHistoryLen = 0;
            }
        }

        private void encounterPseudoInstruction(PseudoInstruction instr) {
            instructionUseSites.computeIfAbsent(instr, k -> new HashSet<>()).add(currentRootNode);
        }

        @Override
        public void traceInstruction(int bci, int id, boolean isVariadic) {
            instructionsExecuted.increment(currentRootNode);
            encounterPseudoInstruction(new RegularInstruction(stats, id));

            // Trace instruction sequences for superinstructions.
            if (isVariadic) {
                // We don't support variadic superinstructions.
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
                    InstructionSequence key = new InstructionSequence(stats, curHistory);
                    instructionSequencesSeen.increment(key);
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

        @Override
        public void traceActiveSpecializations(int bci, int id, boolean[] activeSpecializations) {
            if (activeSpecializations.length < 2) {
                // Instructions with one specialization cannot be quickened.
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

            SpecializationState key = new SpecializationState(stats, id, activeSpecializations);
            specializationStatesSeen.increment(key);
            encounterPseudoInstruction(key);
        }

        @Override
        public void traceSpecialization(int bci, int id, int specializationId, Object... values) {
        }

        @Override
        public void traceStartBasicBlock(int bci) {
            instrHistoryLen = 0;
        }

        private void merge(EnabledExecutionTracer other) {
            other.specializationStatesSeen.forEach((k, v) -> {
                Long existing = this.specializationStatesSeen.get(k);
                if (existing == null) {
                    this.specializationStatesSeen.put(k, v);
                } else if (Long.MAX_VALUE - existing > v) {
                    this.specializationStatesSeen.put(k, existing + v);
                } else {
                    this.specializationStatesSeen.put(k, Long.MAX_VALUE);
                }
            });

            other.instructionUseSites.forEach((k, v) -> {
                instructionUseSites.computeIfAbsent(k, k2 -> new HashSet<>()).addAll(v);
            });
            other.weightedInstructionFrequency.forEach((k, v) -> {
                weightedInstructionFrequency.merge(k, v, EnabledExecutionTracer::saturatingAdd);
            });
            other.instructionSequencesSeen.forEach((k, v) -> {
                instructionSequencesSeen.merge(k, v, EnabledExecutionTracer::saturatingAdd);
            });
            other.instructionsExecuted.forEach((k, v) -> {
                instructionsExecuted.merge(k, v, EnabledExecutionTracer::saturatingAdd);
            });
        }

        private void calculateWeightedInstructionFrequency() {
            instructionUseSites.forEach((instr, nodes) -> {
                // Bias each instruction's frequency based on how "hot" the nodes containing the
                // instruction are (i.e., how many instructions they execute).
                long totalCount = nodes.stream().map(instructionsExecuted::get).filter(x -> x != null).reduce(0L, EnabledExecutionTracer::saturatingAdd);
                weightedInstructionFrequency.merge(instr, totalCount, EnabledExecutionTracer::saturatingAdd);
            });
            instructionUseSites.clear();
        }

        private JSONObject serialize() {
            JSONObject result = new JSONObject();

            JSONArray activeSpecializationsData = new JSONArray();
            specializationStatesSeen.forEach((k, v) -> {
                JSONObject activeSpecData = k.serialize();
                activeSpecData.put("count", v);
                activeSpecializationsData.put(activeSpecData);
            });
            result.put("activeSpecializations", activeSpecializationsData);

            JSONObject instructionFrequency = new JSONObject();
            calculateWeightedInstructionFrequency();
            weightedInstructionFrequency.forEach((k, v) -> {
                instructionFrequency.put(k.serializedName(), v);
            });
            result.put("instructionFrequency", instructionFrequency);

            JSONObject instructionSequences = new JSONObject();
            instructionSequencesSeen.forEach((k, v) -> {
                instructionSequences.put(k.toKey(), v);
            });
            result.put("instructionSequences", instructionSequences);

            return result;
        }

        private static EnabledExecutionTracer deserialize(OperationRootNodeStatistics stats, JSONObject obj) {
            EnabledExecutionTracer inst = new EnabledExecutionTracer(stats);
            JSONArray activeSpecializationsData = obj.getJSONArray("activeSpecializations");

            for (int i = 0; i < activeSpecializationsData.length(); i++) {
                JSONObject activeSpecData = activeSpecializationsData.getJSONObject(i);
                long count = activeSpecData.getLong("count");
                SpecializationState key = SpecializationState.deserialize(stats, activeSpecData);
                inst.specializationStatesSeen.put(key, count);
            }

            JSONObject instructionFrequency = obj.getJSONObject("instructionFrequency");
            for (String key : instructionFrequency.keySet()) {
                inst.weightedInstructionFrequency.put(PseudoInstruction.parse(key), instructionFrequency.getLong(key));
            }

            JSONObject instructionSequences = obj.getJSONObject("instructionSequences");
            for (String key : instructionSequences.keySet()) {
                inst.instructionSequencesSeen.put(InstructionSequence.fromKey(stats, key), instructionSequences.getLong(key));
            }

            return inst;
        }

        private static void orderDecisions(List<Decision> output, List<Decision> input, int expectedCount, OperationRootNodeStatistics stats) {
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

        public JSONArray serializeDecisions(OperationRootNodeStatistics stats, PrintWriter dumpWriter) {
            JSONArray result = new JSONArray();
            result.put("This file is autogenerated by the Operations DSL.");
            result.put("Do not modify, as it will be overwritten when running with tracing support.");
            result.put("Use the overrides file to alter the optimisation decisions.");

            calculateWeightedInstructionFrequency();

            List<Decision> decisions = new ArrayList<>();
            specializationStatesSeen.entrySet().forEach(e -> {
                decisions.add(new Decision.Quicken(e.getKey().instructionId, e.getKey().specializationIds(), e.getValue()));
            });
            instructionSequencesSeen.entrySet().forEach(e -> {
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
            weightedInstructionFrequency.entrySet().stream().map(e -> {
                Decision d = new Decision.CommonInstruction(e.getKey().getName(), e.getValue(), e.getKey().isRegular());
                for (Decision pre : acceptedDecisions) {
                    d.acceptedBefore(pre, stats);
                }
                return d;
            }).filter(x -> x.value() > 0).sorted(Decision.COMPARATOR).limit(64).forEach(acceptedDecisions::add);

            if (dumpWriter != null) {
                dumpWriter.println("======================== OPERATION DSL TRACING DECISIONS ======================== ");
                dumpWriter.println("# For " + stats.operationClass.getSimpleName());
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
            return "EnabledExecutionTracer [" + specializationStatesSeen + "]";
        }

    }

}
